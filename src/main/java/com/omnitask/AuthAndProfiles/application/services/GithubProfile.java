package com.omnitask.AuthAndProfiles.application.services;

/** Perfil mínimo obtenido de la API de GitHub tras el intercambio del código de autorización. */
public record GithubProfile(String email, String name, String login) {
}
