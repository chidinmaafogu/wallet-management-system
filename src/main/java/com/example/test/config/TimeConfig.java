package com.example.test.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Every persisted timestamp is an {@link java.time.Instant} — an absolute moment, independent of
 * any timezone. This clock exists so services never call {@code Instant.now()} directly: tests can
 * substitute a fixed clock, and the zone below is available for the few operations that genuinely
 * need civil time (statement day boundaries, month-end cutoffs), not for formatting the wire.
 *
 * <p>The zone comes from the hosting environment — the {@code TZ} variable the container is started
 * with — and {@code wallet.timezone} overrides it when the host zone cannot be set directly. The
 * zone never reaches the API: responses stay ISO-8601 UTC regardless of where this runs.
 */
@Slf4j
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${wallet.timezone:}") String configuredZone) {
        ZoneId zone = configuredZone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(configuredZone);
        log.info("Civil-time zone resolved to {} (source: {})",
                zone, configuredZone.isBlank() ? "host environment" : "wallet.timezone");
        return Clock.system(zone);
    }
}
