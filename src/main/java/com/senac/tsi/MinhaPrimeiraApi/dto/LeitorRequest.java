package com.senac.tsi.MinhaPrimeiraApi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Dados para criar ou atualizar um leitor")
public record LeitorRequest(
        @Schema(description = "Nome completo", example = "Ana Souza")
        @NotBlank(message = "Informe o nome do leitor.") @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres.") String nome,
        @Schema(description = "E-mail único", example = "ana.souza@example.com")
        @NotBlank(message = "Informe o e-mail do leitor.") @Email(message = "Informe um e-mail válido.") @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres.") String email,
        @Schema(description = "Telefone (opcional)", example = "11999999999")
        @Size(max = 20)
        @Pattern(regexp = "^\\+?[0-9()\\- ]{8,20}$", message = "telefone deve conter de 8 a 20 dígitos, espaços, parênteses ou hífen")
        String telefone) {
}
