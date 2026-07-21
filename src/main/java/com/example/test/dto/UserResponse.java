package com.example.test.dto;

import com.example.test.model.User;

import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        List<AccountResponse> accounts,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user, List<AccountResponse> accounts) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                accounts,
                user.getCreatedAt());
    }
}
