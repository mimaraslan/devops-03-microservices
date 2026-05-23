package com.mimaraslan.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@RequiredArgsConstructor
public class MailStartupVerifier {

    private final MailEnvProperties mailEnv;

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        if (!StringUtils.hasText(mailEnv.getUsername()) || !StringUtils.hasText(mailEnv.getPassword())) {
            log.error("Mail hazir degil — .env MAIL_USERNAME / MAIL_PASSWORD kontrol edin");
            return;
        }
        log.info("Mail hazir: host={}, user={}", mailEnv.getHost(), mailEnv.getUsername());
    }
}
