package com.omnitask.AuthAndProfiles.domain.events;

/** Tipos de evento que publica este microservicio y el topic al que pertenece cada uno. */
public enum EventType {
    USER_REGISTERED("UserRegistered", Topics.AUTH_EVENTS),
    IDENTITY_DOCUMENT_SUBMITTED("IdentityDocumentSubmitted", Topics.AUTH_EVENTS),
    IDENTITY_VERIFICATION_UPDATED("IdentityVerificationUpdated", Topics.AUTH_EVENTS),
    ACCOUNT_STATUS_CHANGED("AccountStatusChanged", Topics.AUTH_EVENTS),
    ACCOUNT_DELETED("AccountDeleted", Topics.AUTH_EVENTS),
    SECURITY_AUDIT("SecurityAudit", Topics.AUTH_AUDIT),
    REVIEW_CREATED("ReviewCreated", Topics.AUTH_EVENTS),
    REPUTATION_UPDATED("ReputationUpdated", Topics.AUTH_EVENTS),
    USER_REPORTED("UserReported", Topics.AUTH_EVENTS);

    private final String eventName;
    private final String topic;

    EventType(String eventName, String topic) {
        this.eventName = eventName;
        this.topic = topic;
    }

    /** Valor que viaja en el campo eventType del sobre. */
    public String getEventName() {
        return eventName;
    }

    public String getTopic() {
        return topic;
    }
}
