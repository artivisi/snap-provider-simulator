package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.entity.LedgerEntry;
import com.artivisi.snapsimulator.repository.LedgerEntryRepository;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The bank's daily statement: the ledger for one Jakarta day (A33), oldest first. */
@Service
@Transactional(readOnly = true)
@SpecRef("sim.statement-csv")
public class StatementService {

    public static final String HEADER = "transaction_date,transaction_id,type,amount,currency,virtual_account_no,remark";

    private final LedgerEntryRepository ledger;

    public StatementService(LedgerEntryRepository ledger) {
        this.ledger = ledger;
    }

    public String csv(UUID connectionId, LocalDate day) {
        Instant from = day.atStartOfDay(SnapTimestamp.JAKARTA).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(SnapTimestamp.JAKARTA).toInstant();
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (LedgerEntry e : ledger
                .findByConnectionIdAndTransactionTimeGreaterThanEqualAndTransactionTimeLessThanOrderByTransactionTime(
                        connectionId, from, to)) {
            csv.append(SnapTimestamp.formatSeconds(e.getTransactionTime())).append(',')
                    .append(e.getJournalId()).append(',')
                    .append(e.getEntryType()).append(',')
                    .append(e.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString()).append(',')
                    .append(e.getCurrency()).append(',')
                    .append(quote(e.getVirtualAccountNo())).append(',')
                    .append(quote(e.getRemark())).append('\n');
        }
        return csv.toString();
    }

    /** RFC 4180 quoting; VA numbers are quoted too because their prefix starts with spaces. */
    static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
