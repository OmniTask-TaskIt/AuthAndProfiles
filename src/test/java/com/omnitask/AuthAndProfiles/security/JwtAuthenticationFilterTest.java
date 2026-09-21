package com.omnitask.AuthAndProfiles.security;

import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.AccessRevocationRepository;
import com.omnitask.AuthAndProfiles.infrastructure.security.JwtAuthenticationFilter;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private AccessRevocationRepository accessRevocationRepository;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void doFilter_deberiaAutenticarConElRolComoAuthority_cuandoElAccessTokenEsValido() throws Exception {
        MockHttpServletRequest request = requestWithBearer("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractEmail("access-token")).thenReturn("test@gmail.com");
        when(jwtService.extractRole("access-token")).thenReturn("PROVIDER");
        when(jwtService.isTokenValid("access-token", "test@gmail.com")).thenReturn(true);
        when(accessRevocationRepository.isUserRevoked("test@gmail.com")).thenReturn(false);

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("test@gmail.com");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_PROVIDER");
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_noDeberiaAutenticar_cuandoNoHayHeaderAuthorization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    void doFilter_noDeberiaAutenticar_cuandoElHeaderNoEsBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    void doFilter_noDeberiaAutenticar_cuandoElTokenEsUnRefreshTokenSinRol() throws Exception {
        MockHttpServletRequest request = requestWithBearer("refresh-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractEmail("refresh-token")).thenReturn("test@gmail.com");
        when(jwtService.extractRole("refresh-token")).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_noDeberiaAutenticar_cuandoElRolEstaEnBlanco() throws Exception {
        MockHttpServletRequest request = requestWithBearer("token-sin-rol");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractEmail("token-sin-rol")).thenReturn("test@gmail.com");
        when(jwtService.extractRole("token-sin-rol")).thenReturn("  ");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_noDeberiaAutenticar_cuandoElTokenNoTieneEmail() throws Exception {
        MockHttpServletRequest request = requestWithBearer("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractEmail("access-token")).thenReturn(null);
        when(jwtService.extractRole("access-token")).thenReturn("SEEKER");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_noDeberiaAutenticar_cuandoElTokenNoEsValidoParaElEmail() throws Exception {
        MockHttpServletRequest request = requestWithBearer("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractEmail("access-token")).thenReturn("test@gmail.com");
        when(jwtService.extractRole("access-token")).thenReturn("SEEKER");
        when(jwtService.isTokenValid("access-token", "test@gmail.com")).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(accessRevocationRepository);
    }

    @Test
    void doFilter_noDeberiaAutenticar_cuandoElUsuarioFueRevocado() throws Exception {
        MockHttpServletRequest request = requestWithBearer("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractEmail("access-token")).thenReturn("test@gmail.com");
        when(jwtService.extractRole("access-token")).thenReturn("SEEKER");
        when(jwtService.isTokenValid("access-token", "test@gmail.com")).thenReturn(true);
        when(accessRevocationRepository.isUserRevoked("test@gmail.com")).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_deberiaContinuarSinAutenticar_cuandoElTokenEstaExpirado() throws Exception {
        MockHttpServletRequest request = requestWithBearer("expirado");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractEmail("expirado")).thenThrow(new ExpiredJwtException(null, null, "expirado"));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_noDeberiaSobrescribirUnaAutenticacionExistente() throws Exception {
        MockHttpServletRequest request = requestWithBearer("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication existente = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(existente);
        when(jwtService.extractEmail("access-token")).thenReturn("test@gmail.com");
        when(jwtService.extractRole("access-token")).thenReturn("SEEKER");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existente);
        verify(chain).doFilter(request, response);
    }
}
