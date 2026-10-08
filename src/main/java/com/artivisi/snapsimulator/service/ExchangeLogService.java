package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.entity.ExchangeLog;
import com.artivisi.snapsimulator.repository.ExchangeLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@SpecRef("sim.exchange-log")
public class ExchangeLogService {

    private final ExchangeLogRepository logs;

    public ExchangeLogService(ExchangeLogRepository logs) {
        this.logs = logs;
    }

    /** Own transaction: the log survives a rolled-back business transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ExchangeLog entry) {
        logs.save(entry);
    }

    public List<ExchangeLog> recent(UUID partnerId, int limit) {
        return logs.findByPartnerIdOrderByCreatedAtDesc(partnerId, PageRequest.of(0, limit));
    }

    public ExchangeLog get(UUID partnerId, UUID id) {
        return logs.findById(id).filter(l -> partnerId.equals(l.getPartnerId()))
                .orElseThrow(() -> new com.artivisi.snapsimulator.exception.NotFoundException("exchange " + id + " not found"));
    }
}
