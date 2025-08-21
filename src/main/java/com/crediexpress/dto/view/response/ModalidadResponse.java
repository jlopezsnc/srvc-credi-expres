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
public class ModalidadResponse {
    private Integer idModalidad;
    private String descripcion;
    private String informacionAdicional;
}