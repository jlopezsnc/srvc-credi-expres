-- Cobranza (opción B, acordada el 2026-10-30): una sola tabla nueva. Cada abono, pago completo o
-- renglón de una liquidación es un registro de `pago`; las tablas existentes no cambian de
-- estructura. El saldo de una cuota = importe_cuota − suma de sus pagos VIGENTE.
CREATE TABLE pago (
  id_pago             INT           NOT NULL AUTO_INCREMENT PRIMARY KEY,
  folio               VARCHAR(12)   NULL COMMENT 'P-000233. Lo comparten todos los renglones de una liquidación.',
  llave_idempotencia  CHAR(36)      NOT NULL COMMENT 'Encabezado Idempotency-Key (UUID) que envía el front.',
  hash_peticion       CHAR(64)      NOT NULL COMMENT 'SHA-256 de la petición normalizada: detecta una llave reutilizada con otros datos.',
  id_cuota            INT           NOT NULL,
  id_prestamo         INT           NOT NULL,
  id_cliente          INT           NOT NULL,
  importe             DECIMAL(12,2) NOT NULL COMMENT 'Lo que se aplica a la cuota (reduce su saldo).',
  recargo             DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Recargo por atraso, a criterio del cobrador. No reduce el saldo.',
  fecha_pago          DATE          NOT NULL COMMENT 'Cuándo pagó el cliente.',
  metodo_pago         VARCHAR(20)   NOT NULL COMMENT 'EFECTIVO | TRANSFERENCIA | DEPOSITO',
  referencia          VARCHAR(50)   NULL,
  nota                VARCHAR(200)  NULL,
  tipo                VARCHAR(20)   NOT NULL COMMENT 'CUOTA_COMPLETA | ABONO_PARCIAL | LIQUIDACION',
  saldo_cuota         DECIMAL(12,2) NOT NULL COMMENT 'Saldo de la cuota justo después de este pago.',
  prestamo_liquidado  BOOLEAN       NOT NULL DEFAULT FALSE,
  registrado_por      VARCHAR(128)  NOT NULL COMMENT 'Correo (o uid) del token de Firebase.',
  fecha_registro      DATETIME(3)   NOT NULL COMMENT 'En UTC.',
  estatus             VARCHAR(12)   NOT NULL DEFAULT 'VIGENTE' COMMENT 'VIGENTE | CANCELADO',
  cancelado_por       VARCHAR(128)  NULL,
  fecha_cancelacion   DATETIME(3)   NULL COMMENT 'En UTC.',
  motivo_cancelacion  VARCHAR(200)  NULL,
  CONSTRAINT fk_pago_cuota    FOREIGN KEY (id_cuota)    REFERENCES estado_prestamos (id),
  CONSTRAINT fk_pago_prestamo FOREIGN KEY (id_prestamo) REFERENCES calculadora_prestamos (id_calculadora_prestamo),
  CONSTRAINT fk_pago_cliente  FOREIGN KEY (id_cliente)  REFERENCES cliente (id_cliente),
  CONSTRAINT ck_pago_importe  CHECK (importe > 0),
  CONSTRAINT ck_pago_recargo  CHECK (recargo >= 0),
  CONSTRAINT uq_pago_llave_cuota UNIQUE (llave_idempotencia, id_cuota),
  INDEX ix_pago_folio (folio),
  INDEX ix_pago_fecha_registro (fecha_registro),
  INDEX ix_pago_cuota_estatus (id_cuota, estatus)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Índice nuevo (no altera columnas): las consultas de cobranza filtran por fecha de vencimiento.
CREATE INDEX ix_cuota_vencimiento ON estado_prestamos (fecha_vencimiento);

-- Cuotas que quedaron sin estado y sin fecha de pago → pendientes (1). Las demás no se tocan.
UPDATE estado_prestamos SET estado_pago = 1 WHERE estado_pago IS NULL AND fecha_pago IS NULL;
