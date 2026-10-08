package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.ConnectionTestResult;
import com.artivisi.snapsimulator.dto.OutboundExchange;
import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.exception.BusinessException;
import com.artivisi.snapsimulator.exception.OutboundException;
import org.springframework.stereotype.Service;


/** "Test connection": the bank requests a token from the partner endpoint. */
@Service
public class ConnectionTestService {

    private final PartnerClient client;

    public ConnectionTestService(PartnerClient client) {
        this.client = client;
    }

    @SpecRef("sim.portal.endpoint")
    public ConnectionTestResult test(BankConnection connection) {
        try {
            OutboundExchange exchange = client.requestToken(connection);
            boolean reachable = exchange.status() != null && exchange.status() == 200 && exchange.json() != null
                    && exchange.json().get("accessToken") != null;
            return new ConnectionTestResult(reachable, exchange.status(), exchange.body(), exchange.error(),
                    exchange.logId());
        } catch (OutboundException e) {
            throw new BusinessException(null, e.getMessage());
        }
    }
}
