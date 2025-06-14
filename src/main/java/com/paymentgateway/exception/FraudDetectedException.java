package com.paymentgateway.exception;

public class FraudDetectedException extends RuntimeException {

    private final String fraudDetails;
    private final Double fraudScore;

    public FraudDetectedException(String message) {
        super(message);
        this.fraudDetails = null;
        this.fraudScore = null;
    }

    public FraudDetectedException(String message, String fraudDetails) {
        super(message);
        this.fraudDetails = fraudDetails;
        this.fraudScore = null;
    }

    public FraudDetectedException(String message, Double fraudScore) {
        super(message);
        this.fraudDetails = null;
        this.fraudScore = fraudScore;
    }

    public FraudDetectedException(String message, String fraudDetails, Double fraudScore) {
        super(message);
        this.fraudDetails = fraudDetails;
        this.fraudScore = fraudScore;
    }

    public FraudDetectedException(String message, Throwable cause) {
        super(message, cause);
        this.fraudDetails = null;
        this.fraudScore = null;
    }

    public String getFraudDetails() {
        return fraudDetails;
    }

    public Double getFraudScore() {
        return fraudScore;
    }
}