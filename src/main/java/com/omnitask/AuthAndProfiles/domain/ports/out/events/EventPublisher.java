package com.omnitask.AuthAndProfiles.domain.ports.out.events;

import com.omnitask.AuthAndProfiles.domain.events.EventType;

/** Puerto de salida para publicar eventos de dominio hacia otros microservicios. */
public interface EventPublisher {

    /**
     * @param type    tipo de evento (define el topic y el eventType del sobre)
     * @param key     clave de partición; se usa userId para conservar el orden por usuario
     * @param payload objeto serializable a JSON con los datos del evento
     */
    void publish(EventType type, String key, Object payload);
}
