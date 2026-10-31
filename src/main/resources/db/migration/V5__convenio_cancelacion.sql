-- Cancelación y consulta de convenios (acordado el 2026-10-30). No cambia columnas.
--
-- estado_pago = 5 (cancelada): cuota del préstamo de un convenio cancelado. No se borra (queda la
-- trazabilidad), no se cobra y cuenta como saldo 0. Solo se cancela un convenio cuyo préstamo no
-- tiene pagos; al cancelarlo, las cuotas de los préstamos de origen pasan de 4 a 1 o 2 según sus
-- abonos y el convenio queda CANCELADO con quién, cuándo y por qué.

-- Lista de convenios: filtra por estatus y ordena por fecha de registro.
CREATE INDEX ix_convenio_estatus_fecha ON convenio (estatus, fecha_registro);

-- Las validaciones "¿el préstamo está en convenio / cancelado?" y los bloqueos por préstamo y
-- estado buscan por estas dos columnas.
CREATE INDEX ix_cuota_prestamo_estado ON estado_prestamos (id_calculadora_prestamo, estado_pago);
