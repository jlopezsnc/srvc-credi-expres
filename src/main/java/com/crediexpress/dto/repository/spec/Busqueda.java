package com.crediexpress.dto.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * Búsqueda "contiene" segura: el texto va como parámetro y se escapan los comodines de LIKE.
 * Acentos y mayúsculas los ignora la collation de las tablas (utf8mb4_0900_ai_ci).
 */
final class Busqueda {

    private static final char ESCAPE = '\\';

    private Busqueda() {
    }

    static Predicate contiene(CriteriaBuilder cb, Expression<String> campo, String texto) {
        if (texto == null || texto.isBlank()) {
            return cb.conjunction();
        }
        String patron = "%" + escapar(texto.trim().toLowerCase()) + "%";
        return cb.like(cb.lower(campo), patron, ESCAPE);
    }

    private static String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
