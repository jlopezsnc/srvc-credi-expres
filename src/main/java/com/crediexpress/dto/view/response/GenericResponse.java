package com.crediexpress.dto.view.response;


import lombok.*;
import org.springframework.http.HttpStatus;

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

}
