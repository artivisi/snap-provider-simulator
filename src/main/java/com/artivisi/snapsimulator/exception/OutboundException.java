package com.artivisi.snapsimulator.exception;

/** A bank-to-partner call did not produce a usable answer; the exchange log holds the details. */
public class OutboundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OutboundException(String message) {
        super(message);
    }
}
