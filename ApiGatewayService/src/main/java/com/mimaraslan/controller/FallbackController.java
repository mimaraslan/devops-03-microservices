package com.mimaraslan.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    @RequestMapping(
            value = "/account",
            method = {
                    RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.PATCH,
                    RequestMethod.OPTIONS, RequestMethod.HEAD
            })
    public ResponseEntity<Map<String, Object>> fallbackAccount(ServerWebExchange exchange) {
        return buildFallback("account", exchange);
    }

    @RequestMapping(
            value = "/notification",
            method = {
                    RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.PATCH,
                    RequestMethod.OPTIONS, RequestMethod.HEAD
            })
    public ResponseEntity<Map<String, Object>> fallbackNotification(ServerWebExchange exchange) {
        return buildFallback("notification", exchange);
    }

    @RequestMapping(
            value = "/fraud",
            method = {
                    RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.PATCH,
                    RequestMethod.OPTIONS, RequestMethod.HEAD
            })
    public ResponseEntity<Map<String, Object>> fallbackFraud(ServerWebExchange exchange) {
        return buildFallback("fraud", exchange);
    }

    @RequestMapping(
            value = "/ledger",
            method = {
                    RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.PATCH,
                    RequestMethod.OPTIONS, RequestMethod.HEAD
            })
    public ResponseEntity<Map<String, Object>> fallbackLedger(ServerWebExchange exchange) {
        return buildFallback("ledger", exchange);
    }

    private ResponseEntity<Map<String, Object>> buildFallback(String service, ServerWebExchange exchange) {
        Throwable cause = (Throwable) exchange.getAttributes()
                .get(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);

        String exceptionType = cause != null ? cause.getClass().getName() : "n/a";
        String exceptionMessage = cause != null ? cause.getMessage() : "n/a";
        String rootType = "n/a";
        String rootMessage = "n/a";
        if (cause != null) {
            Throwable root = cause;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            rootType = root.getClass().getName();
            rootMessage = root.getMessage();
        }

        log.error("[FALLBACK:{}] CircuitBreaker tetiklendi. uri={} method={} exception={} message={} rootCause={}: {}",
                service,
                exchange.getRequest().getURI(),
                exchange.getRequest().getMethod(),
                exceptionType,
                exceptionMessage,
                rootType,
                rootMessage,
                cause);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", service);
        body.put("message", service + " Service: Şu anda geçici olarak hizmet verememekteyiz.");
        body.put("exceptionType", exceptionType);
        body.put("exceptionMessage", exceptionMessage);
        body.put("rootCauseType", rootType);
        body.put("rootCauseMessage", rootMessage);
        body.put("path", exchange.getRequest().getURI().getPath());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
