-- Baja lógica de clientes (docs/API-CLIENTES.md del front): los clientes nunca se borran, se marcan
-- INACTIVO para conservar su historial de préstamos y pagos.
-- Solo agrega una columna; todos los clientes que ya existen quedan ACTIVO.

ALTER TABLE cliente
  ADD COLUMN estatus_cliente VARCHAR(10) NOT NULL DEFAULT 'ACTIVO' COMMENT 'ACTIVO o INACTIVO.';
