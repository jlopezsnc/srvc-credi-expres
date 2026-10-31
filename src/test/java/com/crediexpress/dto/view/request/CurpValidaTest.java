package com.crediexpress.dto.view.request;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CurpValidaTest {

    private final CurpValida.Validador validador = new CurpValida.Validador();

    @ParameterizedTest
    @ValueSource(strings = {"CORJ101020MTCPRR06", "PEGA900515HTCRMN07", " pega900515htcrmn07 "})
    void aceptaCurpConEstructuraYDigitoCorrectos(String curp) {
        assertThat(validador.isValid(curp, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CORJ101020MTCPRR01",   // dígito verificador equivocado
            "AEDO11223344HTCHR01",  // 19 caracteres
            "CORJ101320MTCPRR06",   // mes 13
            "CORJ101020XTCPRR06",   // sexo inválido
            "CORJ101020MZZPRR06",   // entidad inexistente
            "SIN CURP"})
    void rechazaCurpInvalida(String curp) {
        assertThat(validador.isValid(curp, null)).isFalse();
    }

    @Test
    void vacioONuloLoDecideNotBlank() {
        assertThat(validador.isValid(null, null)).isTrue();
        assertThat(validador.isValid("  ", null)).isTrue();
    }
}
