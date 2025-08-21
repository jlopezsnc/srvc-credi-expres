package com.crediexpress.dto.view.response;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter
public class ClienteResponse {
    private Integer idCliente;
    private String nombreCompleto;
    private String curp;
    private String domicilioCalle;
    private String domicilioColonia;
    private String domicilioCiudad;
    private String telefono;
    private String correoElectronico;
}
