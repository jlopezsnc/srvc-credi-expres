package com.crediexpress.dto.view.response;

import java.util.List;

/** Una página de préstamos y cuántos hay en cada filtro (los contadores no dependen de la búsqueda). */
public record PaginaPrestamosResponse(
        List<PrestamoResumenResponse> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas,
        Contadores contadores) {

    /** {@code activos} = al corriente + con atraso; {@code todos} incluye en convenio y cancelados. */
    public record Contadores(long activos, long alCorriente, long conAtraso, long liquidados, long enConvenio,
                             long cancelados, long todos) {
    }

    public static PaginaPrestamosResponse de(List<PrestamoResumenResponse> contenido, int pagina, int tamano,
                                             long totalElementos, Contadores contadores) {
        return new PaginaPrestamosResponse(contenido, pagina, tamano, totalElementos,
                PaginaConTotalResponse.totalPaginas(totalElementos, tamano), contadores);
    }
}
