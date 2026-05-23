package com.mimaraslan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimaraslan.dto.AccountResponse;
import com.mimaraslan.dto.NotificationTransferEvent;
import jakarta.mail.internet.MimeMessage;
import com.mimaraslan.config.MailEnvProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailEnvProperties mailEnv;

    public void sendNotification(String message) {
        try {
            NotificationTransferEvent event = objectMapper.readValue(message, NotificationTransferEvent.class);

            log.info("[NotificationService] Transfer event received: transferId={} from={} to={} amount={}",
                    event.getTransferId(),
                    event.getFromAccount(),
                    event.getToAccount(),
                    event.getAmount());

            AccountResponse fromAccountInfo = event.getSenderAccount();
            AccountResponse toAccountInfo = event.getReceiverAccount();

            String senderIban = formatLedgerIban(event.getFromAccount());
            String receiverIban = formatLedgerIban(event.getToAccount());

            if (fromAccountInfo != null && fromAccountInfo.getEmail() != null) {
                String html = createEmailTemplate(
                        fromAccountInfo.getFirstName() + " " + fromAccountInfo.getLastName(),
                        event.getAmount().toString(),
                        senderIban,
                        "Para Çıkışı",
                        senderIban,
                        receiverIban,
                        fromAccountInfo,
                        toAccountInfo
                );
                sendHtmlMail(fromAccountInfo.getEmail(), "Transfer Bildirimi", html);
            }

            if (toAccountInfo != null && toAccountInfo.getEmail() != null) {
                String html = createEmailTemplate(
                        toAccountInfo.getFirstName() + " " + toAccountInfo.getLastName(),
                        event.getAmount().toString(),
                        receiverIban,
                        "Para Girişi",
                        senderIban,
                        receiverIban,
                        fromAccountInfo,
                        toAccountInfo
                );
                sendHtmlMail(toAccountInfo.getEmail(), "Transfer Bildirimi", html);
            }

        } catch (Exception e) {
            log.error("NotificationService ERROR:", e);
            throw new RuntimeException("Unexpected notification error", e);
        }
    }

    private String formatLedgerIban(Long ledgerIban) {
        if (ledgerIban == null) {
            return "—";
        }
        String raw = String.format("TR%024d", ledgerIban);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < raw.length(); i += 4) {
            if (i > 0) {
                grouped.append(' ');
            }
            grouped.append(raw, i, Math.min(i + 4, raw.length()));
        }
        return grouped.toString();
    }

    private String createEmailTemplate(String fullName, String amount, String iban, String type,
                                       String senderIban, String receiverIban,
                                       AccountResponse senderInfo, AccountResponse receiverInfo) {
        Context context = new Context();

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
        java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");

        String referenceNumber = "TRF-" + System.currentTimeMillis() + "-"
                + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        context.setVariable("fullName", fullName);
        context.setVariable("amount", amount);
        context.setVariable("iban", iban);
        context.setVariable("senderIban", senderIban);
        context.setVariable("receiverIban", receiverIban);
        context.setVariable("type", type);
        context.setVariable("transactionDate", now.format(dateFormatter));
        context.setVariable("transactionTime", now.format(timeFormatter));
        context.setVariable("referenceNumber", referenceNumber);

        if (senderInfo != null) {
            context.setVariable("senderName", senderInfo.getFirstName() + " " + senderInfo.getLastName());
            context.setVariable("senderEmail", senderInfo.getEmail() != null ? senderInfo.getEmail() : "");
            context.setVariable("senderPhone", senderInfo.getPhoneNumber() != null ? senderInfo.getPhoneNumber() : "");
            context.setVariable("senderAddress", senderInfo.getAddress() != null ? senderInfo.getAddress() : "");
        } else {
            context.setVariable("senderName", "");
            context.setVariable("senderEmail", "");
            context.setVariable("senderPhone", "");
            context.setVariable("senderAddress", "");
        }

        if (receiverInfo != null) {
            context.setVariable("receiverName", receiverInfo.getFirstName() + " " + receiverInfo.getLastName());
            context.setVariable("receiverEmail", receiverInfo.getEmail() != null ? receiverInfo.getEmail() : "");
            context.setVariable("receiverPhone", receiverInfo.getPhoneNumber() != null ? receiverInfo.getPhoneNumber() : "");
            context.setVariable("receiverAddress", receiverInfo.getAddress() != null ? receiverInfo.getAddress() : "");
        } else {
            context.setVariable("receiverName", "");
            context.setVariable("receiverEmail", "");
            context.setVariable("receiverPhone", "");
            context.setVariable("receiverAddress", "");
        }

        return templateEngine.process("transfer-email", context);
    }

    private void sendHtmlMail(String to, String subject, String htmlContent) {
        try {
            String mailFrom = mailEnv.getUsername();
            if (!StringUtils.hasText(mailFrom)) {
                log.error("sendHtmlMail atlandi: .env MAIL_USERNAME bos");
                return;
            }

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);

            log.info("HTML Mail sent to {}", to);

        } catch (Exception e) {
            log.error("sendHtmlMail ERROR", e);
        }
    }
}
