-- Modalidades que usa la pantalla "Nuevo préstamo": 1 = semanal (cada 7 días), 2 = quincenal
-- (cada 15 días). La base solo tenía la semanal, así que un préstamo quincenal fallaba.
-- INSERT IGNORE: no toca la semanal que ya existe; en una base nueva crea las dos.
INSERT IGNORE INTO modalidad (id_modalidad, descripcion, dias_activos) VALUES
  (1, 'Semanal', 7),
  (2, 'Quincenal', 15);
