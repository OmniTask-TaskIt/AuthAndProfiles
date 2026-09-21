package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AdminUserDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.PageResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Listado paginado de usuarios para el panel de administración. */
@Service
@RequiredArgsConstructor
public class ListUsersUseCase {

    static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;

    public PageResponseDTO<AdminUserDTO> execute(AccountStatus status, String emailQuery, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        String query = (emailQuery == null || emailQuery.isBlank()) ? null : emailQuery.trim();

        Page<User> result;
        if (status == null && query == null) {
            result = userRepository.findAll(pageable);
        } else if (query == null) {
            result = userRepository.findByAccountStatus(status, pageable);
        } else if (status == null) {
            result = userRepository.findByEmailContainingIgnoreCase(query, pageable);
        } else {
            result = userRepository.findByAccountStatusAndEmailContainingIgnoreCase(status, query, pageable);
        }

        return PageResponseDTO.from(result.map(AdminUserDTO::from));
    }
}
