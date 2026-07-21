package com.example.test.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 80, message = "First name must not exceed 80 characters")
        @Schema(example = "Chidinma")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 80, message = "Last name must not exceed 80 characters")
        @Schema(example = "Afogu")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        @Schema(example = "chidinma@example.com")
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^0[789][01]\\d{8}$", message = "Phone number must be a valid 11-digit Nigerian number")
        @Schema(example = "08031234567")
        String phoneNumber
) {
}
