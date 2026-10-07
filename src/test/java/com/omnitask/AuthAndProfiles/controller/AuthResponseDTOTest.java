package com.omnitask.AuthAndProfiles.controller;

import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthResponseDTOTest {

    @Test
    void elConstructorDeCuatroArgumentos_deberiaRepresentarUnLoginCompleto() {
        AuthResponseDTO dto = new AuthResponseDTO("access", "refresh", "ok", "test@gmail.com");

        assertThat(dto.getAccessToken()).isEqualTo("access");
        assertThat(dto.getRefreshToken()).isEqualTo("refresh");
        assertThat(dto.isTwoFactorRequired()).isFalse();
        assertThat(dto.getChallengeId()).isNull();
    }

    @Test
    void twoFactorChallenge_deberiaIndicarQueFaltaElSegundoFactorSinTokens() {
        AuthResponseDTO dto = AuthResponseDTO.twoFactorChallenge("test@gmail.com", "challenge-1");

        assertThat(dto.isTwoFactorRequired()).isTrue();
        assertThat(dto.getChallengeId()).isEqualTo("challenge-1");
        assertThat(dto.getEmail()).isEqualTo("test@gmail.com");
        assertThat(dto.getAccessToken()).isNull();
        assertThat(dto.getRefreshToken()).isNull();
        assertThat(dto.getMessage()).contains("código");
    }
}
