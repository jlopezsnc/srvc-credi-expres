package com.crediexpress.service.cobranza;

/**
 * Resultado de una operación idempotente: {@code repetida} indica que ya se había hecho con la misma
 * llave y datos, y {@code valor} es lo que se registró la primera vez (el controller responde 200 en
 * lugar de 201).
 */
public record ResultadoOperacion<T>(T valor, boolean repetida) {

    public static <T> ResultadoOperacion<T> nueva(T valor) {
        return new ResultadoOperacion<>(valor, false);
    }

    public static <T> ResultadoOperacion<T> repetida(T valor) {
        return new ResultadoOperacion<>(valor, true);
    }
}
