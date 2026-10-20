package com.tickethub.event.domain;

public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED,
    FINISHED;

    public boolean isEditable() {
        return this == DRAFT || this == PUBLISHED;
    }
}
