package com.mimaraslan.exception;


public class SenderAccountNotFoundException extends RuntimeException {
    public SenderAccountNotFoundException(String message) {
        super(message);
    }
}

