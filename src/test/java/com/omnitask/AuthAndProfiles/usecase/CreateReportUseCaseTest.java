package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.ChangeAccountStatusUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.CreateReportUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.ReportStatus;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.UserReportedEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.ConflictException;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.Report;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ReportRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateReportUseCaseTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private ChangeAccountStatusUseCase changeAccountStatusUseCase;

    @InjectMocks
    private CreateReportUseCase createReportUseCase;

    private User reporter;
    private User reviewee;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(createReportUseCase, "autoSuspendThreshold", 3);
        reporter = User.builder().id("user-1").email("test@gmail.com").build();
        reviewee = User.builder().id("user-2").email("otro@gmail.com").accountStatus(AccountStatus.ACTIVE).build();
    }

    private void stubHappyPath() {
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(reporter));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(reviewee));
        when(reportRepository.existsByReporterIdAndRevieweeIdAndStatus("user-1", "user-2", ReportStatus.OPEN))
                .thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            r.setId("report-1");
            return r;
        });
    }

    @Test
    void execute_deberiaGuardarElReportePublicarElEventoYNoSuspender_cuandoNoAlcanzaElUmbral() {
        stubHappyPath();
        when(reportRepository.countByRevieweeIdAndStatus("user-2", ReportStatus.OPEN)).thenReturn(1L);

        Report result = createReportUseCase.execute("test@gmail.com", "user-2", "Fraude", "detalle");

        assertThat(result.getId()).isEqualTo("report-1");
        assertThat(result.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(result.getReporterId()).isEqualTo("user-1");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(eq(EventType.USER_REPORTED), eq("user-2"), payload.capture());
        assertThat(((UserReportedEvent) payload.getValue()).reason()).isEqualTo("Fraude");

        verifyNoInteractions(changeAccountStatusUseCase);
    }

    @Test
    void execute_deberiaSuspenderPreventivamente_cuandoSeAlcanzaElUmbral() {
        stubHappyPath();
        when(reportRepository.countByRevieweeIdAndStatus("user-2", ReportStatus.OPEN)).thenReturn(3L);

        createReportUseCase.execute("test@gmail.com", "user-2", "Fraude", null);

        verify(changeAccountStatusUseCase).execute(eq("user-2"), eq(AccountStatus.SUSPENDED),
                org.mockito.ArgumentMatchers.contains("3 reportes"), eq("system:auto-report-threshold"));
    }

    @Test
    void execute_noDeberiaVolverASuspender_cuandoLaCuentaYaEstaRestringida() {
        reviewee.setAccountStatus(AccountStatus.SUSPENDED);
        stubHappyPath();

        createReportUseCase.execute("test@gmail.com", "user-2", "Fraude", null);

        verifyNoInteractions(changeAccountStatusUseCase);
        verify(reportRepository, never()).countByRevieweeIdAndStatus(anyString(), any());
    }

    @Test
    void execute_deberiaRechazarAutoReporte() {
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(reporter));

        assertThatThrownBy(() -> createReportUseCase.execute("test@gmail.com", "user-1", "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a ti mismo");
        verifyNoInteractions(reportRepository, eventPublisher, changeAccountStatusUseCase);
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElUsuarioReportadoNoExiste() {
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(reporter));
        when(userRepository.findById("user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createReportUseCase.execute("test@gmail.com", "user-2", "x", null))
                .isInstanceOf(NotFoundException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElReporterNoExiste() {
        // Cubre la rama del orElseThrow() de findByEmail(reporterEmail), nunca ejercitada porque todos
        // los demás tests parten de un reporter existente.
        when(userRepository.findByEmail("fantasma@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createReportUseCase.execute("fantasma@gmail.com", "user-2", "x", null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Usuario no encontrado");
        verifyNoInteractions(reportRepository, eventPublisher, changeAccountStatusUseCase);
    }

    @Test
    void execute_deberiaContinuarConElUmbral_cuandoElReportadoNoTieneAccountStatus() {
        // reviewee.getAccountStatus() == null: cubre la rama corta del "&&" en maybeAutoSuspend, distinta
        // de ACTIVE (false) y SUSPENDED (true) que ya cubren los otros tests.
        reviewee.setAccountStatus(null);
        stubHappyPath();
        when(reportRepository.countByRevieweeIdAndStatus("user-2", ReportStatus.OPEN)).thenReturn(3L);

        createReportUseCase.execute("test@gmail.com", "user-2", "Fraude", null);

        verify(changeAccountStatusUseCase).execute(eq("user-2"), eq(AccountStatus.SUSPENDED),
                org.mockito.ArgumentMatchers.contains("3 reportes"), eq("system:auto-report-threshold"));
    }

    @Test
    void execute_deberiaRechazarUnSegundoReporteAbiertoDelMismoReporterContraElMismoUsuario() {
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(reporter));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(reviewee));
        when(reportRepository.existsByReporterIdAndRevieweeIdAndStatus("user-1", "user-2", ReportStatus.OPEN))
                .thenReturn(true);

        assertThatThrownBy(() -> createReportUseCase.execute("test@gmail.com", "user-2", "x", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("reporte abierto");
        verify(reportRepository, never()).save(any());
        verifyNoInteractions(eventPublisher, changeAccountStatusUseCase);
    }
}