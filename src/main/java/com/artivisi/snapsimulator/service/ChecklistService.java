package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.enums.ChecklistItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
@SpecRef("sim.portal.checklist")
public class ChecklistService {

    private final JdbcTemplate jdbc;

    public ChecklistService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Stamps the item the first time only; later calls keep the original time. */
    @Transactional
    public void stamp(UUID partnerId, ChecklistItem item, Instant at) {
        String sql = switch (item) {
            case KEY_REGISTERED -> "UPDATE partner SET key_registered_at = ? WHERE id = ? AND key_registered_at IS NULL";
            case TOKEN_OBTAINED -> "UPDATE partner SET token_obtained_at = ? WHERE id = ? AND token_obtained_at IS NULL";
            case SIGNED_CALL -> "UPDATE partner SET signed_call_at = ? WHERE id = ? AND signed_call_at IS NULL";
            case VA_CREATED -> "UPDATE partner SET va_created_at = ? WHERE id = ? AND va_created_at IS NULL";
            case ENDPOINT_REACHABLE ->
                    "UPDATE partner SET endpoint_reachable_at = ? WHERE id = ? AND endpoint_reachable_at IS NULL";
            case INQUIRY_ANSWERED ->
                    "UPDATE partner SET inquiry_answered_at = ? WHERE id = ? AND inquiry_answered_at IS NULL";
            case PAYMENT_ACKNOWLEDGED ->
                    "UPDATE partner SET payment_acknowledged_at = ? WHERE id = ? AND payment_acknowledged_at IS NULL";
        };
        jdbc.update(sql, Timestamp.from(at), partnerId);
    }
}
