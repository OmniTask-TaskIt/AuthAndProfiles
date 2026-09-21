package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Respuesta paginada estable (no se serializa PageImpl directamente). */
public record PageResponseDTO<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponseDTO<T> from(Page<T> page) {
        return new PageResponseDTO<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
