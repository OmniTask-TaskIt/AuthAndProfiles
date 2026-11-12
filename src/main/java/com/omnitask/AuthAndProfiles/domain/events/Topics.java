package com.omnitask.AuthAndProfiles.domain.events;

/**
 * Nombres lógicos de los canales de eventos. En RabbitMQ son las <b>routing keys</b> del exchange compartido
 * (el nombre se conserva del esquema anterior con Kafka para no cambiar el contrato con los otros equipos).
 * Se usan pocos canales "gruesos" y el tipo de evento viaja dentro del sobre (eventType).
 */
public final class Topics {

    /** Eventos de negocio que publica Authentication & Profile. Cabecera "key": userId. */
    public static final String AUTH_EVENTS = "taskit.auth.events";

    /** Eventos de auditoría de seguridad que publica Authentication & Profile (logins, accesos a documentos). */
    public static final String AUTH_AUDIT = "taskit.auth.audit";

    /** Eventos que publica Security and Audit HITL y que este MS consume. */
    public static final String SECURITY_EVENTS = "taskit.security.events";

    /** Eventos que publica Task Service y este MS consume (tareas completadas, RF-AUTHPR-8). */
    public static final String TASK_EVENTS = "taskit.task.events";

    /** Cola (y routing key del dead letter exchange) con los mensajes que no se pudieron procesar tras los reintentos. */
    public static final String DEAD_LETTER = "taskit.auth.dead-letter";

    // AITA
    public static final String AITA_EVENTS = "taskit.aita.events";

    private Topics() {
    }
}
