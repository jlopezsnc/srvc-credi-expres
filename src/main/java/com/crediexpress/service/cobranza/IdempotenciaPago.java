package com.crediexpress.service.cobranza;

import com.crediexpress.dto.entity.Pago;
import com.crediexpress.dto.repository.PagoRepository;
import com.crediexpress.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Detecta peticiones repetidas por la llave de idempotencia. Se consulta con las cuotas ya
 * bloqueadas: si la misma petición llega dos veces a la vez, la segunda espera y aquí encuentra lo
 * que registró la primera.
 */
@Component
@RequiredArgsConstructor
public class IdempotenciaPago {

    static final String MENSAJE_LLAVE_REUTILIZADA =
            "Este identificador ya se usó para otro pago. Recarga la página e intenta de nuevo.";

    private final PagoRepository pagoRepository;

    /**
     * Los pagos que ya se registraron con esta llave; vacío si la llave es nueva.
     *
     * @throws ApiException 409 si la llave se usó con otros datos
     */
    public List<Pago> buscarRepetido(LlaveIdempotencia llave, String huella) {
        List<Pago> previos = pagoRepository.findByLlaveIdempotenciaOrderByIdPago(llave.valor());
        if (!previos.isEmpty() && !previos.getFirst().getHashPeticion().equals(huella)) {
            throw ApiException.conflicto(MENSAJE_LLAVE_REUTILIZADA);
        }
        return previos;
    }

    /** SHA-256 de la operación, su destino (cuota o préstamo) y los datos normalizados. */
    public static String huella(Operacion operacion, Integer idDestino, DatosPago datos) {
        String canonico = operacion.name() + "|" + idDestino + "|" + datos.canonico();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }

    public enum Operacion {
        REGISTRAR_PAGO,
        LIQUIDAR_PRESTAMO
    }
}
