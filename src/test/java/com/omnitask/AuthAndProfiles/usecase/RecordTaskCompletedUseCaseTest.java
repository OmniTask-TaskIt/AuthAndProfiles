package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.RecordTaskCompletedUseCase;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordTaskCompletedUseCaseTest {

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private RecordTaskCompletedUseCase recordTaskCompletedUseCase;

    @Test
    void execute_deberiaSumarUnaTareaCompletadaAlPerfilDelPrestador() {
        when(profileRepository.incrementTasksCompletedByUserId("user-1")).thenReturn(1L);

        assertThatCode(() -> recordTaskCompletedUseCase.execute("user-1")).doesNotThrowAnyException();

        verify(profileRepository).incrementTasksCompletedByUserId("user-1");
    }

    @Test
    void execute_noDeberiaFallar_cuandoElUsuarioNoTienePerfil() {
        // Reintentar no crea un perfil que no existe: se registra y el evento se da por atendido
        when(profileRepository.incrementTasksCompletedByUserId("user-fantasma")).thenReturn(0L);

        assertThatCode(() -> recordTaskCompletedUseCase.execute("user-fantasma")).doesNotThrowAnyException();
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoNoHayProviderId() {
        assertThatThrownBy(() -> recordTaskCompletedUseCase.execute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerId");
        assertThatThrownBy(() -> recordTaskCompletedUseCase.execute("  "))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(profileRepository);
    }
}
