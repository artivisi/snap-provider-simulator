package com.artivisi.snapsimulator.repository;

import com.artivisi.snapsimulator.entity.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {

    Optional<Partner> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Partner> findAllByOrderByCreatedAtDesc();
}
