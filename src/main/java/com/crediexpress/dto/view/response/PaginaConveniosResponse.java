package com.crediexpress.dto.view.response;

import java.util.List;

/** Una página de convenios y cuántos hay en cada filtro (los contadores no dependen de la búsqueda). */
public record PaginaConveniosResponse(
        List<ConvenioResumenResponse> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas,
        Contadores contadores) {

    /** {@code vigentes} = al corriente + con atraso. */
    public record Contadores(long vigentes, long conAtraso, long liquidados, long cancelados, long todos) {
    }

    public static PaginaConveniosResponse de(List<ConvenioResumenResponse> contenido, int pagina, int tamano,
                                             long totalElementos, Contadores contadores) {
        return new PaginaConveniosResponse(contenido, pagina, tamano, totalElementos,
                PaginaConTotalResponse.totalPaginas(totalElementos, tamano), contadores);
    }
}
