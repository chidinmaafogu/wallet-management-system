package com.example.test.controller;

import com.example.test.common.ApiResponse;
import com.example.test.dto.CreateUserRequest;
import com.example.test.dto.UserResponse;
import com.example.test.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User registration and lookup")
public class UserController {

    private final UserAccountService userAccountService;

    @PostMapping
    @Operation(summary = "Register a user",
            description = "Creates the user, generates a NUBAN account and provisions a zero-balance wallet.")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userAccountService.createUserAndAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(user, "User and account created"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a user with their accounts")
    public ResponseEntity<ApiResponse<UserResponse>> find(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userAccountService.findUser(id), "User retrieved"));
    }
}
