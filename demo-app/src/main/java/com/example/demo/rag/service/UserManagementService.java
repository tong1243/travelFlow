package com.example.demo.rag.service;

import com.example.demo.rag.dto.UserProfileResponse;
import com.example.demo.rag.repo.UserAccountRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserManagementService {

    private final UserAccountRepository userAccountRepository;

    public UserManagementService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public List<UserProfileResponse> listUsers() {
        return userAccountRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(item -> new UserProfileResponse(item.getId(), item.getUsername(), item.getEmail(), item.getRole()))
                .toList();
    }
}
