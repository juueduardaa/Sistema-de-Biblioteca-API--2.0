package com.senac.tsi.MinhaPrimeiraApi.controller;

import com.senac.tsi.MinhaPrimeiraApi.dto.LivroResponse;
import com.senac.tsi.MinhaPrimeiraApi.dto.LivroV1Response;
import com.senac.tsi.MinhaPrimeiraApi.service.LivroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/** Demonstra duas representações do mesmo recurso, escolhidas pelo cabeçalho X-API-Version. */
@RestController
@Validated
@RequestMapping("/api/versao/livros")
@Tag(name = "Versionamento", description = "Exemplo de versionamento por meio do cabeçalho X-API-Version")
public class LivroVersionadoController {

    private final LivroService livroService;

    public LivroVersionadoController(LivroService livroService) {
        this.livroService = livroService;
    }

    @GetMapping(value = "/{id}", headers = "X-API-Version=1")
    @Operation(summary = "Consultar livro — versão 1",
            description = "Retorna uma representação simplificada do livro. Envie X-API-Version: 1.")
    public LivroV1Response buscarV1(
            @Positive(message = "O ID do livro deve ser positivo.") @PathVariable Long id,
            @Parameter(description = "Versão da API", required = true, example = "1")
            @RequestHeader("X-API-Version") String versao) {
        return LivroV1Response.from(livroService.buscarPorId(id));
    }

    @GetMapping(value = "/{id}", headers = "X-API-Version=2")
    @Operation(summary = "Consultar livro — versão 2",
            description = "Retorna a representação completa do livro, incluindo ano de publicação, categoria e autores. Envie X-API-Version: 2.")
    public LivroResponse buscarV2(
            @Positive(message = "O ID do livro deve ser positivo.") @PathVariable Long id,
            @Parameter(description = "Versão da API", required = true, example = "2")
            @RequestHeader("X-API-Version") String versao) {
        return livroService.buscarPorId(id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Versão da API inválida ou ausente",
            description = "Envie X-API-Version com o valor 1 ou 2. Qualquer outro valor retorna HTTP 400.")
    public ResponseEntity<Void> versaoInvalida(
            @Positive(message = "O ID do livro deve ser positivo.") @PathVariable Long id,
            @RequestHeader(value = "X-API-Version", required = false) String versao) {
        return ResponseEntity.badRequest().build();
    }
}
