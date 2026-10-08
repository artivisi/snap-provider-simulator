package com.artivisi.snapsimulator.bank;

/**
 * A bank channel the customer pays at: the value chosen in the portal (code),
 * the CHANNEL-ID header and the channelCode body field the bank sends.
 */
public record ChannelOption(String code, String label, String channelIdHeader, int channelCode) {
}
