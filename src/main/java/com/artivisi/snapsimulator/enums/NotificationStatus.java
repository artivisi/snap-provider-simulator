package com.artivisi.snapsimulator.enums;

/** Outcome of the payment call to the partner (A31). */
public enum NotificationStatus {
    /** Not sent yet. */
    PENDING,
    /** 2002500 with paymentFlagStatus 00. */
    ACKNOWLEDGED,
    /** Listed 4xx or flag 01: the partner refused; the bank reverses the credit. */
    REVERSED,
    /** 429, 5xx, timeout, unlisted code or flag 02: outcome unknown, credit kept. */
    SUSPENDED,
    /** Not sent because of a DROP_NOTIFICATION rule. */
    DROPPED,
    /** No partner endpoint registered: nothing to notify. */
    NOT_NOTIFIED
}
