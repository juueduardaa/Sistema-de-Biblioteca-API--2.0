package com.senac.tsi.MinhaPrimeiraApi.controller;

import com.senac.tsi.MinhaPrimeiraApi.dto.ApiKeyCreateRequest;
import com.senac.tsi.MinhaPrimeiraApi.dto.ApiKeyCreatedResponse;
import com.senac.tsi.MinhaPrimeiraApi.dto.ApiKeyResponse;
import com.senac.tsi.MinhaPrimeiraApi.model.ApiKey;
import com.senac.tsi.MinhaPrimeiraApi.security.ApiKeyAuthenticationFilter;
import com.senac.tsi.MinhaPrimeiraApi.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chaves-api")
@Tag(name = "Chaves de API", description = "Geração e gerenciamento da chave de acesso à API")
public class ApiKeyController {
    private final ApiKeyService service;

    public ApiKeyController(ApiKeyService service) { this.service = service; }

    @PostMapping
    @SecurityRequirements
    @Operation(summary = "Gerar uma chave de API", description = "Cria uma chave. Este endpoint é público para permitir a geração da primeira chave. Guarde o valor retornado: ele será exibido somente nesta resposta.")
    @Parameter(name = "X-Idempotency-Key", description = "Chave única para evitar que a criação da chave seja processada duas vezes. Reutilize a mesma chave somente ao repetir a mesma operação.", required = true, in = ParameterIn.HEADER, example = "gerar-chave-001")
    public ResponseEntity<ApiKeyCreatedResponse> create(@Valid @RequestBody ApiKeyCreateRequest request) {
        ApiKeyService.CreatedApiKey created = service.create(request.owner());
        ApiKey key = created.apiKey();
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiKeyCreatedResponse(
                key.getId(), key.getOwner(), created.rawKey(), key.isActive(), key.getCreatedAt()));
    }

    @GetMapping("/minha-chave")
    @Operation(summary = "Consultar a chave utilizada", description = "Exibe os dados da chave autenticada, sem revelar seu valor secreto.")
    public ApiKeyResponse mine(HttpServletRequest request) {
        ApiKey key = (ApiKey) request.getAttribute(ApiKeyAuthenticationFilter.API_KEY_ATTRIBUTE);
        return toResponse(key);
    }

    @DeleteMapping("/minha-chave")
    @Operation(summary = "Revogar a chave utilizada", description = "Desativa a chave enviada no cabeçalho X-API-Key. Depois da revogação, ela não poderá mais acessar a API.")
    public ResponseEntity<Void> revoke(HttpServletRequest request) {
        ApiKey key = (ApiKey) request.getAttribute(ApiKeyAuthenticationFilter.API_KEY_ATTRIBUTE);
        service.revoke(key);
        return ResponseEntity.noContent().build();
    }

    private ApiKeyResponse toResponse(ApiKey key) {
        return new ApiKeyResponse(key.getId(), key.getOwner(), key.isActive(), key.getCreatedAt());
    }
}
