package com.omnitask.AuthAndProfiles.controller;

import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ProfileResponseDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileResponseDTOTest {

    @Test
    void fromProfile_deberiaIncluirElHistorialDeActividad() {
        LocalDateTime alta = LocalDateTime.of(2026, 3, 15, 9, 30);
        Profile profile = Profile.builder().userId("user-1").tasksCompleted(12).createdAt(alta).build();

        ProfileResponseDTO dto = ProfileResponseDTO.fromProfile(profile);

        assertThat(dto.getTasksCompleted()).isEqualTo(12);
        assertThat(dto.getMemberSince()).isEqualTo(alta.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void fromProfile_deberiaDejarMemberSinceVacio_cuandoElPerfilNoTieneFechaDeAlta() {
        ProfileResponseDTO dto = ProfileResponseDTO.fromProfile(Profile.builder().userId("user-1").build());

        assertThat(dto.getTasksCompleted()).isZero();
        assertThat(dto.getMemberSince()).isNull();
    }
}
