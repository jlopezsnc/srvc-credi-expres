package com.crediexpress.dto.view.request;

import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.NonNull;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter
public class ClienteRequest {

    private Integer idCliente;
    private String nombreCompleto;
    private String curp;
    private String domicilioCalle;
    private String domicilioColonia;
    private String domicilioCiudad;
    private String telefono;
    private String correoElectronico;
}
