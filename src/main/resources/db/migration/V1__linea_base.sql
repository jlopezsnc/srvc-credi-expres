-- Línea base: el esquema tal como estaba en MySQL el 2026-10-30 (mysqldump --no-data).
-- En una base que ya tiene estas tablas, Flyway NO ejecuta este script: la marca como versión 1
-- (spring.flyway.baseline-on-migrate=true). Solo corre en una base vacía (nuevo ambiente o pruebas).
-- No modificar: cualquier cambio va en un script nuevo (V2, V3, …).

CREATE TABLE `cliente` (
  `id_cliente` int NOT NULL AUTO_INCREMENT COMMENT 'IDENTIFICADOR AUTOINCREMENTABLE DE LA TABLA.',
  `nombre_completo` varchar(100) NOT NULL COMMENT 'NOMBRE COMPLETO DEL CLIENTE.',
  `curp` varchar(20) DEFAULT NULL COMMENT 'CURP DEL CLIENTE.',
  `domicilio_calle` varchar(45) DEFAULT NULL COMMENT 'NOMBRE DE LA CALLE DEL DOMICILIO DEL CLIENTE.',
  `domicilio_colonia` varchar(30) DEFAULT NULL COMMENT 'NOMBRE DE LA COLONIA DEL DOMICILIO DEL CLIENTE.',
  `domicilio_ciudad` varchar(45) DEFAULT NULL COMMENT 'NOMBRE DE LA CIUDAD DEL DOMICILIO DEL CLIENTE.',
  `telefono` varchar(10) DEFAULT NULL COMMENT 'NUMERO DE TELEFONO DEL CLIENTE.',
  `correo_electronico` varchar(40) DEFAULT NULL COMMENT 'CORREO ELECTRONICO DEL CLIENTE.',
  `fecha_alta` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'FECHA DE ALTA DEL CLIENTE.',
  PRIMARY KEY (`id_cliente`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `modalidad` (
  `id_modalidad` int NOT NULL AUTO_INCREMENT COMMENT 'IDENTIFICADOR AUTOINCREMENTABLE DE LA TABLA.',
  `descripcion` varchar(20) NOT NULL COMMENT 'MODO DE PAGO SEMANAL, QUINCENAL, MENSUAL O DIARIO.',
  `dias_activos` int NOT NULL COMMENT 'DIAS PERMITIDOS EN LA MODALIDAD.',
  `informacion_adicional` varchar(45) DEFAULT NULL COMMENT 'ALGUN DATO ADICIONAL QUE SE REQUIERA GUARDAR.',
  PRIMARY KEY (`id_modalidad`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `calculadora_prestamos` (
  `id_calculadora_prestamo` int NOT NULL AUTO_INCREMENT COMMENT 'IDENTIFICADOR AUTOINCREMENTABLE DE LA TABLA.',
  `id_cliente` int DEFAULT NULL COMMENT 'ID DEL CLIENTE.',
  `importe_credito` float DEFAULT NULL COMMENT 'IMPORTE CAPITAL DEL CREDITO SOLICITADO.',
  `id_modalidad` int DEFAULT NULL COMMENT 'IDENTIFICADOR DEL MODO DE PAGO -DIARIO, SEMANAL, QUINCENAL O MENSUAL.',
  `tasa_interes` float NOT NULL COMMENT 'TASA DE INTERES APLICADA AL PRESTAMO.',
  `numero_cuotas` int NOT NULL COMMENT 'NUMERO DE CUOTAS QUE SERA PAGADO EL PRESTAMO.',
  `importe_cuota` float NOT NULL COMMENT 'IMPORTE DE CADA CUOTA QUE APLICARÁ EL PAGO.',
  `total_pagar` float DEFAULT NULL COMMENT 'TOTAL A PAGAR CAPITAL + EL INTERES ACUMULADO.',
  `fecha_solicitud` date DEFAULT NULL COMMENT 'FECHA QUE SE CAPTURA LA SOLICITUD, PARAMETRO ENVIADO DESDE EL FRONT',
  `fecha_inicio_credito` date DEFAULT NULL COMMENT 'FECHA QUE SE VA INICIAR EL CALCULO DE LOS PAGOS, PARAMETRO ENVIADO DESDE EL FRONT',
  `fecha` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'FECHA QUE SE ESTA GUARDANDO EL REGISTRO.',
  `interes_generado` float DEFAULT NULL COMMENT 'TOTAL DE INTERES GENERADO EN EL PRESTAMO.',
  PRIMARY KEY (`id_calculadora_prestamo`),
  KEY `id_cliente_idx` (`id_cliente`),
  KEY `id_modalidad_idx` (`id_modalidad`),
  CONSTRAINT `id_cliente` FOREIGN KEY (`id_cliente`) REFERENCES `cliente` (`id_cliente`),
  CONSTRAINT `id_modalidad` FOREIGN KEY (`id_modalidad`) REFERENCES `modalidad` (`id_modalidad`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `estado_prestamos` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'IDENTIFICADOR AUTOINCREMENTABLE DE LA TABLA.',
  `id_cliente` int DEFAULT NULL COMMENT 'ID DEL CLIENTE.',
  `numero_cuota` float DEFAULT NULL COMMENT 'IDENTIFICADOR DE LA CUOTA, COMIENZA EN 1 Y LOS SIGUIENTES SON N+1.',
  `fecha_vencimiento` date DEFAULT NULL COMMENT 'FECHA LIMITE DE PAGO DEL PLAZO.',
  `importe_cuota` float DEFAULT NULL COMMENT 'IMPORTE A PAGAR EN ESTA CUOTA.',
  `fecha_pago` date DEFAULT NULL COMMENT 'FECHA QUE DEBE REALIZARSE EL PAGO, ESTA FECHA SE CALCULA DE ACUERDO AL PLAZO Y EL NUMERO DE DIAS QUE TIENE EL PLAZO (SI ES UNA SEMANA, LA FECHA DE PAGO INCREMENTE +7 DIAS EN CADA REGISTRO).',
  `estado_pago` int DEFAULT NULL COMMENT 'INDICA SI EL PAGO ESTA PENDIENTE, VENCIDO O PAGADO.',
  `dias_moratorios` int DEFAULT NULL COMMENT 'INDICA SI SE PAGO EN LA FECHA ESTABLECIDA O SE PAGO CON RETARDO.',
  `alerta` varchar(45) DEFAULT NULL COMMENT 'INDICADOR DE SEMAFORO DEL ESTADO DE PAGOS DEL CLIENTE.',
  `id_calculadora_prestamo` int DEFAULT NULL COMMENT 'IDENTIFICADO DE LA TABLA PRINCIPAL calculadora_prestamos.',
  PRIMARY KEY (`id`),
  KEY `id_calculadora_prestamo_idx` (`id_calculadora_prestamo`),
  KEY `cliente_idx` (`id_cliente`),
  CONSTRAINT `cliente` FOREIGN KEY (`id_cliente`) REFERENCES `cliente` (`id_cliente`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
