package com.senac.tsi.MinhaPrimeiraApi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** Testes de integração da API, incluindo os filtros de autenticação e idempotência. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.seed.enabled=false")
class BibliotecaApiIntegrationTests {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicInteger idempotencyCounter = new AtomicInteger();
    private String apiKey;
    private String testSuffix;

    @BeforeEach
    void gerarChaveDeAcesso() throws Exception {
        testSuffix = UUID.randomUUID().toString().substring(0, 8);
        HttpResponse<String> response = sendRaw("POST", "/api/chaves-api",
                "{\"owner\":\"Teste de integração\"}", null, "chave-inicial-" + testSuffix);
        assertEquals(201, response.statusCode(), response.body());
        Matcher matcher = Pattern.compile("\\\"apiKey\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(response.body());
        assertTrue(matcher.find(), "A resposta não trouxe a chave de API: " + response.body());
        apiKey = matcher.group(1);
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        String idempotencyKey = method.equalsIgnoreCase("POST")
                ? "teste-" + testSuffix + "-" + idempotencyCounter.incrementAndGet()
                : null;
        return sendRaw(method, path, body, apiKey, idempotencyKey);
    }

    private HttpResponse<String> sendRaw(String method, String path, String body,
                                         String key, String idempotencyKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "*/*");
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (key != null) {
            builder.header("X-API-Key", key);
        }
        if (idempotencyKey != null) {
            builder.header("X-Idempotency-Key", idempotencyKey);
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = builder.method(method, publisher).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private long idDe(HttpResponse<String> response) {
        Matcher m = Pattern.compile("\\\"id\\\"\\s*:\\s*(\\d+)").matcher(response.body());
        assertTrue(m.find(), "Resposta sem id: " + response.body());
        return Long.parseLong(m.group(1));
    }

    @Test
    void swaggerEOpenApiEstaoDisponiveis() throws Exception {
        HttpResponse<String> docs = send("GET", "/v3/api-docs", null);
        assertEquals(200, docs.statusCode());
        for (String rota : new String[] {"/api/leitores", "/api/carteirinhas", "/api/livros",
                "/api/autores", "/api/categorias", "/api/emprestimos", "/api/chaves-api"}) {
            assertTrue(docs.body().contains(rota), "Rota ausente na documentação: " + rota);
        }
        assertEquals(200, send("GET", "/swagger-ui/index.html", null).statusCode());
        assertEquals(200, send("GET", "/css/swagger-custom.css", null).statusCode());
        assertEquals(200, send("GET", "/api", null).statusCode());
    }

    @Test
    void corsPermiteOrigemConfiguradaERecusaOrigemDesconhecida() throws Exception {
        HttpRequest permitida = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/livros"))
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> respostaPermitida = client.send(permitida, HttpResponse.BodyHandlers.ofString());
        assertTrue(respostaPermitida.headers().firstValue("Access-Control-Allow-Origin")
                .orElse("").equals("http://localhost:5173"));

        HttpRequest bloqueada = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/livros"))
                .header("Origin", "https://origem-nao-autorizada.example")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> respostaBloqueada = client.send(bloqueada, HttpResponse.BodyHandlers.ofString());
        assertTrue(respostaBloqueada.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    void endpointsProtegidosExigemChaveValida() throws Exception {
        HttpResponse<String> semChave = sendRaw("GET", "/api/livros", null, null, null);
        assertEquals(401, semChave.statusCode());
        HttpResponse<String> chaveInvalida = sendRaw("GET", "/api/livros", null, "chave-invalida", null);
        assertEquals(401, chaveInvalida.statusCode());
        HttpResponse<String> raizSemChave = sendRaw("GET", "/api", null, null, null);
        assertEquals(401, raizSemChave.statusCode());
        HttpResponse<String> comChave = send("GET", "/api/livros", null);
        assertEquals(200, comChave.statusCode());
    }

    @Test
    void postSemChaveIdempotenteRetorna400() throws Exception {
        HttpResponse<String> response = sendRaw("POST", "/api/categorias",
                "{\"nome\":\"Sem idempotencia " + testSuffix + "\",\"descricao\":\"Teste\"}", apiKey, null);
        assertEquals(400, response.statusCode(), response.body());
    }

    @Test
    void idempotenciaReproduzRespostaSemDuplicarCadastro() throws Exception {
        String nome = "Categoria idempotente " + testSuffix;
        String body = "{\"nome\":\"" + nome + "\",\"descricao\":\"Teste de idempotência\"}";
        String sameKey = "mesma-operacao-" + testSuffix;

        HttpResponse<String> primeira = sendRaw("POST", "/api/categorias", body, apiKey, sameKey);
        HttpResponse<String> repetida = sendRaw("POST", "/api/categorias", body, apiKey, sameKey);

        assertEquals(201, primeira.statusCode(), primeira.body());
        assertEquals(201, repetida.statusCode(), repetida.body());
        assertEquals(primeira.body(), repetida.body());
        assertEquals("true", repetida.headers().firstValue("Idempotency-Replayed").orElse(null));
        assertEquals(1, send("GET", "/api/categorias/buscar?nome=" + nome.replace(" ", "%20"), null)
                .body().split(Pattern.quote(nome), -1).length - 1);
    }

    @Test
    void fluxoCompletoDaBiblioteca() throws Exception {
        HttpResponse<String> categoria = send("POST", "/api/categorias",
                "{\"nome\":\"Teste Ficcao " + testSuffix + "\",\"descricao\":\"Categoria de teste\"}");
        assertEquals(201, categoria.statusCode(), categoria.body());
        assertTrue(categoria.body().contains("_links"), "Resposta sem links HATEOAS");
        long categoriaId = idDe(categoria);

        HttpResponse<String> autor = send("POST", "/api/autores",
                "{\"nome\":\"Autora de Teste " + testSuffix + "\",\"nacionalidade\":\"Brasileira\"}");
        assertEquals(201, autor.statusCode(), autor.body());
        long autorId = idDe(autor);

        HttpResponse<String> leitor = send("POST", "/api/leitores",
                "{\"nome\":\"Leitor Teste " + testSuffix + "\",\"email\":\"leitor." + testSuffix + "@example.com\",\"telefone\":\"11999999999\"}");
        assertEquals(201, leitor.statusCode(), leitor.body());
        long leitorId = idDe(leitor);

        assertEquals(400, send("POST", "/api/leitores", "{\"nome\":\"\",\"email\":\"invalido\"}").statusCode());

        HttpResponse<String> livro = send("POST", "/api/livros",
                "{\"titulo\":\"Livro de Teste " + testSuffix + "\",\"isbn\":\"978-1-23456-" + testSuffix + "\",\"anoPublicacao\":2020,"
                        + "\"categoriaId\":" + categoriaId + ",\"autoresIds\":[" + autorId + "]}");
        assertEquals(201, livro.statusCode(), livro.body());
        long livroId = idDe(livro);

        String emprestimoJson = "{\"dataEmprestimo\":\"2026-10-11\",\"dataPrevistaDevolucao\":\"2026-10-25\","
                + "\"leitorId\":" + leitorId + ",\"livroId\":" + livroId + "}";
        assertEquals(409, send("POST", "/api/emprestimos", emprestimoJson).statusCode());

        HttpResponse<String> carteirinha = send("POST", "/api/carteirinhas",
                "{\"codigo\":\"TST-" + testSuffix + "\",\"dataEmissao\":\"2026-10-01\",\"ativa\":true,\"leitorId\":" + leitorId + "}");
        assertEquals(201, carteirinha.statusCode(), carteirinha.body());
        assertEquals(409, send("POST", "/api/carteirinhas",
                "{\"codigo\":\"DUP-" + testSuffix + "\",\"dataEmissao\":\"2026-10-01\",\"ativa\":true,\"leitorId\":" + leitorId + "}").statusCode());

        HttpResponse<String> emprestimo = send("POST", "/api/emprestimos", emprestimoJson);
        assertEquals(201, emprestimo.statusCode(), emprestimo.body());
        assertTrue(emprestimo.body().contains("\"ATIVO\""));
        long emprestimoId = idDe(emprestimo);
        assertEquals(409, send("POST", "/api/emprestimos", emprestimoJson).statusCode());
        assertTrue(send("GET", "/api/livros/" + livroId, null).body().contains("\"disponivel\":false"));

        assertEquals(200, send("GET", "/api/livros?page=0&size=5&sort=titulo,asc", null).statusCode());
        assertEquals(200, send("GET", "/api/emprestimos/buscar?status=ATIVO", null).statusCode());
        assertEquals(400, send("GET", "/api/emprestimos/buscar?status=INEXISTENTE", null).statusCode());

        assertEquals(200, send("POST", "/api/emprestimos/" + emprestimoId + "/devolucao", null).statusCode());
        assertTrue(send("GET", "/api/livros/" + livroId, null).body().contains("\"disponivel\":true"));
        assertEquals(409, send("POST", "/api/emprestimos/" + emprestimoId + "/devolucao", null).statusCode());

        assertEquals(404, send("GET", "/api/livros/999999", null).statusCode());
        assertEquals(409, send("DELETE", "/api/categorias/" + categoriaId, null).statusCode());
        assertEquals(204, send("DELETE", "/api/emprestimos/" + emprestimoId, null).statusCode());
        assertEquals(204, send("DELETE", "/api/livros/" + livroId, null).statusCode());
        assertEquals(204, send("DELETE", "/api/categorias/" + categoriaId, null).statusCode());
    }


    @Test
    void mesmaChaveIdempotenteComCorpoDiferenteRetornaConflito() throws Exception {
        String key = "idempotencia-conflito-" + testSuffix;
        HttpResponse<String> primeira = sendRaw("POST", "/api/categorias",
                "{\"nome\":\"Categoria original " + testSuffix + "\",\"descricao\":\"Original\"}", apiKey, key);
        HttpResponse<String> segunda = sendRaw("POST", "/api/categorias",
                "{\"nome\":\"Categoria alterada " + testSuffix + "\",\"descricao\":\"Alterada\"}", apiKey, key);
        assertEquals(201, primeira.statusCode(), primeira.body());
        assertEquals(409, segunda.statusCode(), segunda.body());
    }

    @Test
    void chaveRevogadaDeixaDeAutenticar() throws Exception {
        HttpResponse<String> revogada = send("DELETE", "/api/chaves-api/minha-chave", null);
        assertEquals(204, revogada.statusCode());
        HttpResponse<String> acesso = sendRaw("GET", "/api/livros", null, apiKey, null);
        assertEquals(401, acesso.statusCode());
    }

    @Test
    void rateLimitRetorna429EHeadersDeLimite() throws Exception {
        HttpResponse<String> ultimaPermitida = null;
        for (int i = 0; i < 60; i++) {
            ultimaPermitida = send("GET", "/api/livros", null);
            assertEquals(200, ultimaPermitida.statusCode(), ultimaPermitida.body());
        }
        assertEquals("60", ultimaPermitida.headers().firstValue("X-RateLimit-Limit").orElse(null));
        assertEquals("0", ultimaPermitida.headers().firstValue("X-RateLimit-Remaining").orElse(null));

        HttpResponse<String> bloqueada = send("GET", "/api/livros", null);
        assertEquals(429, bloqueada.statusCode(), bloqueada.body());
        assertTrue(bloqueada.headers().firstValue("Retry-After").isPresent());
    }

    @Test
    void idInvalidoRetorna400() throws Exception {
        HttpResponse<String> response = send("GET", "/api/livros/0", null);
        assertEquals(400, response.statusCode(), response.body());
    }

    @Test
    void versionamentoRetornaDuasRepresentacoes() throws Exception {
        HttpResponse<String> categoria = send("POST", "/api/categorias",
                "{\"nome\":\"Categoria versão " + testSuffix + "\",\"descricao\":\"Teste\"}");
        assertEquals(201, categoria.statusCode(), categoria.body());
        long categoriaId = idDe(categoria);

        HttpResponse<String> autor = send("POST", "/api/autores",
                "{\"nome\":\"Autor versão " + testSuffix + "\",\"nacionalidade\":\"Brasileira\"}");
        assertEquals(201, autor.statusCode(), autor.body());
        long autorId = idDe(autor);

        HttpResponse<String> livro = send("POST", "/api/livros",
                "{\"titulo\":\"Livro versão " + testSuffix + "\",\"isbn\":\"ISBN-" + testSuffix + "-123456\","
                        + "\"anoPublicacao\":2020,\"categoriaId\":" + categoriaId + ",\"autoresIds\":[" + autorId + "]}");
        assertEquals(201, livro.statusCode(), livro.body());
        long livroId = idDe(livro);

        HttpResponse<String> v1 = sendRawVersion("1", livroId);
        HttpResponse<String> v2 = sendRawVersion("2", livroId);
        assertEquals(200, v1.statusCode(), v1.body());
        assertEquals(200, v2.statusCode(), v2.body());
        assertTrue(!v1.body().contains("anoPublicacao"), "A versão 1 deveria ser simplificada.");
        assertTrue(v2.body().contains("anoPublicacao"), "A versão 2 deveria trazer os dados completos.");
        assertEquals(400, send("GET", "/api/versao/livros/" + livroId, null).statusCode());
        assertEquals(400, sendRawVersion("3", livroId).statusCode());
    }

    private HttpResponse<String> sendRawVersion(String version, long livroId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/versao/livros/" + livroId))
                .header("X-API-Key", apiKey).header("X-API-Version", version).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
