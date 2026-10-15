package com.anabada.fleaflea.domain.member.service;

import com.anabada.fleaflea.domain.member.domain.EmailVerificationPurpose;
import com.anabada.fleaflea.domain.member.dto.EmailCodeVerifyResponse;
import com.anabada.fleaflea.domain.member.exception.EmailVerificationConfigurationException;
import com.anabada.fleaflea.domain.member.exception.EmailVerificationHashingException;
import com.anabada.fleaflea.domain.member.repository.RedisEmailVerificationRepository;
import com.anabada.fleaflea.domain.member.repository.RedisEmailVerificationRepository.VerificationResult;
import com.anabada.fleaflea.global.mail.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class EmailVerificationService {
    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    private final SecureRandom secureRandom = new SecureRandom();
    private final RedisEmailVerificationRepository repository;
    private final EmailService emailService;
    private final String hashSecret;

    public EmailVerificationService(
            RedisEmailVerificationRepository repository,
            EmailService emailService,
            @Value("${app.email-verification.hash-secret}") String hashSecret
    ) {
        if (hashSecret == null || hashSecret.isBlank()) {
            throw new EmailVerificationConfigurationException();
        }
        this.repository = repository;
        this.emailService = emailService;
        this.hashSecret = hashSecret;
    }

    public void sendCode(
            String email,
            EmailVerificationPurpose purpose
    ) {
        String code = generateCode();
        String codeHash = hash(email, purpose, code);

        repository.save(
                email,
                purpose.name(),
                codeHash,
                CODE_TTL
        );

        emailService.sendVerificationCode(email, code);
    }

    public EmailCodeVerifyResponse verifyCode(
            String email,
            EmailVerificationPurpose purpose,
            String code
    ) {
        VerificationResult result = repository.verifyAndConsume(
                email,
                purpose.name(),
                hash(email, purpose, code)
        );

        String verificationToken = null;

        if (result == VerificationResult.SUCCESS) {
            verificationToken = repository.issueVerificationToken(email, purpose);
        }

        return new EmailCodeVerifyResponse(
                result.name(),
                verificationToken
        );
    }

    private String generateCode() {
        return String.format(
                Locale.ROOT,
                "%06d",
                secureRandom.nextInt(1_000_000)
        );
    }

    private String hash(
            String email,
            EmailVerificationPurpose purpose,
            String code
    ) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    hashSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));

            String input = purpose.name() + ":" + email + ":" + code;

            return HexFormat.of().formatHex(
                    mac.doFinal(input.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException exception) {
            throw new EmailVerificationHashingException(exception);
        }
    }
}
