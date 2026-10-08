package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.ConnectionTestResult;
import com.artivisi.snapsimulator.exception.BusinessException;
import com.artivisi.snapsimulator.exception.OutboundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** "Test connection": the bank requests a token from the partner endpoint. */
@Service
public class ConnectionTestService {

    private final PartnerService partners;
    private final PartnerClient client;

    public ConnectionTestService(PartnerService partners, PartnerClient client) {
        this.partners = partners;
        this.client = client;
    }

    @SpecRef("sim.portal.endpoint")
    public ConnectionTestResult test(UUID partnerId) {
        try {
            PartnerClient.Exchange exchange = client.requestToken(partners.get(partnerId));
            boolean reachable = exchange.status() != null && exchange.status() == 200 && exchange.json() != null
                    && exchange.json().get("accessToken") != null;
            return new ConnectionTestResult(reachable, exchange.status(), exchange.body(), exchange.error(),
                    exchange.logId());
        } catch (OutboundException e) {
            throw new BusinessException(null, e.getMessage());
        }
    }
}
