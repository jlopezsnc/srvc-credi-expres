package com.crediexpress.dto.view.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Lista paginada del contrato ({@code pagina} desde 0). No expone el {@code Page} de Spring. */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<E, T> convertir) {
        return new PaginaResponse<>(pagina.getContent().stream().map(convertir).toList(),
                pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    public static <T> PaginaResponse<T> de(Page<?> pagina, List<T> contenido) {
        return new PaginaResponse<>(contenido, pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }
}
