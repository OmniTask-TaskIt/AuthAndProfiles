package com.omnitask.AuthAndProfiles.domain.events;

/**
 * Nombres de los topics (Event Hubs en Azure). Se usan pocos topics "gruesos" y el tipo de evento
 * viaja dentro del sobre (eventType), porque el nivel Standard de Event Hubs permite 10 hubs por namespace.
 */
public final class Topics {

    /** Eventos de negocio que publica Authentication & Profile. Clave de partición: userId. */
    public static final String AUTH_EVENTS = "taskit.auth.events";

    /** Eventos de auditoría de seguridad que publica Authentication & Profile (logins, accesos a documentos). */
    public static final String AUTH_AUDIT = "taskit.auth.audit";

    /** Eventos que publica Security and Audit HITL y que este MS consume. */
    public static final String SECURITY_EVENTS = "taskit.security.events";

    /** Mensajes que no se pudieron procesar tras los reintentos. */
    public static final String DEAD_LETTER = "taskit.auth.dead-letter";

    private Topics() {
    }
}
