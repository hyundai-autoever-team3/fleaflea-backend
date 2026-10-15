package com.anabada.fleaflea.global.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;

    public void sendVerificationCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[fleaflea] 이메일 인증 코드");
        message.setText(
                "인증 코드는 " + code + "입니다.\n" +
                        "5분 이내에 입력해주세요."
        );

        mailSender.send(message);
    }
}
