package com.machingclee.blogcomment.context.comment.commandhandler;

import com.machingclee.blogcomment.common.jpa.repository.CommentRepository;
import com.machingclee.blogcomment.context.comment.command.SendCommentGotRepliedNotificationCommand;
import com.machingclee.blogcomment.context.comment.event.CommentGotRepliedNotificationSentEvent;
import com.machingclee.blogcomment.context.external.AwsSesService;
import com.machingclee.domain.util.common.interfaces.CommandHandler;
import com.machingclee.domain.util.common.interfaces.EventQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves notification recipients and sends SES mail for a newly created comment.
 * <p>
 * Recipients = (when parent is set: thread participants via recursive CTE) ∪ site owners
 * ({@code app.ses.cc-addresses}), excluding the author. Root-level comments have
 * {@code parentCommentId == null} — that is expected; they still notify site owners.
 * <p>
 * For replies, each recipient sees a personalized parent label: the parent owner sees
 * "You wrote", while everyone else sees "{parentOwnerName} wrote".
 */
@Component
public class SendCommentGotRepliedNotificationCommandHandler
        implements CommandHandler<SendCommentGotRepliedNotificationCommand, SendCommentGotRepliedNotificationCommand.Result> {

    private static final Logger log = LoggerFactory.getLogger(
            SendCommentGotRepliedNotificationCommandHandler.class);

    private final AwsSesService awsSesService;
    private final CommentRepository commentRepository;
    private final Set<String> siteNotifyEmails;

    public SendCommentGotRepliedNotificationCommandHandler(
            AwsSesService awsSesService,
            CommentRepository commentRepository,
            @Value("${app.ses.cc-addresses:}") String siteNotifyAddresses
    ) {
        this.awsSesService = awsSesService;
        this.commentRepository = commentRepository;
        this.siteNotifyEmails = parseEmailList(siteNotifyAddresses);
    }

    @Override
    public SendCommentGotRepliedNotificationCommand.Result handle(
            EventQueue eventQueue,
            SendCommentGotRepliedNotificationCommand command
    ) {
        Set<String> threadEmails = resolveRecipients(command);
        if (threadEmails.isEmpty()) {
            String reason = "no recipients after excluding author"
                    + (command.getReplierEmail() != null ? " (" + command.getReplierEmail().trim() + ")" : "");
            log.info("Comment id={} — skip notification: {}", command.getReplyCommentId(), reason);
            return SendCommentGotRepliedNotificationCommand.Result.builder()
                    .skipped(true)
                    .skipReason(reason)
                    .recipientCount(0)
                    .build();
        }

        boolean isReply = command.getParentCommentId() != null;
        log.info("Comment id={} isReply={} — notifying {} recipient(s)",
                command.getReplyCommentId(), isReply, threadEmails.size());

        String authorLabel = blankTo(command.getReplierName(), "Someone");
        String messagePreview = truncate(command.getReplyMessage(), 500);

        String enTitle = command.getArticleTitle();
        String tcTitle = command.getArticleTitleTc();
        String articleUrl = command.getArticleUrl();

        String articleDisplay = buildArticleDisplay(enTitle, tcTitle, command.getArticleUuid());

        String parentTime = formatCreatedAt(command.getParentCommentCreatedAt());
        String parentPreview = truncate(command.getParentCommentMessage(), 300);

        String parentOwnerName = blankTo(command.getParentOwnerName(), "Someone");
        String parentOwnerEmail = command.getParentOwnerEmail();

        // Keep subject short for long bilingual titles (clients truncate anyway).
        String subject = isReply
                ? authorLabel + " replied in a discussion on \"" + truncate(articleDisplay, 80) + "\""
                : authorLabel + " commented on \"" + truncate(articleDisplay, 80) + "\"";

        // ── Send individual emails (personalized per recipient) ─────────────
        String firstMessageId = null;
        String firstTo = null;
        List<String> failures = new ArrayList<>();

        for (String recipientEmail : threadEmails) {
            // Per-recipient label (replies only): "You wrote" for parent owner.
            boolean isParentOwner = isReply && sameEmail(recipientEmail, parentOwnerEmail);
            String parentLabel = isParentOwner ? "You" : parentOwnerName;

            String textBody = isReply
                    ? buildTextBody(
                    authorLabel,
                    enTitle, tcTitle, articleUrl,
                    parentPreview, parentTime, parentLabel,
                    messagePreview)
                    : buildTopLevelTextBody(authorLabel, enTitle, tcTitle, articleUrl, messagePreview);

            String htmlBody = isReply
                    ? buildHtmlBody(
                    authorLabel,
                    enTitle, tcTitle, articleUrl,
                    parentPreview, parentTime, parentLabel,
                    messagePreview, command.getReplyCommentId())
                    : buildTopLevelHtmlBody(
                    authorLabel,
                    enTitle, tcTitle, articleUrl,
                    messagePreview, command.getReplyCommentId());

            try {
                String messageId = awsSesService.send(
                        recipientEmail, subject, textBody, htmlBody,
                        null, threadEmails);  // exclude all thread participants from CC
                if (firstMessageId == null) {
                    firstMessageId = messageId;
                    firstTo = recipientEmail;
                }
            } catch (Exception e) {
                String msg = recipientEmail + ": " + e.getMessage();
                log.error("SES send failed for {}", msg, e);
                failures.add(msg);
            }
        }

        if (firstMessageId == null) {
            throw new RuntimeException(
                    "All SES sends failed (" + failures.size() + " recipient(s)): "
                            + String.join("; ", failures));
        }

        if (!failures.isEmpty()) {
            log.warn("{} of {} SES sends failed: {}",
                    failures.size(), threadEmails.size(), String.join("; ", failures));
        }

        eventQueue.add(CommentGotRepliedNotificationSentEvent.builder()
                .messageId(firstMessageId)
                .toAddress(firstTo)
                .subject(subject)
                .replyCommentId(command.getReplyCommentId())
                .parentCommentId(command.getParentCommentId())
                .articleUuid(command.getArticleUuid())
                .threadEmails(threadEmails.stream().toList())
                .build());

        return SendCommentGotRepliedNotificationCommand.Result.builder()
                .messageId(firstMessageId)
                .toAddress(firstTo)
                .subject(subject)
                .skipped(false)
                .recipientCount(threadEmails.size())
                .build();
    }

    /**
     * Recipients for this create:
     * <ul>
     *   <li>Reply (parent set): thread participants via CTE (excluding author) ∪ site owners</li>
     *   <li>Root (parent null): site owners only (excluding author)</li>
     * </ul>
     */
    private Set<String> resolveRecipients(SendCommentGotRepliedNotificationCommand command) {
        Set<String> emails = new LinkedHashSet<>();

        String authorEmail = command.getReplierEmail();
        String authorLower = authorEmail == null || authorEmail.isBlank()
                ? null
                : authorEmail.trim().toLowerCase(Locale.ROOT);

        if (command.getParentCommentId() != null
                && authorEmail != null
                && !authorEmail.isBlank()) {
            emails.addAll(commentRepository.findThreadParticipantEmailsExcludingAuthor(
                    command.getParentCommentId(), authorEmail));
        }

        for (String site : siteNotifyEmails) {
            if (authorLower != null && site.equals(authorLower)) continue;
            emails.add(site);
        }

        // Defensive: drop blanks / author if CTE returned anything odd.
        emails.removeIf(e -> e == null || e.isBlank());
        if (authorLower != null) {
            emails.removeIf(e -> e.trim().toLowerCase(Locale.ROOT).equals(authorLower));
        }
        return emails;
    }

    private static Set<String> parseEmailList(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean sameEmail(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) return false;
        return a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
    }

    private static String blankTo(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        String t = value.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max) + "…";
    }

    private static String buildArticleDisplay(String articleTitle, String articleTitleTc, java.util.UUID articleUuid) {
        boolean hasEn = articleTitle != null && !articleTitle.isBlank();
        boolean hasTc = articleTitleTc != null && !articleTitleTc.isBlank();
        if (hasEn && hasTc) return articleTitle.trim() + " / " + articleTitleTc.trim();
        if (hasEn) return articleTitle.trim();
        if (hasTc) return articleTitleTc.trim();
        return articleUuid != null ? articleUuid.toString() : "(unknown)";
    }

    private static final DateTimeFormatter PARENT_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Hong_Kong"));

    private static String formatCreatedAt(Double createdAt) {
        if (createdAt == null || createdAt <= 0) return "(unknown)";
        try {
            return PARENT_TIME_FMT.format(Instant.ofEpochMilli(createdAt.longValue()));
        } catch (Exception e) {
            return "(unknown)";
        }
    }

    // ── Plain-text body ──────────────────────────────────────────────────

    private static String buildTopLevelTextBody(
            String author,
            String enTitle, String tcTitle, String articleUrl,
            String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello,\n\n");
        sb.append(author).append(" posted a new comment.\n\n");

        sb.append("Article\n");
        if (enTitle != null && !enTitle.isBlank()) {
            sb.append("  EN: ").append(enTitle.trim()).append('\n');
        }
        if (tcTitle != null && !tcTitle.isBlank()) {
            sb.append("  中文: ").append(tcTitle.trim()).append('\n');
        }
        if ((enTitle == null || enTitle.isBlank()) && (tcTitle == null || tcTitle.isBlank())) {
            sb.append("  (unknown article)\n");
        }
        sb.append('\n');

        sb.append("▼ New comment\n");
        sb.append("────────────────────────────────\n");
        sb.append(author).append(" wrote\n");
        sb.append(message == null ? "" : message).append("\n");
        sb.append("────────────────────────────────\n");

        return sb.toString();
    }

    private static String buildTextBody(
            String replier,
            String enTitle, String tcTitle, String articleUrl,
            String parentMsg, String parentTime, String parentLabel,
            String replyMsg) {

        StringBuilder sb = new StringBuilder();
        sb.append("Hello,\n\n");
        sb.append(replier).append(" replied in a discussion.\n\n");

        sb.append("Article\n");
        if (enTitle != null && !enTitle.isBlank()) {
            sb.append("  EN: ").append(enTitle.trim()).append('\n');
        }
        if (tcTitle != null && !tcTitle.isBlank()) {
            sb.append("  中文: ").append(tcTitle.trim()).append('\n');
        }
        if ((enTitle == null || enTitle.isBlank()) && (tcTitle == null || tcTitle.isBlank())) {
            sb.append("  (unknown article)\n");
        }
        sb.append('\n');

        sb.append("────────────────────────────────\n");
        sb.append(parentLabel).append(" wrote  ·  ").append(parentTime).append('\n');
        sb.append(parentMsg == null ? "" : parentMsg).append("\n");
        sb.append("────────────────────────────────\n\n");

        sb.append("▼ New reply\n");
        sb.append("────────────────────────────────\n");
        sb.append(replier).append(" replied\n");
        sb.append(replyMsg == null ? "" : replyMsg).append("\n");
        sb.append("────────────────────────────────\n");

        return sb.toString();
    }

    // ── HTML email template ──────────────────────────────────────────────
    // Table-based layout + inline styles for Gmail/Outlook compatibility.
    // Long bilingual titles stack with word-wrap; parent is a quiet quote card;
    // the reply is the visual focus with a stronger accent.

    private static final String FONT =
            "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    private static String esc(String s) {
        if (s == null || s.isEmpty()) return "";
        return s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Turn bare newlines into &lt;br&gt; after HTML-escaping (for message bodies).
     */
    private static String escMultiline(String s) {
        return esc(s).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>");
    }

    private static String buildArticleTitleBlock(String enTitle, String tcTitle) {
        boolean hasEn = enTitle != null && !enTitle.isBlank();
        boolean hasTc = tcTitle != null && !tcTitle.isBlank();

        if (!hasEn && !hasTc) {
            return """
                    <tr>
                      <td style="padding:0 0 4px 0;font-size:12px;font-weight:600;letter-spacing:0.08em;\
                    text-transform:uppercase;color:#8a8fa3">Article</td>
                    </tr>
                    <tr>
                      <td style="padding:0;font-size:16px;font-weight:600;color:#1a1a2e;\
                    line-height:1.45;word-break:break-word;overflow-wrap:anywhere">
                        (unknown article)
                      </td>
                    </tr>
                    """;
        }

        StringBuilder rows = new StringBuilder();
        rows.append("""
                <tr>
                  <td style="padding:0 0 10px 0;font-size:12px;font-weight:600;letter-spacing:0.08em;\
                text-transform:uppercase;color:#8a8fa3">Article</td>
                </tr>
                """);

        if (hasEn) {
            rows.append("""
                    <tr>
                      <td style="padding:0 0 %spx 0">
                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%">
                          <tr>
                            <td width="36" valign="top" style="padding:3px 10px 0 0;width:36px">
                              <span style="display:inline-block;font-size:10px;font-weight:700;\
                    letter-spacing:0.06em;color:#286197;background:#e8f0fa;border-radius:4px;\
                    padding:2px 6px;line-height:1.4">EN</span>
                            </td>
                            <td valign="top" style="font-size:16px;font-weight:600;color:#1a1a2e;\
                    line-height:1.45;word-break:break-word;overflow-wrap:anywhere">
                              %s
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                    """.formatted(hasTc ? "8" : "0", esc(enTitle.trim())));
        }

        if (hasTc) {
            rows.append("""
                    <tr>
                      <td style="padding:0">
                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%">
                          <tr>
                            <td width="36" valign="top" style="padding:3px 10px 0 0;width:36px">
                              <span style="display:inline-block;font-size:10px;font-weight:700;\
                    letter-spacing:0.04em;color:#5a6178;background:#eef0f5;border-radius:4px;\
                    padding:2px 6px;line-height:1.4">中文</span>
                            </td>
                            <td valign="top" style="font-size:15px;font-weight:500;color:#3d4258;\
                    line-height:1.5;word-break:break-word;overflow-wrap:anywhere">
                              %s
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                    """.formatted(esc(tcTitle.trim())));
        }

        return rows.toString();
    }

    private static String buildTopLevelHtmlBody(
            String author,
            String enTitle, String tcTitle, String articleUrl,
            String message, Object commentId) {
        String authorEscaped = esc(author);
        String messageBody = escMultiline(message == null ? "" : message);

        String ctaRow = "";
        if (articleUrl != null && !articleUrl.isBlank()) {
            String safeUrl = esc(articleUrl.trim() + "?comment=true");
            ctaRow = """
                    <tr>
                      <td style="padding:8px 28px 28px 28px" align="center">
                        <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                          <tr>
                            <td style="border-radius:8px;background:#286197">
                              <a href="%s"
                                 style="display:inline-block;padding:12px 28px;font-size:14px;\
                    font-weight:600;color:#ffffff;text-decoration:none;border-radius:8px;\
                    font-family:%s;letter-spacing:0.01em">
                                View discussion &rarr;
                              </a>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                    """.formatted(safeUrl, FONT);
        }

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <meta name="color-scheme" content="light">
                  <title>New comment</title>
                </head>
                <body style="margin:0;padding:0;background:#f0f2f7">
                  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%"
                         style="background:#f0f2f7;padding:24px 12px">
                    <tr>
                      <td align="center">
                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%"
                               style="max-width:600px;background:#ffffff;border-radius:12px;\
                overflow:hidden;border:1px solid #e4e7ef;font-family:%s;color:#1a1a2e">
                          <tr>
                            <td style="height:4px;background:linear-gradient(90deg,#286197,#4a8fd4);\
                font-size:0;line-height:0">&nbsp;</td>
                          </tr>
                          <tr>
                            <td style="padding:28px 28px 8px 28px">
                              <p style="margin:0 0 6px 0;font-size:12px;font-weight:600;\
                letter-spacing:0.08em;text-transform:uppercase;color:#8a8fa3">
                                New comment
                              </p>
                              <p style="margin:0;font-size:18px;font-weight:600;line-height:1.4;color:#1a1a2e">
                                <span style="color:#286197">%s</span> posted a new comment
                              </p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 28px 8px 28px">
                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%"
                                     style="background:#f7f8fc;border:1px solid #e8ebf3;border-radius:10px">
                                <tr>
                                  <td style="padding:16px 18px">
                                    <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%">
                %s
                                    </table>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:20px 28px 8px 28px">
                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%"
                                     style="background:#f0f6fc;border:1px solid #c8daf0;border-radius:10px;\
                border-left:4px solid #286197;box-shadow:0 1px 2px rgba(40,97,151,0.06)">
                                <tr>
                                  <td style="padding:14px 16px 6px 16px">
                                    <p style="margin:0;font-size:12px;font-weight:700;color:#286197;\
                letter-spacing:0.02em">
                                      %s
                                    </p>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:4px 16px 16px 16px;font-size:15px;line-height:1.7;\
                color:#1a1a2e;font-weight:500;word-break:break-word;overflow-wrap:anywhere">
                                    %s
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                %s
                          <tr>
                            <td style="padding:0 28px 24px 28px">
                              <p style="margin:0;padding-top:16px;border-top:1px solid #eef0f5;\
                font-size:12px;line-height:1.5;color:#9aa0b4">
                                You&rsquo;re receiving this because you follow comments
                                on machingclee.github.io.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                FONT,
                authorEscaped,
                buildArticleTitleBlock(enTitle, tcTitle),
                authorEscaped,
                messageBody,
                ctaRow);
    }

    @SuppressWarnings("unused") // replyId reserved for future deep-link anchors
    private static String buildHtmlBody(
            String replier,
            String enTitle, String tcTitle, String articleUrl,
            String parentMsg, String parentTime, String parentLabel,
            String replyMsg, Object replyId) {

        String replierEscaped = esc(replier);
        String parentLabelEscaped = esc(parentLabel);
        String parentBody = escMultiline(parentMsg == null ? "" : parentMsg);
        String replyBody = escMultiline(replyMsg == null ? "" : replyMsg);
        String parentTimeEscaped = esc(parentTime);

        String ctaRow = "";
        if (articleUrl != null && !articleUrl.isBlank()) {
            String safeUrl = esc(articleUrl.trim());
            ctaRow = """
                    <tr>
                      <td style="padding:8px 28px 28px 28px" align="center">
                        <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                          <tr>
                            <td style="border-radius:8px;background:#286197">
                              <a href="%s"
                                 style="display:inline-block;padding:12px 28px;font-size:14px;\
                    font-weight:600;color:#ffffff;text-decoration:none;border-radius:8px;\
                    font-family:%s;letter-spacing:0.01em">
                                View discussion &rarr;
                              </a>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                    """.formatted(safeUrl, FONT);
        }

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <meta name="color-scheme" content="light">
                  <title>New reply</title>
                </head>
                <body style="margin:0;padding:0;background:#f0f2f7">
                  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%"
                         style="background:#f0f2f7;padding:24px 12px">
                    <tr>
                      <td align="center">
                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%"
                               style="max-width:600px;background:#ffffff;border-radius:12px;\
                overflow:hidden;border:1px solid #e4e7ef;font-family:%s;color:#1a1a2e">
                
                          <!-- Accent bar -->
                          <tr>
                            <td style="height:4px;background:linear-gradient(90deg,#286197,#4a8fd4);\
                font-size:0;line-height:0">&nbsp;</td>
                          </tr>
                
                          <!-- Intro -->
                          <tr>
                            <td style="padding:28px 28px 8px 28px">
                              <p style="margin:0 0 6px 0;font-size:12px;font-weight:600;\
                letter-spacing:0.08em;text-transform:uppercase;color:#8a8fa3">
                                New reply
                              </p>
                              <p style="margin:0;font-size:18px;font-weight:600;line-height:1.4;color:#1a1a2e">
                                <span style="color:#286197">%s</span> replied in a discussion
                              </p>
                            </td>
                          </tr>
                
                          <!-- Article titles (stacked, wrap-friendly) -->
                          <tr>
                            <td style="padding:18px 28px 8px 28px">
                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%"
                                     style="background:#f7f8fc;border:1px solid #e8ebf3;border-radius:10px">
                                <tr>
                                  <td style="padding:16px 18px">
                                    <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%">
                %s
                                    </table>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                
                          <!-- Thread: parent (quoted) -->
                          <tr>
                            <td style="padding:20px 28px 0 28px">
                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%"
                                     style="background:#fafafc;border:1px solid #e8ebf3;border-radius:10px;\
                border-left:4px solid #b0b6cc">
                                <tr>
                                  <td style="padding:14px 16px 6px 16px">
                                    <p style="margin:0;font-size:12px;font-weight:600;color:#6b728a;\
                letter-spacing:0.02em">
                                      %s wrote
                                      <span style="font-weight:500;color:#9aa0b4"> &middot; %s</span>
                                    </p>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:4px 16px 14px 16px;font-size:14px;line-height:1.65;\
                color:#4b5168;word-break:break-word;overflow-wrap:anywhere">
                                    %s
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                
                          <!-- Connector -->
                          <tr>
                            <td style="padding:6px 28px;text-align:center;font-size:18px;line-height:1;\
                color:#b0b6cc;letter-spacing:0">
                              &#8942;
                            </td>
                          </tr>
                
                          <!-- Thread: reply (highlight) -->
                          <tr>
                            <td style="padding:0 28px 8px 28px">
                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%"
                                     style="background:#f0f6fc;border:1px solid #c8daf0;border-radius:10px;\
                border-left:4px solid #286197;box-shadow:0 1px 2px rgba(40,97,151,0.06)">
                                <tr>
                                  <td style="padding:14px 16px 6px 16px">
                                    <p style="margin:0;font-size:12px;font-weight:700;color:#286197;\
                letter-spacing:0.02em">
                                      <span style="display:inline-block;background:#286197;color:#fff;\
                font-size:10px;font-weight:700;letter-spacing:0.06em;text-transform:uppercase;\
                border-radius:3px;padding:2px 6px;margin-right:8px;vertical-align:middle;\
                line-height:1.4">Reply</span>
                                      %s
                                    </p>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:4px 16px 16px 16px;font-size:15px;line-height:1.7;\
                color:#1a1a2e;font-weight:500;word-break:break-word;overflow-wrap:anywhere">
                                    %s
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                
                          <!-- CTA -->
                %s
                
                          <!-- Footer -->
                          <tr>
                            <td style="padding:0 28px 24px 28px">
                              <p style="margin:0;padding-top:16px;border-top:1px solid #eef0f5;\
                font-size:12px;line-height:1.5;color:#9aa0b4">
                                You&rsquo;re receiving this because you participated in a comment thread
                                on machingclee.github.io.
                              </p>
                            </td>
                          </tr>
                
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                FONT,
                replierEscaped,
                buildArticleTitleBlock(enTitle, tcTitle),
                parentLabelEscaped,
                parentTimeEscaped,
                parentBody,
                replierEscaped,
                replyBody,
                ctaRow);
    }
}
