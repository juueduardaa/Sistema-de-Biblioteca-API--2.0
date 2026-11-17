package com.senac.tsi.MinhaPrimeiraApi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;

@Schema(description = "Dados para criar ou atualizar um livro")
public record LivroRequest(
        @Schema(description = "Título do livro", example = "O Hobbit")
        @NotBlank(message = "Informe o título do livro.") @Size(max = 180, message = "O título deve ter no máximo 180 caracteres.") String titulo,
        @Schema(description = "ISBN único (10 a 20 caracteres)", example = "978-0-261-10221-7")
        @NotBlank(message = "Informe o ISBN do livro.") @Size(min = 10, max = 20, message = "O ISBN deve ter entre 10 e 20 caracteres.") String isbn,
        @Schema(description = "Ano de publicação", example = "1937")
        @NotNull(message = "Informe o ano de publicação.") @Min(value = 1400, message = "O ano de publicação deve ser igual ou posterior a 1400.") @Max(value = 2100, message = "O ano de publicação não pode ser posterior a 2100.") Integer anoPublicacao,
        @Schema(description = "ID de uma categoria existente", example = "1")
        @NotNull(message = "Informe o ID da categoria.") @Positive(message = "O ID da categoria deve ser positivo.") Long categoriaId,
        @Schema(description = "IDs de autores existentes (pelo menos um)", example = "[1]")
        @NotEmpty(message = "Informe pelo menos um autor.") List<@NotNull @Positive Long> autoresIds) {
}
