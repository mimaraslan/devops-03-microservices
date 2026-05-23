package com.mimaraslan.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * spring.config.import'tan once proje kokundeki .env yuklenir (MAIL_* vb.).
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "monorepoDotEnv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envFile = DotEnvLoader.locateMonorepoEnv();
        if (envFile == null) {
            return;
        }
        Map<String, String> properties = DotEnvLoader.load(envFile);
        if (properties.isEmpty()) {
            return;
        }
        environment.getPropertySources().addFirst(
                new MapPropertySource(PROPERTY_SOURCE_NAME, new HashMap<>(properties)));
    }
}
