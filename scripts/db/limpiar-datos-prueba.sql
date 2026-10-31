-- =====================================================================================
-- LIMPIEZA DE LOS DATOS DE PRUEBA (manual; NO es una migración de Flyway).
--
-- Borra clientes, préstamos, cuotas, pagos y convenios, y deja el catálogo de modalidades
-- (1 = Semanal, 2 = Quincenal). Se corre UNA vez, antes de cargar los datos del Excel.
-- No toca flyway_schema_history ni la estructura de las tablas.
--
-- Antes de correrlo:
--   1. Respaldo:  mysqldump -u <usuario> -p --single-transaction --routines <base> > respaldo.sql
--   2. Confirmar que la conexión es a la base de DESARROLLO (ver el SELECT de abajo).
--   3. Detener el back (que nadie esté capturando).
-- Uso:  mysql -u <usuario> -p <base> < scripts/db/limpiar-datos-prueba.sql
-- =====================================================================================

SELECT DATABASE() AS base_que_se_va_a_limpiar, NOW() AS fecha;

START TRANSACTION;

-- De los hijos a los padres, para respetar las llaves foráneas.
DELETE FROM convenio_prestamo;
DELETE FROM convenio;
DELETE FROM pago;
DELETE FROM estado_prestamos;
DELETE FROM calculadora_prestamos;
DELETE FROM cliente;

-- Catálogo: solo las modalidades que usa el sistema, con su descripción y días correctos.
DELETE FROM modalidad WHERE id_modalidad NOT IN (1, 2);
INSERT INTO modalidad (id_modalidad, descripcion, dias_activos) VALUES
  (1, 'Semanal', 7),
  (2, 'Quincenal', 15)
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion), dias_activos = VALUES(dias_activos);

COMMIT;

-- Los consecutivos vuelven a empezar (ALTER TABLE confirma por sí solo; va después del COMMIT).
ALTER TABLE cliente               AUTO_INCREMENT = 1;
ALTER TABLE calculadora_prestamos AUTO_INCREMENT = 1;
ALTER TABLE estado_prestamos      AUTO_INCREMENT = 1;
ALTER TABLE pago                  AUTO_INCREMENT = 1;
ALTER TABLE convenio              AUTO_INCREMENT = 1;
ALTER TABLE convenio_prestamo     AUTO_INCREMENT = 1;
ALTER TABLE modalidad             AUTO_INCREMENT = 3;

-- Comprobación: todo en 0 menos modalidad (2).
SELECT 'cliente' AS tabla, COUNT(*) AS registros FROM cliente
UNION ALL SELECT 'calculadora_prestamos', COUNT(*) FROM calculadora_prestamos
UNION ALL SELECT 'estado_prestamos', COUNT(*) FROM estado_prestamos
UNION ALL SELECT 'pago', COUNT(*) FROM pago
UNION ALL SELECT 'convenio', COUNT(*) FROM convenio
UNION ALL SELECT 'convenio_prestamo', COUNT(*) FROM convenio_prestamo
UNION ALL SELECT 'modalidad', COUNT(*) FROM modalidad;
