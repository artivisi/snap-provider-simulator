package com.artivisi.snapsimulator.repository;

import com.artivisi.snapsimulator.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByConnectionIdAndTransactionTimeGreaterThanEqualAndTransactionTimeLessThanOrderByTransactionTime(
            UUID connectionId, Instant from, Instant to);
}
