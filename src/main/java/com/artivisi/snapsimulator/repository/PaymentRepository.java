package com.artivisi.snapsimulator.repository;

import com.artivisi.snapsimulator.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findFirstByPartnerIdAndVirtualAccountNoOrderByPaidAtDesc(UUID partnerId, String virtualAccountNo);

    Optional<Payment> findByPartnerIdAndVirtualAccountNoAndPaymentRequestId(UUID partnerId, String virtualAccountNo,
            String paymentRequestId);

    Optional<Payment> findByIdAndPartnerId(UUID id, UUID partnerId);

    List<Payment> findByPartnerIdOrderByPaidAtDesc(UUID partnerId);
}
