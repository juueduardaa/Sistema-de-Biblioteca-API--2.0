package com.senac.tsi.MinhaPrimeiraApi.dto;

import java.time.Instant;

public record ApiKeyCreatedResponse(Long id, String owner, String apiKey, boolean active, Instant createdAt) { }
