package com.senac.tsi.MinhaPrimeiraApi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@Schema(description = "Dados para criar ou atualizar uma carteirinha")
public record CarteirinhaRequest(
        @Schema(description = "Código único da carteirinha", example = "CAR-0001")
        @NotBlank(message = "Informe o código da carteirinha.") @Size(min = 4, max = 30, message = "O código deve ter entre 4 e 30 caracteres.") String codigo,
        @Schema(description = "Data de emissão (AAAA-MM-DD)", example = "2026-10-01")
        @NotNull @PastOrPresent(message = "A data de emissão não pode estar no futuro.") LocalDate dataEmissao,
        @Schema(description = "Se a carteirinha está ativa", example = "true")
        @NotNull(message = "Informe se a carteirinha está ativa.") Boolean ativa,
        @Schema(description = "ID de um leitor existente que ainda não possui carteirinha", example = "1")
        @NotNull(message = "Informe o ID do leitor.") @Positive(message = "O ID do leitor deve ser positivo.") Long leitorId) {
}
