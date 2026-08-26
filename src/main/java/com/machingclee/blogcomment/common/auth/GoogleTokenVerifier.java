package com.machingclee.blogcomment.common.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Verifies Google Sign-In ID tokens (JWT) for the commentsystem API.
 * <p>
 * Checks (via the classic GoogleIdTokenVerifier + an explicit issuer whitelist):
 * - RS256 signature against Google's public keys
 * - `aud` === our configured client id
 * - `exp` not passed
 * - `iss` ∈ {accounts.google.com, https://accounts.google.com}
 * <p>
 * Any failure (malformed/expired/wrong-audience/wrong-issuer) → empty, so the
 * caller can answer 401 — never a 500.
 */
@Component
public class GoogleTokenVerifier {

    private static final Set<String> ALLOWED_ISSUERS = Set.of(
            "accounts.google.com",
            "https://accounts.google.com"
    );

    @Value("${google.auth.client-id}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    // Field injection (@Value) happens AFTER the constructor runs, so the
    // verifier is built here, once injection is complete.
    @PostConstruct
    void init() {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(googleClientId))
                .setIssuer("https://accounts.google.com")
                .build();
    }

    public Optional<VerifiedIdentity> verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return Optional.empty();
        }
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                return Optional.empty();
            }
            GoogleIdToken.Payload payload = token.getPayload();
            String iss = payload.getIssuer();
            if (!ALLOWED_ISSUERS.contains(iss)) {
                return Optional.empty();
            }
            return Optional.of(new VerifiedIdentity(
                    payload.getSubject(),
                    payload.getEmail(),
                    (String) payload.get("name"),
                    (String) payload.get("picture")));
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
