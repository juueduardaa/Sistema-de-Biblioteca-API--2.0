package com.senac.tsi.MinhaPrimeiraApi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bibliotecaOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("ApiKeyAuth",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-Key")))
                .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"))
                .info(new Info()
                .title("Sistema de Biblioteca - API REST")
                .version("2.0.0")
                .description("API para gerenciar leitores, carteirinhas, livros, autores, categorias e empréstimos. Os endpoints protegidos exigem o cabeçalho X-API-Key. Para gerar a primeira chave, utilize POST /api/chaves-api. Todas as listagens são paginadas (page, size, sort) e as respostas utilizam os códigos HTTP adequados.<br><br>RATE LIMITING: cada chave de API possui um limite padrão de 60 requisições por janela de 60 segundos. A criação pública de chaves é limitada pelo IP. As respostas incluem X-RateLimit-Limit, X-RateLimit-Remaining e X-RateLimit-Reset. Ao exceder o limite, a API retorna HTTP 429 e o cabeçalho Retry-After informa quantos segundos aguardar.<br><br>CORS: o acesso cross-origin é permitido somente para as origens configuradas em app.cors.allowed-origins. São aceitos os métodos GET, POST, PUT, PATCH, DELETE e OPTIONS. Os cabeçalhos X-API-Key, X-Idempotency-Key e X-API-Version são aceitos nas requisições cross-origin.<br><br>VERSIONAMENTO: o endpoint GET /api/versao/livros/{id} possui duas versões selecionadas pelo cabeçalho X-API-Version. Envie o valor 1 para receber uma resposta simplificada ou o valor 2 para receber os dados completos do livro. O cabeçalho é obrigatório nesse endpoint.<br><br>"
                        + "TERMOS DE USO: o sistema permite cadastrar novos registros, consultar as informações já existentes, atualizar dados quando necessário e excluir registros que não precisam mais ser mantidos. Dessa forma, facilita a organização, a consulta e o gerenciamento das informações de maneira simples e prática.<br><br>"
                        + "FUNCIONAMENTO: Permite cadastrar, consultar, atualizar e excluir registros <br><br>"
                        + "CONTATO: Caso necessario procure por meio do Email: julia.efmonteiro@senacsp.edu.br")
                .contact(new Contact()
                        .name("Projeto API - TSI")));
    }

    /**
     * Complementa a documentação de todas as operações da API com as respostas
     * produzidas pelos filtros de autenticação e de limite de requisições.
     */
    @Bean
    public OpenApiCustomizer documentacaoRespostasGlobais() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
                String path = pathEntry.getKey();
                if (!path.startsWith("/api/")) {
                    continue;
                }

                PathItem pathItem = pathEntry.getValue();
                for (Map.Entry<HttpMethod, Operation> operationEntry : pathItem.readOperationsMap().entrySet()) {
                    HttpMethod httpMethod = operationEntry.getKey();
                    Operation operation = operationEntry.getValue();
                    if (operation.getResponses() == null) {
                        operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
                    }

                    // A criação da primeira chave é pública; as demais operações exigem X-API-Key.
                    boolean publicOperation = operation.getSecurity() != null
                            && operation.getSecurity().isEmpty();
                    if (!publicOperation && !operation.getResponses().containsKey("401")) {
                        operation.getResponses().addApiResponse("401",
                                new ApiResponse().description("Chave X-API-Key ausente, inválida ou revogada."));
                    }

                    if (!operation.getResponses().containsKey("400")) {
                        operation.getResponses().addApiResponse("400",
                                new ApiResponse().description("Dados, parâmetros ou cabeçalhos inválidos ou ausentes; confira as regras de validação."));
                    }

                    // Documenta o indicador de repetição nas operações POST.
                    if (httpMethod == HttpMethod.POST) {
                        ApiResponse success = operation.getResponses().get("201");
                        if (success == null) {
                            success = operation.getResponses().get("200");
                        }
                        if (success != null) {
                            success.addHeaderObject("Idempotency-Replayed", new Header()
                                    .description("false na primeira execução; true quando a resposta anterior é reproduzida.")
                                    .schema(new StringSchema()));
                        }
                    }
                    if (httpMethod == HttpMethod.POST && !operation.getResponses().containsKey("409")) {
                        operation.getResponses().addApiResponse("409",
                                new ApiResponse().description("Conflito: a chave idempotente já foi utilizada com dados diferentes ou o registro viola uma regra de unicidade."));
                    }
                    if (!operation.getResponses().containsKey("500")) {
                        operation.getResponses().addApiResponse("500",
                                new ApiResponse().description("Erro interno inesperado. Consulte a mensagem retornada pela API."));
                    }

                    // O OpenAPI representa um único GET por caminho; documentamos aqui as duas
                    // representações reais, selecionadas pelo cabeçalho X-API-Version.
                    if (path.startsWith("/api/versao/livros/")) {
                        boolean versionHeaderExists = operation.getParameters() != null
                                && operation.getParameters().stream().anyMatch(parameter -> "X-API-Version".equals(parameter.getName()));
                        if (!versionHeaderExists) {
                            operation.addParametersItem(new Parameter()
                                    .name("X-API-Version")
                                    .in("header")
                                    .required(true)
                                    .description("Versão da representação do livro. Use 1 para dados simplificados ou 2 para dados completos.")
                                    .schema(new StringSchema()._enum(java.util.List.of("1", "2"))));
                        }
                        if (!operation.getResponses().containsKey("400")) {
                            operation.getResponses().addApiResponse("400",
                                    new ApiResponse().description("O cabeçalho X-API-Version é obrigatório."));
                        }
                        if (!operation.getResponses().containsKey("400")) {
                            operation.getResponses().addApiResponse("400",
                                    new ApiResponse().description("Cabeçalho X-API-Version ausente ou inválido. Use 1 ou 2."));
                        }
                        operation.setDescription((operation.getDescription() == null ? "" : operation.getDescription() + " ")
                                + "Versão 1: retorna ID, título, ISBN e disponibilidade. Versão 2: retorna os dados completos, incluindo ano de publicação, categoria e autores.");
                    }

                    // Todos os POST exigem a chave idempotente, inclusive a criação de API Key.
                    if (httpMethod == HttpMethod.POST) {
                        boolean idempotencyHeaderExists = operation.getParameters() != null
                                && operation.getParameters().stream().anyMatch(parameter -> "X-Idempotency-Key".equals(parameter.getName()));
                        if (!idempotencyHeaderExists) {
                            operation.addParametersItem(new Parameter()
                                    .name("X-Idempotency-Key")
                                    .in("header")
                                    .required(true)
                                    .description("Chave única para evitar o processamento duplicado desta operação POST.")
                                    .schema(new StringSchema())
                                    .example("operacao-unica-001"));
                        }
                    }

                    ApiResponse rateLimitResponse = new ApiResponse()
                            .description("Limite de requisições excedido. Aguarde o tempo indicado em Retry-After.")
                            .addHeaderObject("Retry-After", new Header()
                                    .description("Quantidade de segundos que o cliente deve aguardar antes de tentar novamente.")
                                    .schema(new StringSchema()))
                            .addHeaderObject("X-RateLimit-Limit", new Header()
                                    .description("Quantidade máxima de requisições permitidas na janela.")
                                    .schema(new StringSchema()))
                            .addHeaderObject("X-RateLimit-Remaining", new Header()
                                    .description("Quantidade de requisições restantes na janela atual.")
                                    .schema(new StringSchema()))
                            .addHeaderObject("X-RateLimit-Reset", new Header()
                                    .description("Instante Unix em que a janela atual será renovada.")
                                    .schema(new StringSchema()));
                    if (!operation.getResponses().containsKey("429")) {
                        operation.getResponses().addApiResponse("429", rateLimitResponse);
                    }
                }
            }
        };
    }
}
