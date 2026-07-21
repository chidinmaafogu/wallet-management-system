package com.example.test.service;

import org.springframework.stereotype.Component;

@Component
public class NubanGenerator {

    private static final int[] WEIGHT_PATTERN = {3, 7, 3};
    private static final int INSTITUTION_CODE_LENGTH = 6;
    private static final int SERIAL_LENGTH = 9;
    private static final int NUBAN_LENGTH = 10;

    public String generate(String institutionCode, long serial) {
        String paddedSerial = padSerial(serial);
        return paddedSerial + checkDigit(institutionCode, paddedSerial);
    }

    public boolean isValid(String institutionCode, String nuban) {
        if (nuban == null || nuban.length() != NUBAN_LENGTH || !nuban.chars().allMatch(Character::isDigit)) {
            return false;
        }
        String serial = nuban.substring(0, SERIAL_LENGTH);
        int expected = checkDigit(institutionCode, serial);
        return expected == Character.getNumericValue(nuban.charAt(SERIAL_LENGTH));
    }

    public int checkDigit(String institutionCode, String serial) {
        String seed = padInstitutionCode(institutionCode) + serial;
        int sum = 0;
        for (int i = 0; i < seed.length(); i++) {
            int digit = Character.getNumericValue(seed.charAt(i));
            sum += digit * WEIGHT_PATTERN[i % WEIGHT_PATTERN.length];
        }
        int check = 10 - (sum % 10);
        return check == 10 ? 0 : check;
    }

    private String padInstitutionCode(String institutionCode) {
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new IllegalArgumentException("Institution code is required");
        }
        if (institutionCode.length() > INSTITUTION_CODE_LENGTH) {
            throw new IllegalArgumentException("Institution code must not exceed 6 digits");
        }
        return "0".repeat(INSTITUTION_CODE_LENGTH - institutionCode.length()) + institutionCode;
    }

    private String padSerial(long serial) {
        String value = Long.toString(serial);
        if (value.length() > SERIAL_LENGTH) {
            throw new IllegalStateException("Account serial has exhausted the 9-digit NUBAN range");
        }
        return "0".repeat(SERIAL_LENGTH - value.length()) + value;
    }
}
