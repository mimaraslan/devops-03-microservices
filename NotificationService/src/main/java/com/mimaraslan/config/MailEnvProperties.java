package com.mimaraslan.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.Map;

/**
 * Mail ayarlari yalnizca kok .env dosyasindan (MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD).
 */
@Component
@Getter
@Slf4j
public class MailEnvProperties {

    private final String host;
    private final int port;
    private final String username;
    private final String password;

    public MailEnvProperties() {
        Path envPath = DotEnvLoader.locateMonorepoEnv();
        Map<String, String> dotenv = envPath != null ? DotEnvLoader.load(envPath) : Map.of();

        host = orEnv(dotenv, "MAIL_HOST", "smtp.gmail.com");
        port = Integer.parseInt(orEnv(dotenv, "MAIL_PORT", "587"));
        username = orEnv(dotenv, "MAIL_USERNAME", null);
        password = orEnv(dotenv, "MAIL_PASSWORD", null);

        if (envPath != null) {
            log.info("Mail .env yuklendi: {} (user={})", envPath.toAbsolutePath(), username);
        } else {
            log.error("Mail: .env bulunamadi (cwd={})", System.getProperty("user.dir"));
        }

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.error("Mail: MAIL_USERNAME veya MAIL_PASSWORD .env icinde bos");
        }
    }

    private static String orEnv(Map<String, String> dotenv, String key, String defaultValue) {
        String v = dotenv.get(key);
        if (!StringUtils.hasText(v)) {
            v = System.getenv(key);
        }
        return StringUtils.hasText(v) ? v : defaultValue;
    }
}
