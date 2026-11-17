package com.senac.tsi.MinhaPrimeiraApi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Representação simplificada de livro disponibilizada na versão 1 da API. */
@Schema(description = "Representação do livro na versão 1 da API")
public record LivroV1Response(
        @Schema(example = "1") Long id,
        @Schema(example = "O Hobbit") String titulo,
        @Schema(example = "978-0-261-10221-7") String isbn,
        @Schema(example = "true") Boolean disponivel) {

    public static LivroV1Response from(LivroResponse livro) {
        return new LivroV1Response(livro.id(), livro.titulo(), livro.isbn(), livro.disponivel());
    }
}
