package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class ExternalIdService {

    private final JdbcTemplate jdbc;

    public ExternalIdService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** False when the id was already used by this partner on the same Jakarta day (A9, A33). */
    @Transactional
    @SpecRef("snap.external-id")
    public boolean register(UUID partnerId, String externalId, Instant now) {
        Date day = Date.valueOf(now.atZone(SnapTimestamp.JAKARTA).toLocalDate());
        return jdbc.update("""
                INSERT INTO external_id (partner_id, business_date, external_id, created_at) VALUES (?, ?, ?, ?)
                ON CONFLICT DO NOTHING""", partnerId, day, externalId, Timestamp.from(now)) == 1;
    }
}
