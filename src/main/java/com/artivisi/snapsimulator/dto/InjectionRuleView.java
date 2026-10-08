package com.artivisi.snapsimulator.dto;

import com.artivisi.snapsimulator.entity.InjectionRule;
import com.artivisi.snapsimulator.enums.InjectionType;

import java.time.Instant;
import java.util.UUID;

public record InjectionRuleView(UUID id, InjectionType type, String target, Long delayMs, Integer httpStatus,
        int remaining, Instant createdAt) {

    public static InjectionRuleView of(InjectionRule r) {
        return new InjectionRuleView(r.getId(), r.getType(), r.getTarget(), r.getDelayMs(), r.getHttpStatus(),
                r.getRemaining(), r.getCreatedAt());
    }
}
