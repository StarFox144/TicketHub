package com.tickethub.common.exception;

import org.springframework.http.HttpStatus;

/** Невдала автентифікація: невірні облікові дані, відкликаний чи прострочений refresh-токен. */
public non-sealed class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNAUTHORIZED;
    }

    @Override
    public String title() {
        return "Unauthorized";
    }
}
