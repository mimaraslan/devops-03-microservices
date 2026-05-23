package com.mimaraslan.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(ex -> ex
                // Public endpoints
                .pathMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/actuator/**",
                    "/fallback/**",
                    "/auth/**",
                    // Account register/login/logout publicly reachable
                    "/account/**"
                ).permitAll()
                // Everything else requires JWT
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .build();
    }
    

    /**
     * JWT içindeki realm_access.roles veya resource_access rollerini ROLE_ prefix'iyle authorities'e map eder.
     */
    private ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Keycloak tarzı realm_access.roles
            Collection<String> roles = extractRoles(jwt.getClaims());
            SimpleAuthorityMapper mapper = new SimpleAuthorityMapper();
            mapper.setConvertToUpperCase(true);
            mapper.setPrefix("ROLE_");
            return mapper.mapAuthorities(
                    roles.stream()
                            .map(r -> (org.springframework.security.core.GrantedAuthority) () -> r)
                            .collect(Collectors.toList())
            );
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    @SuppressWarnings("unchecked")
    private Collection<String> extractRoles(Map<String, Object> claims) {
        // realm_access.roles
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmMap) {
            Object roles = realmMap.get("roles");
            if (roles instanceof Collection<?> c) {
                return c.stream().map(Object::toString).collect(Collectors.toSet());
            }
        }
        // resource_access.{client}.roles
        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map<?, ?> resMap) {
            return resMap.values().stream()
                    .filter(v -> v instanceof Map)
                    .map(v -> ((Map<?, ?>) v).get("roles"))
                    .filter(r -> r instanceof Collection<?>)
                    .flatMap(r -> ((Collection<?>) r).stream())
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        }
        return java.util.Set.of();
    }
}

