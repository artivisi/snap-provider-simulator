package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Clears a partner's simulation data; account, credentials, key, endpoint and settings stay. */
@Service
public class PartnerDataService {

    private final JdbcTemplate jdbc;

    public PartnerDataService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    @SpecRef("sim.portal.reset")
    public void reset(UUID partnerId) {
        jdbc.update("DELETE FROM ledger_entry WHERE partner_id = ?", partnerId);
        jdbc.update("DELETE FROM payment WHERE partner_id = ?", partnerId);
        jdbc.update("DELETE FROM virtual_account WHERE partner_id = ?", partnerId);
        jdbc.update("DELETE FROM injection_rule WHERE partner_id = ?", partnerId);
        jdbc.update("DELETE FROM exchange_log WHERE partner_id = ?", partnerId);
        jdbc.update("DELETE FROM external_id WHERE partner_id = ?", partnerId);
        jdbc.update("DELETE FROM access_token WHERE partner_id = ?", partnerId);
        jdbc.update("""
                UPDATE partner SET token_obtained_at = NULL, signed_call_at = NULL, va_created_at = NULL,
                    endpoint_reachable_at = NULL, inquiry_answered_at = NULL, payment_acknowledged_at = NULL,
                    version = version + 1
                WHERE id = ?""", partnerId);
    }
}
