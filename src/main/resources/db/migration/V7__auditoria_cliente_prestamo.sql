-- Auditoría sin roles (docs §1.4): quién dio de alta y quién editó por última vez a cada cliente, y
-- quién generó cada préstamo. Mismo formato que pago y convenio: el correo del token de Firebase o,
-- si no trae correo, su uid.
-- Solo agrega columnas que aceptan NULL: no toca datos. Los registros anteriores quedan en NULL
-- (se capturaron antes de la auditoría).

ALTER TABLE cliente
  ADD COLUMN registrado_por      VARCHAR(128) NULL COMMENT 'Correo (o uid) del token de Firebase.',
  ADD COLUMN actualizado_por     VARCHAR(128) NULL COMMENT 'Último usuario que editó los datos.',
  ADD COLUMN fecha_actualizacion DATETIME(3)  NULL COMMENT 'En UTC.';

ALTER TABLE calculadora_prestamos
  ADD COLUMN registrado_por VARCHAR(128) NULL COMMENT 'Correo (o uid) del token de Firebase.';
