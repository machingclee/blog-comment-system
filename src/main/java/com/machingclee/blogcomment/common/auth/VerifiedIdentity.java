package com.machingclee.blogcomment.common.auth;

/**
 * Identity extracted from a verified Google ID token.
 */
public record VerifiedIdentity(
        String googleSub,
        String email,
        String name,
        String picture) {
}
