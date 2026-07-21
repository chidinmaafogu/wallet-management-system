package com.example.test.dto;

import java.util.List;

public record StatementResponse(
        String accountNumber,
        List<StatementEntryResponse> entries,
        Long nextCursor,
        boolean hasMore
) {
}
