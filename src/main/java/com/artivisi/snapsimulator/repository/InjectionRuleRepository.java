package com.artivisi.snapsimulator.repository;

import com.artivisi.snapsimulator.entity.InjectionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InjectionRuleRepository extends JpaRepository<InjectionRule, UUID> {

    List<InjectionRule> findByConnectionIdOrderByCreatedAt(UUID connectionId);

    Optional<InjectionRule> findByIdAndConnectionId(UUID id, UUID connectionId);
}
