-- Saldo de caja (acordado el 2026-11-05): capital aportado (incluido el inicial) y retiros. El saldo
-- = aportado + cobrado (pagos VIGENTE) - prestado (importe_credito de préstamos que no nacen de un
-- convenio) - retirado. Lo cobrado y lo prestado salen de las tablas que ya existen; esta solo guarda
-- el dinero que entra o sale de caja sin ser un pago ni un préstamo. Nunca se borra: se cancela.
CREATE TABLE movimiento_caja (
  id_movimiento       INT           NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tipo                VARCHAR(10)   NOT NULL COMMENT 'APORTACION (capital que se pone) | RETIRO (dinero que se saca).',
  importe             DECIMAL(12,2) NOT NULL,
  fecha               DATE          NOT NULL COMMENT 'Día en que entró o salió el dinero.',
  concepto            VARCHAR(200)  NOT NULL,
  estatus             VARCHAR(12)   NOT NULL DEFAULT 'VIGENTE' COMMENT 'VIGENTE | CANCELADO',
  registrado_por      VARCHAR(128)  NOT NULL COMMENT 'Correo (o uid) del token de Firebase.',
  fecha_registro      DATETIME(3)   NOT NULL COMMENT 'En UTC.',
  cancelado_por       VARCHAR(128)  NULL,
  fecha_cancelacion   DATETIME(3)   NULL COMMENT 'En UTC.',
  motivo_cancelacion  VARCHAR(200)  NULL,
  CONSTRAINT ck_movimiento_caja_tipo    CHECK (tipo IN ('APORTACION', 'RETIRO')),
  CONSTRAINT ck_movimiento_caja_importe CHECK (importe > 0),
  CONSTRAINT ck_movimiento_caja_estatus CHECK (estatus IN ('VIGENTE', 'CANCELADO')),
  INDEX ix_movimiento_caja_fecha (fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
