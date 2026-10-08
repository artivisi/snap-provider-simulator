package com.artivisi.snapsimulator.enums;

import java.util.Locale;

/** Banks the simulator imitates, each from its own public documentation. */
public enum Bank {
    BRI("PT Bank Rakyat Indonesia"),
    BCA("PT Bank Central Asia");

    private final String legalName;

    Bank(String legalName) {
        this.legalName = legalName;
    }

    public String legalName() {
        return legalName;
    }

    /** File and URL name: bri, bca. */
    public String slug() {
        return name().toLowerCase(Locale.ROOT);
    }
}
