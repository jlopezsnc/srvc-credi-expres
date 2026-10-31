package com.crediexpress.dto.view.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos capturables del cliente para {@code PUT /cliente-actualizar/{idCliente}}. Los límites son
 * los de las columnas de la tabla {@code cliente}. Los opcionales pueden venir vacíos ("") y se
 * guardan como NULL. No incluye el estatus: para eso están la baja y la reactivación.
 */
@MensajeValidacion(ActualizarClienteRequest.MENSAJE)
public record ActualizarClienteRequest(
        @NotBlank(message = "Escribe el nombre completo.")
        @Size(max = 100, message = "El nombre admite hasta 100 caracteres.")
        String nombreCompleto,

        @NotBlank(message = "Captura la CURP.")
        @CurpValida
        String curp,

        @Pattern(regexp = "^(\\d{10})?$", message = "El teléfono debe tener 10 dígitos.")
        String telefono,

        @Email(message = "El correo no es válido.")
        @Size(max = 40, message = "El correo admite hasta 40 caracteres.")
        String correoElectronico,

        @Size(max = 45, message = "La calle admite hasta 45 caracteres.")
        String domicilioCalle,

        @Size(max = 30, message = "La colonia admite hasta 30 caracteres.")
        String domicilioColonia,

        @Size(max = 45, message = "La ciudad admite hasta 45 caracteres.")
        String domicilioCiudad) {

    public static final String MENSAJE = "Revisa los datos del cliente.";
}
