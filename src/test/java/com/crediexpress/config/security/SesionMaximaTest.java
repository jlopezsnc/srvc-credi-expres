package com.crediexpress.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SesionMaximaTest {

    private static final Instant AHORA = Instant.parse("2026-11-05T20:00:00Z");
    private static final SesionMaxima VALIDADOR =
            new SesionMaxima(Duration.ofHours(12), Clock.fixed(AHORA, ZoneOffset.UTC));

    private static Jwt token(Object authTime) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "RS256").subject("uid");
        if (authTime != null) {
            builder.claim(SesionMaxima.AUTH_TIME, authTime);
        }
        return builder.build();
    }

    private static boolean valido(Object authTime) {
        return !VALIDADOR.validate(token(authTime)).hasErrors();
    }

    @Test
    void aceptaUnaSesionDeMenosDeDoceHoras() {
        assertThat(valido(AHORA.minus(Duration.ofHours(11)).getEpochSecond())).isTrue();
        assertThat(valido(AHORA.minus(Duration.ofHours(12)).getEpochSecond())).isTrue();
    }

    @Test
    void rechazaUnaSesionDeMasDeDoceHoras() {
        assertThat(valido(AHORA.minus(Duration.ofHours(12)).minus(Duration.ofMinutes(2)).getEpochSecond())).isFalse();
        assertThat(VALIDADOR.validate(token(AHORA.minus(Duration.ofDays(3)).getEpochSecond()))
                .getErrors().iterator().next().getErrorCode()).isEqualTo("invalid_token");
    }

    @Test
    void aceptaElClaimComoInstante() {
        assertThat(valido(AHORA.minus(Duration.ofHours(1)))).isTrue();
        assertThat(valido(AHORA.minus(Duration.ofHours(13)))).isFalse();
    }

    @Test
    void sinAuthTimeSeRechaza() {
        assertThat(valido(null)).isFalse();
        assertThat(valido("no-es-fecha")).isFalse();
    }
}
