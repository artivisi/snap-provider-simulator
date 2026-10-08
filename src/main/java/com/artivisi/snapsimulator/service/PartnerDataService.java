package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Clears one connection's simulation data; credentials, key, endpoint and settings stay. */
@Service
public class PartnerDataService {

    private final JdbcTemplate jdbc;

    public PartnerDataService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    @SpecRef("sim.portal.reset")
    public void reset(UUID connectionId) {
        jdbc.update("DELETE FROM ledger_entry WHERE connection_id = ?", connectionId);
        jdbc.update("DELETE FROM payment WHERE connection_id = ?", connectionId);
        jdbc.update("DELETE FROM virtual_account WHERE connection_id = ?", connectionId);
        jdbc.update("DELETE FROM injection_rule WHERE connection_id = ?", connectionId);
        jdbc.update("DELETE FROM exchange_log WHERE connection_id = ?", connectionId);
        jdbc.update("DELETE FROM external_id WHERE connection_id = ?", connectionId);
        jdbc.update("DELETE FROM access_token WHERE connection_id = ?", connectionId);
        jdbc.update("""
                UPDATE bank_connection SET token_obtained_at = NULL, signed_call_at = NULL, va_created_at = NULL,
                    endpoint_reachable_at = NULL, inquiry_answered_at = NULL, payment_acknowledged_at = NULL,
                    version = version + 1
                WHERE id = ?""", connectionId);
    }
}
