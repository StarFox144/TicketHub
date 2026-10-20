package com.tickethub.common.exception;

import org.springframework.http.HttpStatus;

/** Порушення бізнес-правила у валідному за форматом запиті (наприклад, endsAt раніше за startsAt). */
public non-sealed class BusinessRuleException extends DomainException {

    public BusinessRuleException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    @Override
    public String title() {
        return "Business rule violation";
    }
}
