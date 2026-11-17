package com.senac.tsi.MinhaPrimeiraApi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApiKeyCreateRequest(
        @NotBlank(message = "Informe o nome do usuário ou sistema responsável pela chave.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String owner
) { }
