package com.crediexpress.dto.view.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.http.HttpStatus;

import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter
public class GenericResponse {
    private Object objetoResponse;
    private HttpStatus httpStatus;
    private String mensaje;
    /** Solo en errores de validación (400): qué campo falló y por qué. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<CampoError> campos;

}
