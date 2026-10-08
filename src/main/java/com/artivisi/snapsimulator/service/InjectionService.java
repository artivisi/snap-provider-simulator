package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.InjectionRuleRequest;
import com.artivisi.snapsimulator.entity.InjectionRule;
import com.artivisi.snapsimulator.enums.InjectionType;
import com.artivisi.snapsimulator.enums.OutboundTarget;
import com.artivisi.snapsimulator.exception.BusinessException;
import com.artivisi.snapsimulator.exception.NotFoundException;
import com.artivisi.snapsimulator.repository.InjectionRuleRepository;
import com.artivisi.snapsimulator.snap.SnapService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@SpecRef("sim.error-injection")
public class InjectionService {

    /** A rule taken for one call. */
    public record Taken(InjectionType type, Long delayMs, Integer httpStatus) {
    }

    private final InjectionRuleRepository rules;
    private final PartnerService partners;
    private final JdbcTemplate jdbc;

    public InjectionService(InjectionRuleRepository rules, PartnerService partners, JdbcTemplate jdbc) {
        this.rules = rules;
        this.partners = partners;
        this.jdbc = jdbc;
    }

    public List<InjectionRule> list(UUID partnerId) {
        return rules.findByPartnerIdOrderByCreatedAt(partnerId);
    }

    @Transactional
    public InjectionRule add(UUID partnerId, InjectionRuleRequest request) {
        InjectionType type = request.type();
        validTargets(type).stream().filter(t -> t.equals(request.target())).findFirst()
                .orElseThrow(() -> new BusinessException("target", type + " applies to " + String.join(", ", validTargets(type))));
        if (type.needsDelay() != (request.delayMs() != null)) {
            throw new BusinessException("delayMs", type.needsDelay() ? type + " needs delayMs" : type + " takes no delayMs");
        }
        if (type.needsStatus() != (request.httpStatus() != null)) {
            throw new BusinessException("httpStatus", type.needsStatus() ? type + " needs httpStatus" : type + " takes no httpStatus");
        }
        if (type.needsStatus() && !InjectionType.HTTP_ERROR_STATUSES.contains(request.httpStatus())) {
            throw new BusinessException("httpStatus", "httpStatus must be 500, 502 or 503");
        }
        InjectionRule rule = new InjectionRule();
        rule.setPartner(partners.get(partnerId));
        rule.setType(type);
        rule.setTarget(request.target());
        rule.setDelayMs(request.delayMs());
        rule.setHttpStatus(request.httpStatus());
        rule.setRemaining(request.remaining());
        return rules.save(rule);
    }

    @Transactional
    public void delete(UUID partnerId, UUID ruleId) {
        rules.delete(rules.findByIdAndPartnerId(ruleId, partnerId)
                .orElseThrow(() -> new NotFoundException("injection rule " + ruleId + " not found")));
    }

    public static List<String> validTargets(InjectionType type) {
        return switch (type.kind()) {
            case INBOUND -> Arrays.stream(SnapService.values()).map(Enum::name).toList();
            case OUTBOUND -> Arrays.stream(OutboundTarget.values()).map(Enum::name).toList();
            case PAYMENT -> List.of(OutboundTarget.PAYMENT.name());
        };
    }

    /** Consumes one use of the oldest rule for the target; the rule is deleted when used up. */
    @Transactional
    public Optional<Taken> take(UUID partnerId, String target) {
        List<Taken> taken = jdbc.query("""
                UPDATE injection_rule SET remaining = remaining - 1, version = version + 1
                WHERE id = (SELECT id FROM injection_rule WHERE partner_id = ? AND target = ?
                            ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED)
                RETURNING rule_type, delay_ms, http_status""",
                (rs, i) -> new Taken(InjectionType.valueOf(rs.getString("rule_type")),
                        rs.getObject("delay_ms", Long.class), rs.getObject("http_status", Integer.class)),
                partnerId, target);
        jdbc.update("DELETE FROM injection_rule WHERE partner_id = ? AND remaining <= 0", partnerId);
        return taken.stream().findFirst();
    }
}
