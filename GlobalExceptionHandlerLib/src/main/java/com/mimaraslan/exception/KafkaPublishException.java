package com.mimaraslan.exception;

public class KafkaPublishException extends RuntimeException {
    public KafkaPublishException(String message) {
        super(message);
    }
}
