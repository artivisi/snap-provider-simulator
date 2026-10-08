package com.artivisi.snapsimulator.repository;

import com.artivisi.snapsimulator.entity.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    Optional<VirtualAccount> findFirstByPartnerIdAndVirtualAccountNoOrderByCreatedAtDesc(UUID partnerId, String virtualAccountNo);

    boolean existsByPartnerIdAndTrxId(UUID partnerId, String trxId);

    boolean existsByPartnerIdAndVirtualAccountNoAndStatus(UUID partnerId, String virtualAccountNo,
            com.artivisi.snapsimulator.enums.VaStatus status);

    List<VirtualAccount> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId);
}
