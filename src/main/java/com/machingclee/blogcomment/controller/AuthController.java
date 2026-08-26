package com.machingclee.blogcomment.controller;

import com.machingclee.blogcomment.common.aop.logging.LogQuery;
import com.machingclee.blogcomment.common.aop.logging.LogRequest;
import com.machingclee.blogcomment.common.auth.GoogleTokenVerifier;
import com.machingclee.blogcomment.common.auth.VerifiedIdentity;
import com.machingclee.blogcomment.common.dto.ApiResponse;
import com.machingclee.blogcomment.common.dto.GoogleAuthRequest;
import com.machingclee.blogcomment.common.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication for blog comments.
 * <p>
 * POST /api/auth/google  body: { "googleIdToken": "<GSI JWT>" }
 * → 200 { success, result: { googleSub, email, name, picture } }
 * → 401 { success:false, errorMessage: "invalid google id token" }
 */
@Tag(name = "Auth", description = "Google Sign-In verification")
@RestController
@RequestMapping("/api/auth/google")
@LogRequest
@LogQuery
@RequiredArgsConstructor
public class AuthController {

    private final GoogleTokenVerifier googleTokenVerifier;

    @Operation(summary = "Verify Google ID token", description = "Verifies a Google Sign-In JWT server-side and returns the user identity.")
    @PostMapping
    public ApiResponse<VerifiedIdentity> authenticate(@RequestBody GoogleAuthRequest request) {
        VerifiedIdentity identity = googleTokenVerifier.verify(request.googleIdToken())
                .orElseThrow(() -> new UnauthorizedException("invalid google id token"));
        return ApiResponse.success(ApiResponse.SuccessParam.<VerifiedIdentity>builder()
                .payload(identity)
                .build());
    }
}
