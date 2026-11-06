package com.crediexpress.config.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Duración máxima de una sesión. Firebase renueva el ID token cada hora sin pedir contraseña, así que
 * la expiración del token no limita la sesión. El claim {@code auth_time} (firmado por Firebase) dice
 * cuándo se inició sesión con contraseña; si pasó más de {@code maxima}, el token se rechaza (401) y
 * hay que volver a entrar. El front cierra la sesión al mismo tiempo; esto es la barrera real.
 */
public class SesionMaxima implements OAuth2TokenValidator<Jwt> {

    static final String AUTH_TIME = "auth_time";
    /** Diferencia tolerada entre el reloj de Google y el del servidor. */
    private static final Duration TOLERANCIA = Duration.ofMinutes(1);

    private final Duration maxima;
    private final Clock clock;

    public SesionMaxima(Duration maxima, Clock clock) {
        this.maxima = maxima;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Instant inicio = inicioDeSesion(token.getClaims().get(AUTH_TIME));
        if (inicio == null) {
            return error("El token no indica cuándo se inició sesión.");
        }
        if (inicio.plus(maxima).plus(TOLERANCIA).isBefore(clock.instant())) {
            return error("La sesión venció; vuelve a iniciar sesión.");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static Instant inicioDeSesion(Object valor) {
        if (valor instanceof Instant instante) {
            return instante;
        }
        if (valor instanceof Number segundos) {
            return Instant.ofEpochSecond(segundos.longValue());
        }
        return null;
    }

    private static OAuth2TokenValidatorResult error(String descripcion) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, descripcion, null));
    }
}
