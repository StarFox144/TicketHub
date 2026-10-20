package com.tickethub.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** Базовий клас доменних винятків; GlobalExceptionHandler перетворює його на ProblemDetail. */
public abstract sealed class DomainException extends RuntimeException
        permits NotFoundException, ConflictException, BusinessRuleException, UnauthorizedException {

    protected DomainException(String message) {
        super(message);
    }

    public abstract HttpStatus status();

    public abstract String title();

    /** Додаткові поля, що потрапляють у тіло відповіді (наприклад, список недоступних місць). */
    public Map<String, Object> properties() {
        return Map.of();
    }
}
