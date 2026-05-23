package com.mimaraslan.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Component
public class GatewayRedirectFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayRedirectFilter.class);

    private final String gatewayBaseUrl;
    private final int servicePort;

    public GatewayRedirectFilter(
            @Value("${gateway.redirect.base-url:http://localhost:80}") String gatewayBaseUrl,
            @Value("${server.port:0}") int servicePort) {
        this.gatewayBaseUrl = gatewayBaseUrl;
        this.servicePort = servicePort;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // keep actuator and swagger reachable
        return path.startsWith("/actuator")
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // If request already passed through gateway, it should carry X-Forwarded-Host; skip redirect
        if (hasForwardedHeaders(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String hostHeader = request.getHeader(HttpHeaders.HOST);
        boolean directServiceCall = hostHeader != null && hostHeader.contains(":" + servicePort);

        if (directServiceCall) {
            String redirectUrl = gatewayBaseUrl + request.getRequestURI();
            String query = request.getQueryString();
            if (StringUtils.hasText(query)) {
                redirectUrl = redirectUrl + "?" + query;
            }

            response.setStatus(HttpStatus.TEMPORARY_REDIRECT.value()); // 307 preserves method/body
            response.setHeader(HttpHeaders.LOCATION, redirectUrl);
            response.setContentType("application/json");
            String body = """
                    {
                      "message": "Bu servis dogrudan erisime kapali, lutfen API Gateway uzerinden erisin.",
                      "from": "%s",
                      "to": "%s"
                    }
                    """.formatted(request.getRequestURL(), redirectUrl);
            response.getOutputStream().write(body.getBytes());

            log.info("Direct call redirected from {} to {}", request.getRequestURL(), redirectUrl);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasForwardedHeaders(HttpServletRequest request) {
        return StringUtils.hasText(request.getHeader("X-Forwarded-Host"))
                || StringUtils.hasText(request.getHeader("Forwarded"));
    }
}

