package com.senac.tsi.MinhaPrimeiraApi.security;

import tools.jackson.databind.json.JsonMapper;
import com.senac.tsi.MinhaPrimeiraApi.model.ApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita a quantidade de requisições por chave de API autenticada.
 * Para a criação pública de chaves (que ainda não possui uma chave), utiliza o IP do cliente.
 * Implementa uma janela fixa de tempo, com valores configuráveis no application.properties.
 */
@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API_KEY_ATTRIBUTE = ApiKeyAuthenticationFilter.API_KEY_ATTRIBUTE;
    private final JsonMapper objectMapper;
    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(
            JsonMapper objectMapper,
            @Value("${app.rate-limit.max-requests:60}") int maxRequests,
            @Value("${app.rate-limit.window-seconds:60}") long windowSeconds) {
        this.objectMapper = objectMapper;
        this.maxRequests = Math.max(1, maxRequests);
        this.windowMillis = Math.max(1, windowSeconds) * 1000L;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // O limite é aplicado às rotas da API; Swagger, arquivos estáticos e console H2 ficam fora.
        return !("/api".equals(path) || path.startsWith("/api/"))
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String client = identifyClient(request);
        long now = System.currentTimeMillis();
        // Remove janelas antigas ocasionalmente para evitar crescimento indefinido do mapa.
        if (windows.size() > 1000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt > windowMillis * 2);
        }
        Window window = windows.compute(client, (key, current) -> {
            if (current == null || now >= current.startedAt + windowMillis) {
                return new Window(now);
            }
            return current;
        });

        synchronized (window) {
            long resetAt = window.startedAt + windowMillis;
            long secondsUntilReset = Math.max(1, (long) Math.ceil((resetAt - now) / 1000.0));

            response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - window.requests - 1)));
            response.setHeader("X-RateLimit-Reset", String.valueOf(Instant.ofEpochMilli(resetAt).getEpochSecond()));

            if (window.requests >= maxRequests) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(secondsUntilReset));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                objectMapper.writeValue(response.getWriter(), Map.of(
                        "status", 429,
                        "erro", "Limite de requisições excedido. Aguarde antes de tentar novamente.",
                        "limite", maxRequests,
                        "tenteNovamenteEmSegundos", secondsUntilReset));
                return;
            }

            window.requests++;
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - window.requests)));
        }

        filterChain.doFilter(request, response);
    }

    private String identifyClient(HttpServletRequest request) {
        Object authenticated = request.getAttribute(API_KEY_ATTRIBUTE);
        if (authenticated instanceof ApiKey apiKey) {
            return "api-key:" + apiKey.getId();
        }
        // Usado principalmente no endpoint público de geração da primeira chave.
        return "ip:" + request.getRemoteAddr();
    }

    private static final class Window {
        private final long startedAt;
        private int requests;

        private Window(long startedAt) {
            this.startedAt = startedAt;
            this.requests = 0;
        }
    }
}
