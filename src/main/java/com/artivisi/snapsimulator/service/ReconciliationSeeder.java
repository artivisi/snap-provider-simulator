package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.bank.BankProfiles;
import com.artivisi.snapsimulator.bank.ChannelOption;
import com.artivisi.snapsimulator.dto.SeedRequest;
import com.artivisi.snapsimulator.dto.SeedResult;
import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Today's payments with the anomalies a reconciliation must find: a credit
 * without notification, a notification whose paidAmount differs from the
 * credit, and one credit notified twice under different X-EXTERNAL-IDs.
 */
@Service
@SpecRef("sim.reconciliation-seeder")
public class ReconciliationSeeder {

    private static final List<PaymentService.Scenario> ORDER = List.of(PaymentService.Scenario.NORMAL,
            PaymentService.Scenario.NORMAL, PaymentService.Scenario.DROPPED, PaymentService.Scenario.AMOUNT_MISMATCH,
            PaymentService.Scenario.DUPLICATE);

    private final PaymentService payments;
    private final BankProfiles profiles;

    public ReconciliationSeeder(PaymentService payments, BankProfiles profiles) {
        this.payments = payments;
        this.profiles = profiles;
    }

    public List<SeedResult> seed(BankConnection connection, SeedRequest request) {
        if (!connection.hasEndpoint()) {
            throw new BusinessException(null, "Register the partner endpoint first: the seeder sends inquiries and payments to it");
        }
        ChannelOption channel = profiles.of(connection.getBank()).channel(request.channelId());
        List<SeedResult> results = new ArrayList<>();
        for (int i = 0; i < ORDER.size(); i++) {
            String va = request.virtualAccountNos().get(i);
            results.add(new SeedResult(ORDER.get(i), va, payments.billerFlow(connection, va, null, channel, ORDER.get(i))));
        }
        return results;
    }
}
