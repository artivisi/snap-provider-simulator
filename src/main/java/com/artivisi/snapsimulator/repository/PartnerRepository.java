package com.artivisi.snapsimulator.repository;

import com.artivisi.snapsimulator.entity.Partner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {

    Optional<Partner> findByEmail(String email);

    Optional<Partner> findByClientId(String clientId);

    boolean existsByEmail(String email);

    List<Partner> findAllByOrderByCreatedAtDesc();

    @Query(value = "SELECT nextval('partner_service_id_seq')", nativeQuery = true)
    long nextPartnerServiceNumber();
}
