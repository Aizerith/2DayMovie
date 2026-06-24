package com.example.backend.common.mail;

import com.example.backend.common.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Primary
@Slf4j
public class LoggingMailService implements MailService {

    private final DevMailInboxService devMailInboxService;
    private final AppProperties appProperties;
    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;

    public LoggingMailService(
            DevMailInboxService devMailInboxService,
            AppProperties appProperties,
            ObjectProvider<JavaMailSender> javaMailSenderProvider
    ) {
        this.devMailInboxService = devMailInboxService;
        this.appProperties = appProperties;
        this.javaMailSenderProvider = javaMailSenderProvider;
    }

    @Override
    public void send(MailMessage message) {
        devMailInboxService.record(message);

        log.info("""
                Simulated mail delivery
                To: {}
                Subject: {}
                Body:
                {}
                """, message.to(), message.subject(), message.body());

        sendViaSmtpIfEnabled(message);
    }

    private void sendViaSmtpIfEnabled(MailMessage message) {
        if (!appProperties.getMail().isSmtpEnabled()) {
            return;
        }

        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (javaMailSender == null) {
            log.warn("SMTP delivery is enabled but no JavaMailSender is configured.");
            return;
        }

        SimpleMailMessage smtpMessage = new SimpleMailMessage();
        smtpMessage.setFrom(appProperties.getMail().getFromAddress());
        smtpMessage.setTo(message.to());
        smtpMessage.setSubject(message.subject());
        smtpMessage.setText(message.body());

        try {
            javaMailSender.send(smtpMessage);
        } catch (Exception exception) {
            log.warn("SMTP delivery failed for {}", message.to(), exception);
        }
    }
}
