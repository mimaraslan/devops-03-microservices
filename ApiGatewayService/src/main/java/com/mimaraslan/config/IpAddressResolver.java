package com.mimaraslan.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Spring Cloud Gateway için özel KeyResolver.
 * Gelen isteğin IP adresini Rate Limiting için anahtar olarak kullanır.
 * Bu sayede, aynı IP'den gelen isteklere limit uygulanabilir.
 */
@Component // Bu sınıfı Spring Bean'i olarak işaretler
public class IpAddressResolver implements KeyResolver {

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        // İstemcinin IP adresini Rate Limiting için anahtar olarak kullan
        // exchange.getRequest().getRemoteAddress() null dönebilir, güvenli kontrol önemlidir.
        String ipAddress = "unknown"; // Varsayılan değer

        if (exchange.getRequest().getRemoteAddress() != null) {
            ipAddress = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }

        // Loglama veya hata ayıklama için
        // System.out.println("Rate Limit Key for IP: " + ipAddress);

        return Mono.just(ipAddress);
    }
}