package com.crediexpress.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * "Hoy" lo decide el back con la hora de México, no el reloj del navegador ni el del servidor
 * (en GKE corre en UTC). Los servicios reciben el {@link Clock} inyectado para poder probarlos
 * con una fecha fija.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Mexico_City");

    @Bean
    Clock clock() {
        return Clock.system(ZONA_NEGOCIO);
    }
}
