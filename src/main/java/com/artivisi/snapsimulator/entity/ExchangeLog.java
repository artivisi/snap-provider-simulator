package com.artivisi.snapsimulator.entity;

import com.artivisi.snapsimulator.enums.Direction;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One inbound or outbound HTTP exchange, as sent and received. */
@Getter
@Setter
@Entity
@Table(name = "exchange_log")
public class ExchangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private Instant createdAt;
    /** Null for inbound calls that could not be attributed to a partner. */
    private UUID connectionId;
    @Enumerated(EnumType.STRING)
    private Direction direction;
    private String method;
    private String url;
    private String requestHeaders;
    private String requestBody;
    private String stringToSign;
    private Integer responseStatus;
    private String responseHeaders;
    private String responseBody;
    private String error;
    private long durationMs;
}
