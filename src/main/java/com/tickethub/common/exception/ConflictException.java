package com.tickethub.common.exception;

import org.springframework.http.HttpStatus;

public non-sealed class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String title() {
        return "Conflict";
    }
}
