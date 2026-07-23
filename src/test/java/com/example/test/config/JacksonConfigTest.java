package com.example.test.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JacksonConfig()
                    .instantInAppZoneModule(Clock.system(ZoneId.of("Africa/Lagos"))));

    @Test
    void rendersInstantInLagosOffset() throws Exception {
        Instant instant = Instant.parse("2026-07-23T13:06:38.501239Z");
        String json = mapper.writeValueAsString(instant);
        assertThat(json).isEqualTo("\"2026-07-23T14:06:38.501239+01:00\"");
    }
}
