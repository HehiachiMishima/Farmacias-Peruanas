-- ############################################################
-- Migración: rendimiento del panel Simulación (aplicar una vez sobre una base
-- creada con la versión anterior de panel.simulacion.sql).
--   1. Totales del Excel en archivos_excel: la lista de registros deja de
--      sumar todas las rutas de cada registro.
--   2. Columna generada «movida» + índice en rutas_proyeccion.
--   3. Totales de cada lanzamiento: el historial se lista sin contar cambios.
-- Nota: el paso 2 reconstruye rutas_proyeccion (ALTER con columna STORED);
-- en tablas grandes conviene correrlo fuera del horario de uso.
-- ############################################################

ALTER TABLE archivos_excel
    ADD COLUMN total_rutas INT NOT NULL DEFAULT 0 COMMENT 'Totales del Excel (no cambian al balancear): la lista los lee sin sumar las rutas.' AFTER estado,
    ADD COLUMN total_chutes INT NOT NULL DEFAULT 0 AFTER total_rutas,
    ADD COLUMN total_bultos DECIMAL(14, 2) NOT NULL DEFAULT 0.00 AFTER total_chutes,
    ADD COLUMN total_unidades DECIMAL(14, 2) NOT NULL DEFAULT 0.00 AFTER total_bultos,
    ADD COLUMN total_lineas DECIMAL(14, 2) NOT NULL DEFAULT 0.00 AFTER total_unidades,
    ADD COLUMN total_cod INT NOT NULL DEFAULT 0 AFTER total_lineas;

UPDATE archivos_excel a
  JOIN (SELECT archivo_id,
               COUNT(*) AS rutas,
               COUNT(DISTINCT chute_original_id) AS chutes,
               COALESCE(SUM(total_bultos), 0) AS bultos,
               COALESCE(SUM(total_unidades), 0) AS unidades,
               COALESCE(SUM(total_lineas), 0) AS lineas,
               COALESCE(SUM(conteo_cod), 0) AS cod
          FROM rutas_proyeccion
         GROUP BY archivo_id) r ON r.archivo_id = a.id
   SET a.total_rutas = r.rutas,
       a.total_chutes = r.chutes,
       a.total_bultos = r.bultos,
       a.total_unidades = r.unidades,
       a.total_lineas = r.lineas,
       a.total_cod = r.cod;

ALTER TABLE rutas_proyeccion
    ADD COLUMN movida TINYINT(1) AS (chute_id <> chute_original_id) STORED COMMENT 'La ruta está hoy en otro chute que al cargar el Excel.' AFTER conteo_cod,
    ADD INDEX idx_rutas_archivo_movida (archivo_id, movida, cluster_id);

ALTER TABLE lanzamientos_balanceo
    ADD COLUMN rutas INT NOT NULL DEFAULT 0 COMMENT 'Rutas movidas (filas del historial).' AFTER mensaje,
    ADD COLUMN separaciones INT NOT NULL DEFAULT 0 COMMENT 'De esas, las movidas para separar CL1 y CL2.' AFTER rutas,
    ADD COLUMN chutes_operarios INT NOT NULL DEFAULT 0 COMMENT 'Chutes cuya dotación cambió (paso 2).' AFTER separaciones;

UPDATE lanzamientos_balanceo l
  JOIN (SELECT lanzamiento_id,
               SUM(tipo_accion = 'MOVER_RUTA') AS rutas,
               SUM(tipo_accion = 'MOVER_RUTA' AND motivo = 'SEPARACION') AS separaciones,
               SUM(tipo_accion = 'ASIGNAR_OPERARIOS') AS chutes
          FROM historial_balanceo
         WHERE lanzamiento_id IS NOT NULL
         GROUP BY lanzamiento_id) h ON h.lanzamiento_id = l.id
   SET l.rutas = h.rutas,
       l.separaciones = h.separaciones,
       l.chutes_operarios = h.chutes;
