package com.crediexpress.service.convenio;

import com.crediexpress.dto.view.request.GenerarConvenioRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * SHA-256 de la petición normalizada (préstamos ordenados y sin repetir, importes sin ceros de más):
 * con la misma llave de idempotencia, otra petición con otros datos se rechaza.
 */
public final class HuellaConvenio {

    private HuellaConvenio() {
    }

    public static String de(GenerarConvenioRequest request) {
        String prestamos = new TreeSet<>(request.idsPrestamo()).stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        String canonico = String.join("|",
                "GENERAR_CONVENIO",
                String.valueOf(request.idClienteTitular()),
                prestamos,
                String.valueOf(request.idModalidad()),
                String.valueOf(request.numeroPagos()),
                plano(request.tasaInteres()),
                String.valueOf(request.fechaPrimerPago()),
                Objects.toString(request.motivo(), "").strip(),
                plano(request.saldoEsperado()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }

    private static String plano(BigDecimal valor) {
        return valor == null ? "" : valor.stripTrailingZeros().toPlainString();
    }
}
