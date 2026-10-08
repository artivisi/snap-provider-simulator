package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.InjectionRuleRequest;
import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.entity.InjectionRule;
import com.artivisi.snapsimulator.enums.Bank;
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
    private final JdbcTemplate jdbc;

    public InjectionService(InjectionRuleRepository rules, JdbcTemplate jdbc) {
        this.rules = rules;
        this.jdbc = jdbc;
    }

    public List<InjectionRule> list(UUID connectionId) {
        return rules.findByConnectionIdOrderByCreatedAt(connectionId);
    }

    @Transactional
    public InjectionRule add(BankConnection connection, InjectionRuleRequest request) {
        InjectionType type = request.type();
        List<String> targets = validTargets(connection.getBank(), type);
        targets.stream().filter(t -> t.equals(request.target())).findFirst()
                .orElseThrow(() -> new BusinessException("target", type + " applies to " + String.join(", ", targets)));
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
        rule.setConnection(connection);
        rule.setType(type);
        rule.setTarget(request.target());
        rule.setDelayMs(request.delayMs());
        rule.setHttpStatus(request.httpStatus());
        rule.setRemaining(request.remaining());
        return rules.save(rule);
    }

    @Transactional
    public void delete(UUID connectionId, UUID ruleId) {
        rules.delete(rules.findByIdAndConnectionId(ruleId, connectionId)
                .orElseThrow(() -> new NotFoundException("injection rule " + ruleId + " not found")));
    }

    /** Inbound rules target the bank's own services. */
    public static List<String> validTargets(Bank bank, InjectionType type) {
        return switch (type.kind()) {
            case INBOUND -> SnapService.of(bank).stream().map(Enum::name).toList();
            case OUTBOUND -> Arrays.stream(OutboundTarget.values()).map(Enum::name).toList();
            case PAYMENT -> List.of(OutboundTarget.PAYMENT.name());
        };
    }

    /** Consumes one use of the oldest rule for the target; the rule is deleted when used up. */
    @Transactional
    public Optional<Taken> take(UUID connectionId, String target) {
        List<Taken> taken = jdbc.query("""
                UPDATE injection_rule SET remaining = remaining - 1, version = version + 1
                WHERE id = (SELECT id FROM injection_rule WHERE connection_id = ? AND target = ?
                            ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED)
                RETURNING rule_type, delay_ms, http_status""",
                (rs, i) -> new Taken(InjectionType.valueOf(rs.getString("rule_type")),
                        rs.getObject("delay_ms", Long.class), rs.getObject("http_status", Integer.class)),
                connectionId, target);
        jdbc.update("DELETE FROM injection_rule WHERE connection_id = ? AND remaining <= 0", connectionId);
        return taken.stream().findFirst();
    }
}
