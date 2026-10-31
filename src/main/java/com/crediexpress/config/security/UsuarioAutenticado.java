package com.crediexpress.config.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Quién hace la operación, para auditoría: el correo del token de Firebase y, si no viene, el uid
 * ({@code sub}). Nunca se toma del cuerpo de la petición.
 */
public final class UsuarioAutenticado {

    private static final String CLAIM_CORREO = "email";

    private UsuarioAutenticado() {
    }

    public static String de(Jwt token) {
        String correo = token.getClaimAsString(CLAIM_CORREO);
        return correo == null || correo.isBlank() ? token.getSubject() : correo;
    }
}
