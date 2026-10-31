package com.crediexpress.service.cobranza;

import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.entity.Pago;
import com.crediexpress.dto.entity.TipoPago;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lo que comparten todos los renglones de {@code pago} de una misma operación (quién, cuándo, con
 * qué llave y con qué datos) y la fábrica de cada renglón.
 */
public record OperacionPago(LlaveIdempotencia llave, String huella, DatosPago datos, String usuario,
                            Instant fechaRegistro) {

    public Pago renglon(EstadoPrestamos cuota, BigDecimal importe, BigDecimal recargo, TipoPago tipo,
                        BigDecimal saldoCuota, boolean prestamoLiquidado) {
        return Pago.builder()
                .llaveIdempotencia(llave.valor())
                .hashPeticion(huella)
                .cuota(cuota)
                .prestamo(cuota.getIdCalculadoraPrestamo())
                .cliente(cuota.getIdCliente())
                .importe(importe)
                .recargo(recargo)
                .fechaPago(datos.fechaPago())
                .metodoPago(datos.metodoPago())
                .referencia(datos.referencia())
                .nota(datos.nota())
                .tipo(tipo)
                .saldoCuota(saldoCuota)
                .prestamoLiquidado(prestamoLiquidado)
                .registradoPor(usuario)
                .fechaRegistro(fechaRegistro)
                .build();
    }
}
