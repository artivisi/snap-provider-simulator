package com.artivisi.snapsimulator.repository;

import com.artivisi.snapsimulator.entity.BankConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankConnectionRepository extends JpaRepository<BankConnection, UUID> {

    Optional<BankConnection> findByClientId(String clientId);

    Optional<BankConnection> findByIdAndPartnerId(UUID id, UUID partnerId);

    List<BankConnection> findByPartnerIdOrderByCreatedAt(UUID partnerId);

    @Query(value = "SELECT nextval('partner_service_id_seq')", nativeQuery = true)
    long nextPartnerServiceNumber();
}
