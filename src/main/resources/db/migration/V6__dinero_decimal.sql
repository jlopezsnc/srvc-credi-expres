-- Dinero en DECIMAL (autorizado el 2026-10-30). Las columnas FLOAT guardaban aproximaciones
-- (788.57 quedaba como 788.570007…) y el back tenía que redondear al leer. DECIMAL(12,2) guarda los
-- centavos exactos; al convertir, MySQL redondea cada valor a 2 decimales. numero_cuota pasa a INT
-- (siempre fue un entero). Los comentarios se repiten porque MODIFY los reemplaza.

ALTER TABLE calculadora_prestamos
  MODIFY importe_credito  DECIMAL(12,2) NULL     COMMENT 'IMPORTE CAPITAL DEL CREDITO SOLICITADO.',
  MODIFY tasa_interes     DECIMAL(6,2)  NOT NULL COMMENT 'INTERES MENSUAL EN PORCENTAJE (12 = 12% POR MES); 0 EN UN CONVENIO SIN INTERES.',
  MODIFY importe_cuota    DECIMAL(12,2) NOT NULL COMMENT 'IMPORTE DE CADA CUOTA; LA ULTIMA PUEDE AJUSTAR CENTAVOS.',
  MODIFY total_pagar      DECIMAL(12,2) NULL     COMMENT 'TOTAL A PAGAR CAPITAL + EL INTERES ACUMULADO.',
  MODIFY interes_generado DECIMAL(12,2) NULL     COMMENT 'TOTAL DE INTERES GENERADO EN EL PRESTAMO.';

ALTER TABLE estado_prestamos
  MODIFY numero_cuota  INT           NULL COMMENT 'NUMERO DE LA CUOTA: 1, 2, 3...',
  MODIFY importe_cuota DECIMAL(12,2) NULL COMMENT 'IMPORTE A PAGAR EN ESTA CUOTA.';
