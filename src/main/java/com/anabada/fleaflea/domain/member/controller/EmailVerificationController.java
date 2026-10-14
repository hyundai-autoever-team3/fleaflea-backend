package com.anabada.fleaflea.domain.member.controller;

import com.anabada.fleaflea.domain.member.dto.EmailCodeSendRequest;
import com.anabada.fleaflea.domain.member.dto.EmailCodeVerifyRequest;
import com.anabada.fleaflea.domain.member.dto.EmailCodeVerifyResponse;
import com.anabada.fleaflea.domain.member.service.EmailVerificationService;
import com.anabada.fleaflea.domain.member.repository.RedisEmailVerificationRepository.VerificationResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {
    private final EmailVerificationService verificationService;

    @PostMapping("/code")
    public ResponseEntity<Void> sendCode(
            @Valid @RequestBody EmailCodeSendRequest request
    ) {
        verificationService.sendCode(
                request.email(),
                request.purpose()
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<EmailCodeVerifyResponse> verifyCode(
            @Valid @RequestBody EmailCodeVerifyRequest request
            ) {
        EmailCodeVerifyResponse response =  verificationService.verifyCode(
                request.email(),
                request.purpose(),
                request.code()
        );

        VerificationResult result = VerificationResult.valueOf(response.result());

        HttpStatus status = switch (result) {
            case SUCCESS -> HttpStatus.OK;
            case NOT_FOUND, MISMATCH -> HttpStatus.BAD_REQUEST;
            case ATTEMPTS_EXCEED -> HttpStatus.TOO_MANY_REQUESTS;
        };

        return ResponseEntity.status(status)
                .body(response);
    }
}
