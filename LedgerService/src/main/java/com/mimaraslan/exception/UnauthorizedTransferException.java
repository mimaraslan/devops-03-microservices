package com.mimaraslan.exception;

public class UnauthorizedTransferException extends RuntimeException {
    public UnauthorizedTransferException(String message) {
        super(message);
    }
}

