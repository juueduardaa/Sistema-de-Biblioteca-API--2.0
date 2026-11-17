package com.senac.tsi.MinhaPrimeiraApi.security;

import com.senac.tsi.MinhaPrimeiraApi.model.ApiKeyRole;
import com.senac.tsi.MinhaPrimeiraApi.model.ApiKey;
import com.senac.tsi.MinhaPrimeiraApi.service.ApiKeyService;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Map;

@Component
@Order(1)
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    public static final String API_KEY_ATTRIBUTE = "authenticatedApiKey";
    private final ApiKeyService apiKeyService;
    private final JsonMapper objectMapper;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, JsonMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // A criação inicial da chave é pública para permitir o primeiro acesso à API.
        // Requisições OPTIONS também passam para permitir preflight de CORS na próxima etapa.
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || ("POST".equalsIgnoreCase(request.getMethod()) && "/api/chaves-api".equals(path))
                || !("/api".equals(path) || path.startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String rawKey = request.getHeader("X-API-Key");
        ApiKey apiKey = apiKeyService.validate(rawKey).orElse(null);
        if (apiKey == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "status", 401,
                    "erro", "Chave de API ausente ou inválida. Envie uma chave válida no cabeçalho X-API-Key."));
            return;
        }
        // Usuários comuns não podem atualizar registros.
        if (apiKey.getRole() == ApiKeyRole.USER
                && ("PUT".equalsIgnoreCase(request.getMethod())
                || "PATCH".equalsIgnoreCase(request.getMethod()))) {

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            objectMapper.writeValue(response.getWriter(), Map.of(
                    "status", 403,
                    "erro", "Acesso negado. Somente administradores podem atualizar registros."
            ));
            return;
        }

        // Usuários comuns não podem atualizar registros.
        if (apiKey.getRole() == ApiKeyRole.USER
                && ("PUT".equalsIgnoreCase(request.getMethod())
                || "PATCH".equalsIgnoreCase(request.getMethod()))) {

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            objectMapper.writeValue(response.getWriter(), Map.of(
                    "status", 403,
                    "erro", "Acesso negado. Somente administradores podem atualizar registros."
            ));
            return;
        }

        request.setAttribute(API_KEY_ATTRIBUTE, apiKey);
        filterChain.doFilter(request, response);
    }
}
