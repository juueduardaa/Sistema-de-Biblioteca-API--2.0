package com.senac.tsi.MinhaPrimeiraApi.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protege operações POST da API contra duplicidade acidental.
 * As respostas ficam armazenadas em memória durante a execução da aplicação.
 */
@Component
@Order(3)
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Idempotency-Key";
    private static final String REPLAY_HEADER = "Idempotency-Replayed";
    private static final long EXPIRATION_MILLIS = 24 * 60 * 60 * 1000L;
    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, StoredResponse> responses = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "O cabeçalho X-Idempotency-Key é obrigatório em operações POST.");
            return;
        }
        if (key.length() > 200) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "A chave X-Idempotency-Key deve ter no máximo 200 caracteres.");
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, 1024 * 1024);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        byte[] requestBody = wrappedRequest.getInputStream().readAllBytes();
        String fingerprint = fingerprint(request.getMethod() + "\n" + request.getRequestURI()
                + "\n" + request.getQueryString() + "\n" + new String(requestBody, StandardCharsets.UTF_8));
        // Separa o armazenamento por cliente: uma chave de um usuário não pode
        // reproduzir a resposta de outro usuário. A criação pública inicial usa o IP.
        Object authenticated = request.getAttribute(
                com.senac.tsi.MinhaPrimeiraApi.security.ApiKeyAuthenticationFilter.API_KEY_ATTRIBUTE);
        String clientScope;
        if (authenticated instanceof com.senac.tsi.MinhaPrimeiraApi.model.ApiKey apiKey) {
            clientScope = "api-key:" + apiKey.getId();
        } else {
            clientScope = "ip:" + request.getRemoteAddr();
        }
        String storageKey = clientScope + "::" + request.getRequestURI() + "::" + key;

        synchronized (responses) {
            removeExpired();
            StoredResponse previous = responses.get(storageKey);
            if (previous != null) {
                if (!previous.fingerprint().equals(fingerprint)) {
                    writeError(response, HttpServletResponse.SC_CONFLICT,
                            "Esta chave de idempotência já foi utilizada com dados diferentes. Envie outra chave.");
                    return;
                }
                replay(previous, response);
                return;
            }
            if (responses.size() >= MAX_ENTRIES) {
                writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "O armazenamento temporário de chaves idempotentes atingiu o limite. Tente novamente mais tarde.");
                return;
            }

            // Repassa o corpo original ao controller após a leitura usada para calcular a assinatura.
            ReplayableRequest replayableRequest = new ReplayableRequest(request, requestBody);
            filterChain.doFilter(replayableRequest, wrappedResponse);
            wrappedResponse.flushBuffer();

            Map<String, List<String>> headers = new ConcurrentHashMap<>();
            for (String name : wrappedResponse.getHeaderNames()) {
                if (!name.equalsIgnoreCase("Content-Length") && !name.equalsIgnoreCase(REPLAY_HEADER)) {
                    headers.put(name, new ArrayList<>(wrappedResponse.getHeaders(name)));
                }
            }
            responses.put(storageKey, new StoredResponse(fingerprint, wrappedResponse.getStatus(),
                    headers, wrappedResponse.getContentAsByteArray(), Instant.now().toEpochMilli()));
            wrappedResponse.setHeader(REPLAY_HEADER, "false");
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void replay(StoredResponse stored, HttpServletResponse response) throws IOException {
        response.setStatus(stored.status());
        stored.headers().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        response.setHeader(REPLAY_HEADER, "true");
        response.setContentLength(stored.body().length);
        response.getOutputStream().write(stored.body());
    }

    private void removeExpired() {
        long now = Instant.now().toEpochMilli();
        responses.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > EXPIRATION_MILLIS);
    }

    private String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Não foi possível calcular a assinatura da requisição.", ex);
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String json = "{\"status\":" + status + ",\"erro\":\"" + escape(message) + "\"}";
        response.getWriter().write(json);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record StoredResponse(String fingerprint, int status, Map<String, List<String>> headers,
                                  byte[] body, long createdAt) { }

    /** Reutiliza o corpo já lido sem impedir que o controller receba o JSON original. */
    private static class ReplayableRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final byte[] body;
        ReplayableRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            java.io.ByteArrayInputStream source = new java.io.ByteArrayInputStream(body);
            return new jakarta.servlet.ServletInputStream() {
                @Override public int read() { return source.read(); }
                @Override public boolean isFinished() { return source.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(jakarta.servlet.ReadListener listener) { }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
