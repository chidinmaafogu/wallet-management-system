package com.example.test.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NubanGeneratorTest {

    private final NubanGenerator generator = new NubanGenerator();

    @Test
    @DisplayName("matches the published CBN vector: GTBank 058, serial 001656322 -> 0016563228")
    void matchesPublishedVector() {
        assertThat(generator.checkDigit("058", "001656322")).isEqualTo(8);
        assertThat(generator.generate("058", 1656322L)).isEqualTo("0016563228");
    }

    @Test
    @DisplayName("padding a 3-digit code to 6 digits yields the same check digit")
    void paddingIsBackwardCompatible() {
        assertThat(generator.checkDigit("058", "001656322"))
                .isEqualTo(generator.checkDigit("000058", "001656322"));
    }

    @ParameterizedTest
    @CsvSource({"000999, 1", "000999, 100000001", "011, 23456789", "000058, 1656322"})
    void generatesTenDigitNubansThatValidate(String institutionCode, long serial) {
        String nuban = generator.generate(institutionCode, serial);

        assertThat(nuban).hasSize(10).containsOnlyDigits();
        assertThat(generator.isValid(institutionCode, nuban)).isTrue();
    }

    @Test
    void rejectsANubanWithAMistypedDigit() {
        String nuban = generator.generate("000999", 100000001L);
        String mistyped = (nuban.charAt(0) == '9' ? "8" : "9") + nuban.substring(1);

        assertThat(generator.isValid("000999", mistyped)).isFalse();
    }

    @Test
    void rejectsMalformedInput() {
        assertThat(generator.isValid("000999", null)).isFalse();
        assertThat(generator.isValid("000999", "123")).isFalse();
        assertThat(generator.isValid("000999", "12345678AB")).isFalse();
    }

    @Test
    void sequentialSerialsProduceDistinctAccountNumbers() {
        Set<String> generated = new HashSet<>();
        for (long serial = 100000001L; serial < 100001001L; serial++) {
            generated.add(generator.generate("000999", serial));
        }
        assertThat(generated).hasSize(1000);
    }

    @Test
    void refusesSerialsBeyondTheNubanRange() {
        assertThatThrownBy(() -> generator.generate("000999", 1_000_000_000L))
                .isInstanceOf(IllegalStateException.class);
    }
}
