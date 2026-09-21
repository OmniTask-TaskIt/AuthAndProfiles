package com.omnitask.AuthAndProfiles.infrastructure.security;

import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.AccessRevocationRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autentica cada petición a partir del access token JWT:
 * <ul>
 * <li>Solo se aceptan access tokens (los refresh tokens no llevan rol y se ignoran).</li>
 * <li>El rol del token se convierte en la authority ROLE_X para la autorización por rol.</li>
 * <li>Se rechazan los tokens de usuarios revocados (cuenta suspendida o bloqueada).</li>
 * </ul>
 * Ante cualquier problema con el token la petición sigue sin autenticar (respuesta 401).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AccessRevocationRepository accessRevocationRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        try {
            String userEmail = jwtService.extractEmail(jwt);
            String role = jwtService.extractRole(jwt);

            boolean isAccessToken = role != null && !role.isBlank();

            if (userEmail != null
                    && isAccessToken
                    && SecurityContextHolder.getContext().getAuthentication() == null
                    && jwtService.isTokenValid(jwt, userEmail)
                    && !accessRevocationRepository.isUserRevoked(userEmail)) {

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userEmail,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            logger.warn("Token JWT rechazado, la petición continúa sin autenticar: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
