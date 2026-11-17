package com.senac.tsi.MinhaPrimeiraApi.dto;

import java.time.Instant;

public record ApiKeyResponse(Long id, String owner, boolean active, Instant createdAt) { }
