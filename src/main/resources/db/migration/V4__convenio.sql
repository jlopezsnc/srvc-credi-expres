-- Convenios (acordado el 2026-10-30): varios préstamos con saldo, vigentes o vencidos y de uno o
-- varios clientes, se juntan en un préstamo nuevo a nombre del titular. Ese préstamo nuevo vive en
-- `calculadora_prestamos` y `estado_prestamos` como cualquier otro, así Cobranza, recibos y reportes
-- lo manejan sin cambios. Las cuotas con saldo de los préstamos de origen pasan a
-- `estado_pago = 4` (en convenio): dejan de cobrarse y su saldo cuenta como 0 en la cartera.
-- Las tablas existentes no cambian de estructura.

CREATE TABLE convenio (
  id_convenio           INT           NOT NULL AUTO_INCREMENT PRIMARY KEY,
  folio                 VARCHAR(12)   NULL COMMENT 'CV-000124. Se asigna con el id al guardar.',
  llave_idempotencia    CHAR(36)      NOT NULL COMMENT 'Encabezado Idempotency-Key (UUID) que envía el front.',
  hash_peticion         CHAR(64)      NOT NULL COMMENT 'SHA-256 de la petición normalizada: detecta una llave reutilizada con otros datos.',
  id_cliente_titular    INT           NOT NULL COMMENT 'Quien firma y paga el convenio.',
  id_prestamo_convenio  INT           NOT NULL COMMENT 'Préstamo nuevo con el calendario del convenio.',
  id_modalidad          INT           NOT NULL,
  numero_pagos          INT           NOT NULL,
  fecha_primer_pago     DATE          NOT NULL,
  saldo_convenido       DECIMAL(12,2) NOT NULL COMMENT 'Suma de los saldos de los préstamos de origen al convenir.',
  tasa_interes          DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT 'Interés mensual; 0 salvo casos excepcionales.',
  interes               DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  total_pagar           DECIMAL(12,2) NOT NULL,
  importe_cuota         DECIMAL(12,2) NOT NULL,
  motivo                VARCHAR(250)  NOT NULL,
  estatus               VARCHAR(12)   NOT NULL DEFAULT 'VIGENTE' COMMENT 'VIGENTE | CANCELADO',
  -- Bitácora
  registrado_por        VARCHAR(128)  NOT NULL COMMENT 'Correo (o uid) del token de Firebase.',
  fecha_registro        DATETIME(3)   NOT NULL COMMENT 'En UTC.',
  ip_origen             VARCHAR(45)   NULL COMMENT 'IP desde la que se generó (IPv4 o IPv6).',
  actualizado_por       VARCHAR(128)  NULL,
  fecha_actualizacion   DATETIME(3)   NULL COMMENT 'En UTC.',
  cancelado_por         VARCHAR(128)  NULL,
  fecha_cancelacion     DATETIME(3)   NULL COMMENT 'En UTC.',
  motivo_cancelacion    VARCHAR(250)  NULL,
  version               INT           NOT NULL DEFAULT 0 COMMENT 'Bloqueo optimista: dos cambios a la vez no se pisan.',
  CONSTRAINT fk_convenio_titular   FOREIGN KEY (id_cliente_titular)   REFERENCES cliente (id_cliente),
  CONSTRAINT fk_convenio_prestamo  FOREIGN KEY (id_prestamo_convenio) REFERENCES calculadora_prestamos (id_calculadora_prestamo),
  CONSTRAINT fk_convenio_modalidad FOREIGN KEY (id_modalidad)         REFERENCES modalidad (id_modalidad),
  CONSTRAINT ck_convenio_pagos     CHECK (numero_pagos BETWEEN 1 AND 52),
  CONSTRAINT ck_convenio_tasa      CHECK (tasa_interes BETWEEN 0 AND 100),
  CONSTRAINT ck_convenio_saldo     CHECK (saldo_convenido > 0),
  CONSTRAINT uq_convenio_llave     UNIQUE (llave_idempotencia),
  CONSTRAINT uq_convenio_folio     UNIQUE (folio),
  CONSTRAINT uq_convenio_prestamo  UNIQUE (id_prestamo_convenio),
  INDEX ix_convenio_titular (id_cliente_titular),
  INDEX ix_convenio_fecha_registro (fecha_registro)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Cada préstamo de origen con cómo estaba al convenir (trazabilidad: qué se convino y por cuánto).
CREATE TABLE convenio_prestamo (
  id_convenio_prestamo  INT           NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_convenio           INT           NOT NULL,
  id_prestamo           INT           NOT NULL,
  id_cliente            INT           NOT NULL COMMENT 'A nombre de quién estaba el préstamo (puede no ser el titular).',
  situacion_anterior    VARCHAR(12)   NOT NULL COMMENT 'AL_CORRIENTE | CON_ATRASO al momento de convenir.',
  cuotas_convenidas     INT           NOT NULL COMMENT 'Cuotas con saldo que pasaron a estado_pago = 4.',
  cuotas_vencidas       INT           NOT NULL,
  abonado_convenido     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Abonos parciales que ya tenían esas cuotas.',
  saldo_convenido       DECIMAL(12,2) NOT NULL,
  estatus               VARCHAR(12)   NOT NULL DEFAULT 'VIGENTE' COMMENT 'VIGENTE | CANCELADO (sigue al convenio).',
  registrado_por        VARCHAR(128)  NOT NULL,
  fecha_registro        DATETIME(3)   NOT NULL COMMENT 'En UTC.',
  CONSTRAINT fk_convenio_prestamo_convenio FOREIGN KEY (id_convenio) REFERENCES convenio (id_convenio),
  CONSTRAINT fk_convenio_prestamo_origen   FOREIGN KEY (id_prestamo) REFERENCES calculadora_prestamos (id_calculadora_prestamo),
  CONSTRAINT fk_convenio_prestamo_cliente  FOREIGN KEY (id_cliente)  REFERENCES cliente (id_cliente),
  CONSTRAINT ck_convenio_prestamo_saldo    CHECK (saldo_convenido > 0),
  CONSTRAINT uq_convenio_prestamo UNIQUE (id_convenio, id_prestamo),
  INDEX ix_convenio_prestamo_origen (id_prestamo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
