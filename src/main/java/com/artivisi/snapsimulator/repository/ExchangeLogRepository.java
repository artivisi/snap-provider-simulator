package com.artivisi.snapsimulator.repository;

import com.artivisi.snapsimulator.entity.ExchangeLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExchangeLogRepository extends JpaRepository<ExchangeLog, UUID> {

    List<ExchangeLog> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId, Pageable pageable);
}
