package com.artivisi.snapsimulator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ApplicationIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("Context starts and Flyway creates the schema")
    void schemaMigrated() {
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class)).contains("partner", "bank_connection", "access_token", "external_id", "virtual_account", "payment",
                "ledger_entry", "exchange_log", "injection_rule");
    }
}
