package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchProfileUseCase {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    public List<Profile> execute(String query) {
        if (query != null && query.contains("@")) {
            Optional<User> userOpt = userRepository.findByEmail(query.trim());
            if (userOpt.isPresent()) {
                return profileRepository.findByUserId(userOpt.get().getId())
                        .map(List::of)
                        .orElse(new ArrayList<>());
            }
            return new ArrayList<>();
        }

        List<User> users = userRepository.findByNameContainingIgnoreCase(query);
        List<String> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        return profileRepository.findByUserIdIn(userIds);
    }
}