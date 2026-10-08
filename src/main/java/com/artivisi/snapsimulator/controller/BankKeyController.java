package com.artivisi.snapsimulator.controller;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.config.BankKeys;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public bank key, for partner apps verifying bank-to-partner token requests. */
@RestController
public class BankKeyController {

    private final BankKeys bankKeys;

    public BankKeyController(BankKeys bankKeys) {
        this.bankKeys = bankKeys;
    }

    @SpecRef("sim.bank-key.publish")
    @GetMapping(value = "/keys/bank-public.pem", produces = MediaType.TEXT_PLAIN_VALUE)
    public String bankPublicKey() {
        return bankKeys.publicKeyPem();
    }
}
