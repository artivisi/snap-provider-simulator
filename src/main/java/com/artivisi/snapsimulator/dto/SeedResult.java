package com.artivisi.snapsimulator.dto;

import com.artivisi.snapsimulator.service.PaymentService;

public record SeedResult(PaymentService.Scenario scenario, String virtualAccountNo, PaymentResult result) {
}
