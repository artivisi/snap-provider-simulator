package com.artivisi.snapsimulator.config;

import com.artivisi.snapsimulator.enums.Bank;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/** Every bank's key, loaded from {keyDir}/{bank}-private.pem; startup fails if one is missing. */
public final class BankKeys {

    private final Map<Bank, BankKey> keys;

    private BankKeys(Map<Bank, BankKey> keys) {
        this.keys = keys;
    }

    public static BankKeys load(Path dir) {
        Map<Bank, BankKey> keys = new EnumMap<>(Bank.class);
        for (Bank bank : Bank.values()) {
            keys.put(bank, BankKey.load(dir.resolve(bank.slug() + "-private.pem")));
        }
        return new BankKeys(keys);
    }

    public BankKey of(Bank bank) {
        return keys.get(bank);
    }
}
