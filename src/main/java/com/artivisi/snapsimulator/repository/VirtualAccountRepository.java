package com.artivisi.snapsimulator.repository;

import com.artivisi.snapsimulator.entity.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    Optional<VirtualAccount> findFirstByConnectionIdAndVirtualAccountNoOrderByCreatedAtDesc(UUID connectionId, String virtualAccountNo);

    boolean existsByConnectionIdAndTrxId(UUID connectionId, String trxId);

    boolean existsByConnectionIdAndVirtualAccountNoAndStatus(UUID connectionId, String virtualAccountNo,
            com.artivisi.snapsimulator.enums.VaStatus status);

    List<VirtualAccount> findByConnectionIdOrderByCreatedAtDesc(UUID connectionId);
}
