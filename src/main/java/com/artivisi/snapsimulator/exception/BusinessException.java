package com.artivisi.snapsimulator.exception;

/** A rule violation the user can fix; field is the form field it belongs to, or null. */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public BusinessException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
