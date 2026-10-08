package com.artivisi.snapsimulator.dto;

import java.util.UUID;

public record ConnectionTestResult(boolean reachable, Integer httpStatus, String responseBody, String error,
        UUID exchangeId) {
}
