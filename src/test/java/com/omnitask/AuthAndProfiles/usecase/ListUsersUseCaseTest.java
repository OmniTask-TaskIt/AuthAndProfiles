package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.ListUsersUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AdminUserDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.PageResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListUsersUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ListUsersUseCase listUsersUseCase;

    private Page<User> pageOf(User... users) {
        return new PageImpl<>(List.of(users));
    }

    private User user() {
        return User.builder().id("user-1").email("test@gmail.com").name("Test").role(Role.SEEKER)
                .accountStatus(AccountStatus.ACTIVE).emailVerified(true).passwordHash("secreto").build();
    }

    @Test
    void execute_deberiaListarTodos_cuandoNoHayFiltros() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(pageOf(user()));

        PageResponseDTO<AdminUserDTO> result = listUsersUseCase.execute(null, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).email()).isEqualTo("test@gmail.com");
        assertThat(result.content().get(0).emailVerified()).isTrue();
        assertThat(result.page()).isZero();
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void execute_deberiaTratarUnaBusquedaEnBlancoComoSinFiltro() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(pageOf(user()));

        PageResponseDTO<AdminUserDTO> result = listUsersUseCase.execute(null, "   ", 0, 20);

        assertThat(result.content()).hasSize(1);
        verify(userRepository).findAll(any(Pageable.class));
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void execute_deberiaFiltrarPorEstado() {
        when(userRepository.findByAccountStatus(eq(AccountStatus.SUSPENDED), any(Pageable.class)))
                .thenReturn(pageOf(user()));

        PageResponseDTO<AdminUserDTO> result = listUsersUseCase.execute(AccountStatus.SUSPENDED, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        verify(userRepository).findByAccountStatus(eq(AccountStatus.SUSPENDED), any(Pageable.class));
    }

    @Test
    void execute_deberiaFiltrarPorCorreo_recortandoEspacios() {
        when(userRepository.findByEmailContainingIgnoreCase(eq("test"), any(Pageable.class)))
                .thenReturn(pageOf(user()));

        PageResponseDTO<AdminUserDTO> result = listUsersUseCase.execute(null, "  test ", 0, 20);

        assertThat(result.content()).hasSize(1);
        verify(userRepository).findByEmailContainingIgnoreCase(eq("test"), any(Pageable.class));
    }

    @Test
    void execute_deberiaFiltrarPorEstadoYCorreo() {
        when(userRepository.findByAccountStatusAndEmailContainingIgnoreCase(eq(AccountStatus.ACTIVE), eq("test"),
                any(Pageable.class))).thenReturn(pageOf(user()));

        PageResponseDTO<AdminUserDTO> result = listUsersUseCase.execute(AccountStatus.ACTIVE, "test", 0, 20);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    void execute_deberiaLimitarElTamanoDePaginaYCorregirValoresInvalidos() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(pageOf(user()));

        listUsersUseCase.execute(null, null, -5, 5000);
        listUsersUseCase.execute(null, null, 2, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository, org.mockito.Mockito.times(2)).findAll(captor.capture());
        assertThat(captor.getAllValues().get(0).getPageNumber()).isZero();
        assertThat(captor.getAllValues().get(0).getPageSize()).isEqualTo(100);
        assertThat(captor.getAllValues().get(1).getPageNumber()).isEqualTo(2);
        assertThat(captor.getAllValues().get(1).getPageSize()).isEqualTo(1);
    }
}
