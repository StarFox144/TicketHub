package com.tickethub.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public non-sealed class NotFoundException extends DomainException {

    public NotFoundException(String resource, UUID id) {
        super("%s %s not found".formatted(resource, id));
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }

    @Override
    public String title() {
        return "Resource not found";
    }
}
