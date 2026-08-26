package com.machingclee.blogcomment.common.dto;

/**
 * POST /api/auth/google request body — the GSI ID token (JWT) from the frontend.
 * A missing or blank token is rejected by GoogleTokenVerifier.verify with 401.
 */
public record GoogleAuthRequest(
        String googleIdToken) {
}
