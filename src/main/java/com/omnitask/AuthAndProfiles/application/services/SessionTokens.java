package com.omnitask.AuthAndProfiles.application.services;

/** Par de tokens emitido al abrir una sesión; ambos llevan el id de la sesión en el claim "sid". */
public record SessionTokens(String accessToken, String refreshToken) {
}
