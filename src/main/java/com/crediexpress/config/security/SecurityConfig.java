package com.crediexpress.config.security;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * API sin sesión: cada petición trae el ID token de Firebase en {@code Authorization: Bearer}.
 * Sin roles (docs §1.4): todo usuario del negocio (lista blanca de UIDs) tiene acceso total; sin token
 * o con la sesión vencida responde 401 y con token de alguien fuera de la lista, 403.
 * El nombre del usuario autenticado es el UID de Firebase (claim {@code sub}).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Value("${seguridad.usuarios-permitidos:}") String usuariosPermitidos) throws Exception {
        http
                // Sin cookies de sesión no hay CSRF que proteger: el token va en un header.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                        // Token válido Y usuario del negocio (lista blanca de UIDs).
                        .anyRequest().access(new UsuariosPermitidos(usuariosPermitidos)))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Valida el ID token de Firebase: firma (llaves públicas de Google), emisor, audiencia, expiración
     * y duración máxima de la sesión ({@link SesionMaxima}). Se declara a mano porque Spring Boot no
     * permite agregar validadores propios a su decoder automático.
     */
    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String emisor,
            @Value("${spring.security.oauth2.resourceserver.jwt.audiences}") String audiencia,
            @Value("${seguridad.sesion-maxima:12h}") Duration sesionMaxima) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(emisor),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(audiencia)),
                new SesionMaxima(sesionMaxima, Clock.systemUTC())));
        return decoder;
    }
}
