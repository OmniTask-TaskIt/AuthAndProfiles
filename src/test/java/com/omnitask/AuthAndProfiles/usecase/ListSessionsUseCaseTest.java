package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.application.services.SessionService;
import com.omnitask.AuthAndProfiles.application.usecases.ListSessionsUseCase;
import com.omnitask.AuthAndProfiles.domain.models.UserSession;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.SessionResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListSessionsUseCaseTest {

    @Mock
    private SessionService sessionService;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private ListSessionsUseCase listSessionsUseCase;

    private UserSession session(String id, String deviceInfo, int minutesAgo) {
        LocalDateTime lastActive = LocalDateTime.now().minusMinutes(minutesAgo);
        return UserSession.builder().id(id).userEmail("test@gmail.com").deviceInfo(deviceInfo)
                .ipAddress("203.0.113.5").createdAt(lastActive.minusDays(1)).lastActiveAt(lastActive).build();
    }

    @Test
    void execute_deberiaListarDeLaMasRecienteALaMasAntiguaYMarcarLaSesionActual() {
        // Arrange: el repositorio las entrega de la menos a la más reciente
        UserSession antigua = session("sesion-1", "Firefox en Linux", 90);
        UserSession reciente = session("sesion-2", "Chrome en Windows", 2);
        when(jwtService.extractSessionId("access-token")).thenReturn("sesion-1");
        when(sessionService.listActive("test@gmail.com")).thenReturn(List.of(antigua, reciente));

        // Act
        List<SessionResponseDTO> result = listSessionsUseCase.execute("test@gmail.com", "access-token");

        // Assert
        assertThat(result).extracting(SessionResponseDTO::getId).containsExactly("sesion-2", "sesion-1");
        assertThat(result).extracting(SessionResponseDTO::isCurrent).containsExactly(false, true);
        assertThat(result.get(0).getDeviceInfo()).isEqualTo("Chrome en Windows");
        assertThat(result.get(0).getIpAddress()).isEqualTo("203.0.113.5");
    }

    @Test
    void execute_noDeberiaMarcarNingunaComoActual_cuandoElTokenNoTieneSesion() {
        // Arrange: token anterior a las sesiones
        when(jwtService.extractSessionId("access-token")).thenReturn(null);
        when(sessionService.listActive("test@gmail.com")).thenReturn(List.of(session("sesion-1", "Chrome en Windows", 2)));

        // Act
        List<SessionResponseDTO> result = listSessionsUseCase.execute("test@gmail.com", "access-token");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isCurrent()).isFalse();
    }
}
