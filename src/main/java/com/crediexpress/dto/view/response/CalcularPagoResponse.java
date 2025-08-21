package com.crediexpress.dto.view.response;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter
public class CalcularPagoResponse {
    private Integer plazosGenerados;
    private String resultado;
}
