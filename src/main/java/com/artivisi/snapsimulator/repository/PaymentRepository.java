package com.artivisi.snapsimulator.repository;

import com.artivisi.snapsimulator.entity.Payment;
import com.artivisi.snapsimulator.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findFirstByPartnerIdAndVirtualAccountNoOrderByPaidAtDesc(UUID partnerId, String virtualAccountNo);

    Optional<Payment> findByPartnerIdAndVirtualAccountNoAndPaymentRequestId(UUID partnerId, String virtualAccountNo,
            String paymentRequestId);

    Optional<Payment> findByIdAndPartnerId(UUID id, UUID partnerId);

    List<Payment> findByPartnerIdOrderByPaidAtDesc(UUID partnerId);

    /** Records a notification attempt without re-saving the (detached) entity. */
    @Transactional
    @Modifying
    @Query("""
            UPDATE Payment p SET p.notificationStatus = :status, p.externalId = :externalId, p.version = p.version + 1
            WHERE p.id = :id""")
    int updateNotification(UUID id, NotificationStatus status, String externalId);
}
