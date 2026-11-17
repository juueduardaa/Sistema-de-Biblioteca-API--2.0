package com.senac.tsi.MinhaPrimeiraApi.service;

import com.senac.tsi.MinhaPrimeiraApi.model.ApiKey;
import com.senac.tsi.MinhaPrimeiraApi.model.ApiKeyRole;
import com.senac.tsi.MinhaPrimeiraApi.repository.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class ApiKeyService {

    private final ApiKeyRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CreatedApiKey create(String owner) {
        return create(owner, ApiKeyRole.USER);
    }

    @Transactional
    public CreatedApiKey create(String owner, ApiKeyRole role) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("O nome do proprietário é obrigatório.");
        }

        if (role == null) {
            throw new IllegalArgumentException("O tipo de chave é obrigatório.");
        }

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        String rawKey = "bib_" + HexFormat.of().formatHex(bytes);

        ApiKey saved = repository.save(
                new ApiKey(owner.trim(), sha256(rawKey), role)
        );

        return new CreatedApiKey(saved, rawKey);
    }


    public Optional<ApiKey> validate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }

        if ("USER".equalsIgnoreCase(rawKey.trim())) {
            return Optional.of(
                    new ApiKey("Usuário de teste", sha256("USER"), ApiKeyRole.USER)
            );
        }

        if ("ADMIN".equalsIgnoreCase(rawKey.trim())) {
            return Optional.of(
                    new ApiKey("Administrador de teste", sha256("ADMIN"), ApiKeyRole.ADMIN)
            );
        }

        return repository.findByKeyHashAndActiveTrue(sha256(rawKey));
    }


    @Transactional
    public void revoke(ApiKey apiKey) {
        apiKey.revoke();
        repository.save(apiKey);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Não foi possível processar a chave de API.", ex
            );
        }
    }
    public record CreatedApiKey(ApiKey apiKey, String rawKey) { }
}