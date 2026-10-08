package com.artivisi.snapsimulator.repository;

import com.artivisi.snapsimulator.entity.AccessToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccessTokenRepository extends JpaRepository<AccessToken, UUID> {

    @EntityGraph(attributePaths = "partner")
    Optional<AccessToken> findByToken(String token);
}
