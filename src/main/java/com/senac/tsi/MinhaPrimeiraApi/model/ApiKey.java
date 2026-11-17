
package com.senac.tsi.MinhaPrimeiraApi.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String owner;

    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApiKeyRole role = ApiKeyRole.USER;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ApiKey() { }

    public ApiKey(String owner, String keyHash) {
        this(owner, keyHash, ApiKeyRole.USER);
    }

    public ApiKey(String owner, String keyHash, ApiKeyRole role) {
        this.owner = owner;
        this.keyHash = keyHash;
        this.role = role;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public ApiKeyRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void revoke() {
        this.active = false;
    }
}
