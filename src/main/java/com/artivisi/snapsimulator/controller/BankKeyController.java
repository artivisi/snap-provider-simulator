package com.artivisi.snapsimulator.controller;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.config.BankKeys;
import com.artivisi.snapsimulator.enums.Bank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public bank keys, for partner apps verifying the bank's token requests. */
@RestController
@SpecRef("sim.bank-key.publish")
public class BankKeyController {

    private final BankKeys bankKeys;

    public BankKeyController(BankKeys bankKeys) {
        this.bankKeys = bankKeys;
    }

    @GetMapping(value = "/keys/bri-public.pem", produces = MediaType.TEXT_PLAIN_VALUE)
    public String bri() {
        return bankKeys.of(Bank.BRI).publicKeyPem();
    }

    @GetMapping(value = "/keys/bca-public.pem", produces = MediaType.TEXT_PLAIN_VALUE)
    public String bca() {
        return bankKeys.of(Bank.BCA).publicKeyPem();
    }
}
