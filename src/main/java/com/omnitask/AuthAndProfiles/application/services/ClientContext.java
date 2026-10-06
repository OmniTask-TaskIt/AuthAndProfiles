package com.omnitask.AuthAndProfiles.application.services;

/**
 * Datos del cliente que inicia sesión: IP, User-Agent y, si el front lo envía, un identificador estable del
 * dispositivo (cabecera X-Device-Id). Sirve para registrar la sesión (RF-AUTH-10), limitar dispositivos
 * (RF-AUTH-13) y detectar inicios de sesión sospechosos (RF-AUTH-11).
 */
public record ClientContext(String ip, String userAgent, String deviceId) {
}
