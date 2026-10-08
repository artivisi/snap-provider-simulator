package com.artivisi.snapsimulator.enums;

import com.artivisi.snapsimulator.SpecRef;

import java.util.Arrays;
import java.util.Optional;

/** CHANNEL-ID values the bank sends on outbound calls (A11, A28). */
@SpecRef("bri.channel-id")
public enum Channel {
    TELLER("00001", "Teller"),
    ATM("00002", "ATM"),
    MOBILE("00003", "Internet / mobile banking"),
    SMS("00004", "SMS banking"),
    CMS("00005", "Cash management"),
    EDC("00006", "EDC"),
    RTGS("00007", "RTGS"),
    OTHER("00008", "Other"),
    API("00009", "API");

    private final String id;
    private final String label;

    Channel(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** channelCode in the body is the integer value of CHANNEL-ID (A28). */
    public int code() {
        return Integer.parseInt(id);
    }

    public static Optional<Channel> of(String id) {
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }
}
