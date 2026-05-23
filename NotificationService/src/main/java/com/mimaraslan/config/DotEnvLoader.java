package com.mimaraslan.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Proje kokundeki .env — UTF-8, bosluklu degerler (Gmail App Password). */
final class DotEnvLoader {

    private DotEnvLoader() {
    }

    static Map<String, String> load(Path envFile) {
        try {
            Map<String, String> map = new HashMap<>();
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = stripBom(line.trim());
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                if (!key.isEmpty()) {
                    map.put(key, value);
                }
            }
            return map;
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }

    static Path locateMonorepoEnv() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path env = dir.resolve(".env");
            if (Files.isRegularFile(env)) {
                return env;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static String stripBom(String value) {
        if (value.startsWith("\uFEFF")) {
            return value.substring(1);
        }
        return value;
    }
}
