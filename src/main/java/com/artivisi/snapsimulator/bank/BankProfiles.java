package com.artivisi.snapsimulator.bank;

import com.artivisi.snapsimulator.enums.Bank;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public final class BankProfiles {

    private final Map<Bank, BankProfile> profiles = new EnumMap<>(Bank.class);

    public BankProfiles(List<BankProfile> all) {
        all.forEach(p -> profiles.put(p.bank(), p));
        for (Bank bank : Bank.values()) {
            if (!profiles.containsKey(bank)) {
                throw new IllegalStateException("No BankProfile for " + bank);
            }
        }
    }

    public BankProfile of(Bank bank) {
        return profiles.get(bank);
    }
}
