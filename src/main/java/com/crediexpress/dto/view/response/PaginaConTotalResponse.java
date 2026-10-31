package com.crediexpress.dto.view.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.List;

/**
 * Una página de una lista más el importe de TODO lo filtrado (no solo de la página), para el pie de
 * la tabla: "23 cuotas · $18,450.00 por cobrar".
 */
public record PaginaConTotalResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importeTotal) {

    public static <T> PaginaConTotalResponse<T> de(List<T> contenido, int pagina, int tamano, long totalElementos,
                                                   BigDecimal importeTotal) {
        return new PaginaConTotalResponse<>(contenido, pagina, tamano, totalElementos,
                totalPaginas(totalElementos, tamano), importeTotal);
    }

    /** Corta en memoria una lista que ya viene completa y ordenada. */
    public static <T> PaginaConTotalResponse<T> cortar(List<T> todos, int pagina, int tamano, BigDecimal importeTotal) {
        int desde = Math.min(todos.size(), pagina * tamano);
        int hasta = Math.min(todos.size(), desde + tamano);
        return de(List.copyOf(todos.subList(desde, hasta)), pagina, tamano, todos.size(), importeTotal);
    }

    static int totalPaginas(long totalElementos, int tamano) {
        return (int) ((totalElementos + tamano - 1) / tamano);
    }
}
