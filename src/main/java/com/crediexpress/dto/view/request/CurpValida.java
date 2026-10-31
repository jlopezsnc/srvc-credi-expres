package com.crediexpress.dto.view.request;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

/**
 * CURP con la estructura oficial de RENAPO y su dígito verificador. Un valor nulo o vacío pasa:
 * la obligatoriedad la decide {@code @NotBlank}. Es la misma regla que aplica el front
 * ({@code src/app/core/validation/curp.ts}).
 */
@Documented
@Constraint(validatedBy = CurpValida.Validador.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CurpValida {

    String message() default "La CURP no es válida.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<CurpValida, String> {

        private static final Pattern ESTRUCTURA = Pattern.compile(
                "^[A-Z][AEIOUX][A-Z]{2}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])[HM]"
                        + "(AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS"
                        + "|TL|VZ|YN|ZS|NE)[B-DF-HJ-NP-TV-Z]{3}[A-Z\\d]\\d$");
        private static final String DICCIONARIO = "0123456789ABCDEFGHIJKLMNÑOPQRSTUVWXYZ";

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            return valor == null || valor.isBlank() || esValida(valor.strip().toUpperCase());
        }

        static boolean esValida(String curp) {
            if (!ESTRUCTURA.matcher(curp).matches()) {
                return false;
            }
            int suma = 0;
            for (int i = 0; i < 17; i++) {
                suma += DICCIONARIO.indexOf(curp.charAt(i)) * (18 - i);
            }
            return (10 - suma % 10) % 10 == curp.charAt(17) - '0';
        }
    }
}
