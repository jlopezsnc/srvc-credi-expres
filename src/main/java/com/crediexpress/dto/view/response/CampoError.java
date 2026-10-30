package com.crediexpress.dto.view.response;

/** Error de validación de un campo, con un mensaje que el front muestra tal cual. */
public record CampoError(String campo, String mensaje) {
}
