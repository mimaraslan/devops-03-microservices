package com.mimaraslan.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SwaggerConfig {

    // Discovery client üzerinden servislerin bulunması ve gruplanması
    @Bean
    public List<GroupedOpenApi> apis(RouteDefinitionLocator locator) {
        List<GroupedOpenApi> groups = new ArrayList<>();

        // Tüm rota tanımlarını çek
        locator.getRouteDefinitions().collectList().block()
                .stream()
                // Sadece gerçekten bir servise yönlenen rotaları filtrele (Örn: ID'si 'auth' olan)
                .filter(routeDefinition -> routeDefinition.getId().matches(".*-service"))
                .forEach(routeDefinition -> {
                    String name = routeDefinition.getId().replace("-service", ""); // Örn: 'auth'
                    // Rota predikatlarından yolu (Path) bul
                    String path = routeDefinition.getPredicates().stream()
                            .filter(predicateDefinition -> ("Path").equalsIgnoreCase(predicateDefinition.getName()))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("Rota yolu (Path) bulunamadı."))
                            .getArgs().get("_genkey_0").replace("/**", "/**");

                    // Gruplanmış OpenAPI nesnesini oluştur
                    groups.add(GroupedOpenApi.builder()
                            .pathsToMatch(path) // Örn: /auth/**
                            .group(name)        // Örn: auth
                            .build());
                });
        return groups;
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
