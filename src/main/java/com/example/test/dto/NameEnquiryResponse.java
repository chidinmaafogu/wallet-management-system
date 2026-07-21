package com.example.test.dto;

import com.example.test.model.enums.AccountStatus;

public record NameEnquiryResponse(
        String accountNumber,
        String institutionCode,
        String accountName,
        AccountStatus status
) {
}
