package com.senac.tsi.MinhaPrimeiraApi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@Schema(description = "Dados para registrar um novo empréstimo")
public record EmprestimoRequest(
        @Schema(description = "Data do empréstimo (AAAA-MM-DD)", example = "2026-10-11")
        @NotNull @PastOrPresent(message = "A data do empréstimo não pode estar no futuro.") LocalDate dataEmprestimo,
        @Schema(description = "Data prevista para devolução (não pode ser anterior ao empréstimo)", example = "2026-10-25")
        @NotNull(message = "Informe a data prevista de devolução.") LocalDate dataPrevistaDevolucao,
        @Schema(description = "ID de um leitor existente com carteirinha ativa", example = "1")
        @NotNull(message = "Informe o ID do leitor.") @Positive(message = "O ID do leitor deve ser positivo.") Long leitorId,
        @Schema(description = "ID de um livro existente e disponível", example = "1")
        @NotNull(message = "Informe o ID do livro.") @Positive(message = "O ID do livro deve ser positivo.") Long livroId) {
}
