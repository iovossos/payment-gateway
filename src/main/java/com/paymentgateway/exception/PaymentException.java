package com.paymentgateway.exception;

import lombok.Getter;

@Getter
public class PaymentException extends RuntimeException {

    private final String errorCode;
    private final Object[] args;

    public PaymentException(String message) {
        super(message);
        this.errorCode = null;
        this.args = null;
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.args = null;
    }

    public PaymentException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
    }

    public PaymentException(String message, String errorCode, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
    }

    public PaymentException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = null;
    }

    public PaymentException(String message, Throwable cause, String errorCode, Object... args) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = args;
    }
}