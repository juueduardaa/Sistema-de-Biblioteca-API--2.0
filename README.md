# Sistema de Biblioteca — API REST (Spring Boot)

API RESTful para gerenciar uma biblioteca: leitores, carteirinhas, livros, autores, categorias e empréstimos.
Usa Spring Boot 4.1.1, Java 17+, Spring Data JPA, H2, Bean Validation, Spring HATEOAS e Springdoc OpenAPI (Swagger).

> **Status de validação (leia antes de entregar):** foi feita uma revisão estática do código e foram
> ajustados os testes de integração, mas **a compilação e os testes ainda não foram executados com sucesso**
> neste ambiente porque o Maven Wrapper não conseguiu baixar o Maven Central. Execute os testes no IntelliJ
> antes de considerar a entrega validada. Veja a seção "Como confirmar que está funcionando".

---

## 1. Pré-requisitos

| Item | Versão | Observação |
|---|---|---|
| JDK | 17 ou superior (21 recomendado) | No IntelliJ: *File → Project Structure → SDK → Download JDK* |
| IntelliJ IDEA | Community ou Ultimate | O Maven já vem embutido no IntelliJ |
| Internet | — | Necessária na 1ª abertura para baixar as dependências |

Não é preciso instalar Maven nem banco de dados: o H2 roda em memória dentro da aplicação.

## 2. Passo a passo no IntelliJ IDEA

1. **Extraia** o ZIP. Você terá a pasta `sistema-biblioteca-api` (a que contém o `pom.xml`).
2. No IntelliJ: **File → Open…** e selecione a pasta `sistema-biblioteca-api` (a do `pom.xml`). Se perguntar, escolha **Trust Project**.
3. Aguarde o IntelliJ importar o projeto: no canto inferior direito aparece a barra "Resolving dependencies / Indexing".
   Se aparecer um balão **"Maven projects need to be imported"**, clique em **Load Maven Changes**
   (ou abra a aba **Maven** à direita e clique no ícone de recarregar 🔄).
4. Confira o JDK: **File → Project Structure → Project → SDK** deve estar em 17 ou superior.
5. Abra `src/main/java/com/senac/tsi/MinhaPrimeiraApi/MinhaPrimeiraApiApplication.java`
   e clique no triângulo verde ▶ ao lado de `main` → **Run 'MinhaPrimeiraApiApplication'**.
6. No console deve aparecer algo como `Started MinhaPrimeiraApiApplication in X seconds`
   e `Tomcat started on port 8080`.
7. Abra o navegador em **http://localhost:8080/swagger-ui.html** (a raiz `http://localhost:8080/` redireciona para lá).

Para parar a aplicação: botão quadrado vermelho ■ na janela *Run*.

### Alternativa pelo terminal
```bash
./mvnw spring-boot:run        # Linux/macOS
mvnw.cmd spring-boot:run      # Windows
```
(o `mvnw` baixa o Maven sozinho; precisa de internet)

## 3. Como confirmar que está funcionando

**Opção A — rodar os testes (recomendado):** no IntelliJ, clique com o botão direito na pasta `src/test/java`
→ **Run 'All Tests'**. Os testes de integração sobem a aplicação de verdade e verificam:
- `/v3/api-docs` lista os recursos, `/swagger-ui/index.html` e o CSS personalizado respondem 200;
- autenticação por chave, revogação, idempotência (repetição e conflito), rate limiting (429 e cabeçalhos), CORS, versionamento e o fluxo de cadastro/empréstimo/devolução;
- regras de negócio (409), validação de DTO e ID (400), recurso não encontrado (404), paginação e exclusões.

Verde = compila, sobe, Swagger e API funcionando. (No terminal: `./mvnw test`.)

**Opção B — manualmente:** abra o Swagger UI e siga a seção 5.

## 4. Endereços úteis

| O que | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI (JSON) | http://localhost:8080/v3/api-docs |
| Índice HATEOAS da API | http://localhost:8080/api |
| Console do H2 | http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:biblioteca`, usuário `sa`, senha vazia) |

O banco é **em memória**: os dados somem ao reiniciar. Ao iniciar, a aplicação carrega dados de exemplo
(2 categorias, 2 autores, 2 livros, 2 leitores, 1 carteirinha, 1 empréstimo). Para começar vazio,
use `app.seed.enabled=false` em `application.properties`.

## 5. Testando no Swagger UI

1. Gere a primeira chave em `POST /api/chaves-api`. Informe também `X-Idempotency-Key`.
2. Copie o valor secreto retornado (ele aparece somente uma vez) e clique em **Authorize** no Swagger. Cole a chave no campo `X-API-Key`.
3. Agora teste os endpoints protegidos, como `GET /api/livros` → **Try it out** → **Execute**.
4. Para criar dados na ordem certa (um depende do outro): **Categoria → Autor → Leitor → Livro → Carteirinha → Empréstimo**.
   Cada `POST` exige `X-Idempotency-Key`; ao repetir a mesma operação, reutilize a mesma chave e os mesmos dados.
5. Observe o campo `_links` em cada resposta (HATEOAS): `self`, `update`, `delete`, links para recursos relacionados.
6. Tente provocar erros: e-mail repetido (409), campo vazio (400), ID inexistente (404), chave ausente ou inválida (401) e excesso de requisições (429).
7. Para testar o versionamento, use `GET /api/versao/livros/{id}` e altere `X-API-Version` entre `1` e `2`.

A documentação OpenAPI em JSON fica em `http://localhost:8080/v3/api-docs`. No Swagger UI, cada operação apresenta os parâmetros, cabeçalhos, exemplos e respostas HTTP documentadas. As operações protegidas mostram o esquema **ApiKeyAuth**; a criação da primeira chave é pública. O visual usa um CSS simples em `src/main/resources/static/css/swagger-custom.css`, carregado pela propriedade `springdoc.swagger-ui.customCssUrl`.

## 6. Recursos e endpoints (6 por entidade)

| Recurso | Base | Operações | Busca personalizada (paginada) |
|---|---|---|---|
| Leitores | `/api/leitores` | GET (lista), POST, GET `/{id}`, PUT `/{id}`, DELETE `/{id}` | `GET /buscar?nome=ana` |
| Carteirinhas | `/api/carteirinhas` | idem | `GET /buscar?codigo=CAR` |
| Livros | `/api/livros` | idem | `GET /buscar?titulo=hobbit` |
| Autores | `/api/autores` | idem | `GET /buscar?nome=tolkien` |
| Categorias | `/api/categorias` | idem | `GET /buscar?nome=fantasia` |
| Empréstimos | `/api/emprestimos` | idem + `POST /{id}/devolucao` | `GET /buscar?status=ATIVO` |

Todas as listagens aceitam `?page=0&size=10&sort=campo,asc`.

Códigos HTTP: `200` OK · `201` Created (com cabeçalho `Location`) · `204` No Content (exclusão) ·
`400` dados inválidos · `404` não encontrado · `409` conflito (duplicidade ou regra de negócio).

## 7. Mapa dos requisitos

| Requisito | Onde está |
|---|---|
| Spring Boot recente, Java 17+, Maven, H2, JPA | `pom.xml`, `application.properties` |
| 6 entidades | `model/` (Leitor, Carteirinha, Livro, Autor, Categoria, Emprestimo) |
| One-to-One | Leitor ↔ Carteirinha |
| One-to-Many | Categoria → Livro; Leitor → Empréstimo; Livro → Empréstimo |
| Many-to-Many | Livro ↔ Autor (tabela `livro_autor`) |
| Enum | `StatusEmprestimo` (ATIVO, DEVOLVIDO, ATRASADO) |
| Bean Validation | anotações nas entidades (`model/`) e nos DTOs de entrada (`dto/*Request`) |
| CRUD + paginação + consulta personalizada | `controller/` + `service/` + `repository/` |
| Swagger/OpenAPI | `@Tag`, `@Operation`, `@ApiResponses`, parâmetros de cabeçalho, respostas 401/429 e esquema `X-API-Key`; `config/OpenApiConfig` |
| HATEOAS | `assembler/` (EntityModel), `PagedModel` nas listagens, índice em `/api` |
| Tratamento de erros | `exception/ApiExceptionHandler` (formato JSON único `ApiError`) |

Regras de negócio extras (em `EmprestimoService`): só empresta quem tem carteirinha **ativa**; livro emprestado fica
indisponível; devolver libera o livro; não exclui categoria/autor/leitor/livro que ainda estejam em uso.

## 8. Estrutura de pastas

```
src/main/java/com/senac/tsi/MinhaPrimeiraApi/
├── MinhaPrimeiraApiApplication.java
├── config/       OpenApiConfig, DataLoader (dados de exemplo)
├── model/        entidades JPA + enum
├── repository/   Spring Data JPA (consultas paginadas)
├── dto/          *Request (entrada + validação) e *Response (saída)
├── service/      regras de negócio e transações
├── assembler/    montagem dos recursos HATEOAS
├── controller/   endpoints REST + anotações do Swagger
└── exception/    erros padronizados
```

Por que DTOs? Expor as entidades JPA direto no JSON causa erros de carregamento preguiçoso (lazy) e loops
entre relacionamentos. Os DTOs evitam isso e deixam a documentação do Swagger limpa.

## 9. Problemas comuns

| Sintoma | Causa provável / solução |
|---|---|
| `release version 17 not supported` / `invalid source release` | O IntelliJ está usando um JDK menor que 17. *File → Project Structure → SDK* e também *Settings → Build → Compiler → Java Compiler*. |
| Dependências em vermelho / `Cannot resolve` | Sem internet ou Maven não recarregado: aba **Maven → 🔄 Reload**. |
| `Port 8080 was already in use` | Outra aplicação usa a porta. Pare-a ou adicione `server.port=8081` em `application.properties`. |
| Swagger abre mas aparece "Failed to load API definition" | Abra `/v3/api-docs` no navegador: a mensagem de erro mostrada ali indica o problema. Envie-a para análise. |
| Erro no console ao iniciar | Copie a mensagem **completa**, em especial a linha `Caused by:` mais ao final. |

## Parte 2 — Idempotência em operações POST

Todas as operações `POST` abaixo de `/api/` exigem o cabeçalho `X-Idempotency-Key`.

Exemplo:

```http
POST /api/leitores
Content-Type: application/json
X-Idempotency-Key: cadastro-leitor-001
```

- Na primeira chamada com uma chave, a API executa a operação e guarda a resposta.
- Se a mesma chave for enviada novamente para o mesmo endereço, com o mesmo conteúdo, a API devolve a resposta original sem executar o cadastro novamente. A resposta inclui `Idempotency-Replayed: true`.
- Na primeira resposta, o cabeçalho será `Idempotency-Replayed: false`.
- Se a mesma chave for reutilizada no mesmo endereço com conteúdo diferente, a API responde `409 Conflict`.
- Se o cabeçalho estiver ausente ou vazio, a API responde `400 Bad Request`.

As chaves e respostas são mantidas em memória por até 24 horas. Portanto, esse mecanismo evita duplicidades durante a execução atual da aplicação; ao reiniciar a aplicação, o armazenamento temporário é reiniciado. Cada nova operação legítima deve usar uma chave nova.

O cabeçalho também está documentado nos endpoints POST do Swagger UI.

## Parte 2 — CORS (Cross-Origin Resource Sharing)

A API possui uma política CORS centralizada em `config/CorsConfig.java`. Por padrão, são autorizadas as origens locais mais comuns para desenvolvimento de frontend:

- `http://localhost:3000`
- `http://localhost:5173`

A lista pode ser alterada em `src/main/resources/application.properties`, na propriedade `app.cors.allowed-origins`. Para adicionar mais de uma origem, separe os endereços por vírgula. Informe a origem completa (protocolo, host e porta, quando houver), sem barra no final.

Métodos permitidos: `GET`, `POST`, `PUT`, `PATCH`, `DELETE` e `OPTIONS` (preflight). A configuração aceita os cabeçalhos `Content-Type`, `Authorization`, `X-API-Key`, `X-Idempotency-Key` e `X-API-Version`. Também expõe ao navegador cabeçalhos úteis como `Retry-After`, `X-RateLimit-*`, `Location` e `Idempotency-Replayed`.

O filtro CORS é executado antes dos filtros de autenticação e rate limiting. Assim, o navegador recebe os cabeçalhos CORS também nas respostas de erro, e as requisições preflight `OPTIONS` podem ser respondidas antes da exigência de chave de API.

Para produção, substitua os endereços locais pelos domínios reais autorizados. Não use `*` quando a API precisar restringir o acesso a origens específicas.

## Parte 2 — Autenticação, Rate Limiting e Versionamento

- A primeira chave é criada em `POST /api/chaves-api`; o valor secreto é mostrado apenas na resposta de criação. A API armazena o hash SHA-256 e permite consultar/revogar a chave autenticada em `/api/chaves-api/minha-chave`.
- Os demais endpoints em `/api` e `/api/` exigem `X-API-Key`, inclusive o índice HATEOAS.
- O limite padrão é de 60 requisições por chave a cada 60 segundos; a criação pública de chaves é limitada por IP. A configuração fica em `app.rate-limit.max-requests` e `app.rate-limit.window-seconds`.
- O versionamento é demonstrado em `GET /api/versao/livros/{id}`: envie `X-API-Version: 1` para a representação simplificada ou `X-API-Version: 2` para a representação completa.
- As chaves de API, janelas de rate limiting e respostas de idempotência ficam em memória (exceto o hash das chaves, persistido no H2 em memória). Ao reiniciar a aplicação, esses dados são reiniciados.
