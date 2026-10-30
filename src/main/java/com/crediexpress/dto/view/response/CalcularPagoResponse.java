package com.crediexpress.dto.view.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.math.BigDecimal;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter
public class CalcularPagoResponse {
    private Integer plazosGenerados;
    private String resultado;
    /** Lo que guardó el back (puede diferir de lo que mostró el front si este calculó distinto). */
    private Integer idPrestamo;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal interesGenerado;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalPagar;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal importeCuota;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal importeUltimaCuota;
}
