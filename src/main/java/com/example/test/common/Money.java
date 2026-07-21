package com.example.test.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final int API_SCALE = 2;
    public static final int STORAGE_SCALE = 4;

    private Money() {
    }

    public static BigDecimal forApi(BigDecimal amount) {
        return amount == null ? null : amount.setScale(API_SCALE, RoundingMode.HALF_EVEN);
    }

    public static BigDecimal forStorage(BigDecimal amount) {
        return amount == null ? null : amount.setScale(STORAGE_SCALE, RoundingMode.UNNECESSARY);
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }
}
