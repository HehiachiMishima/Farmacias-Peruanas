-- Active: 1791152181635@@127.0.0.1@3306@farmacias_peruanas
-- ============================================================
-- Migración del panel Simulación: TIPO de ruta y chute original
-- Cambios:
--   1. parametros_chutes.operarios: DEFAULT 2 -> 1.
--   2. rutas_proyeccion.tipo: B2C/B2B. Las rutas existentes se
--      consideran B2C.
--   3. rutas_proyeccion.chute_original_id: chute congelado al cargar.
--      Para registros ya balanceados se recupera el origen del primer
--      MOVER_RUTA del historial; si no hay, se usa el chute actual.
--   4. UNIQUE (archivo_id, codigo_ruta, tipo) e índice por chute original.
-- ============================================================
USE farmacias_peruanas;

-- ------------------------------------------------------------
-- 1) Operarios por chute: el valor inicial pasa a 1
-- ------------------------------------------------------------
ALTER TABLE parametros_chutes
    MODIFY COLUMN operarios INT NOT NULL DEFAULT 1 COMMENT 'Cantidad de operarios asignados.';

-- ------------------------------------------------------------
-- 2) Columnas nuevas (primero nullable para poder rellenarlas)
-- ------------------------------------------------------------
ALTER TABLE rutas_proyeccion
    ADD COLUMN chute_original_id BIGINT NULL
        COMMENT 'Chute inicial al cargar el Excel; no lo cambia el balanceo.'
        AFTER chute_id,
    ADD COLUMN tipo VARCHAR(10) NULL
        COMMENT 'Tipo de ruta: B2C o B2B.'
        AFTER codigo_ruta;

-- Todas las rutas históricas se catalogan como B2C.
UPDATE rutas_proyeccion
   SET tipo = 'B2C'
 WHERE tipo IS NULL;

-- Chute original: el del primer MOVER_RUTA registrado para la ruta.
UPDATE rutas_proyeccion r
  JOIN (
        SELECT h.ruta_id, h.chute_origen_id
          FROM historial_balanceo h
          JOIN (
                SELECT ruta_id, MIN(id) AS primer_id
                  FROM historial_balanceo
                 WHERE tipo_accion = 'MOVER_RUTA'
                   AND ruta_id IS NOT NULL
                 GROUP BY ruta_id
               ) p ON p.primer_id = h.id
       ) m ON m.ruta_id = r.id
   SET r.chute_original_id = m.chute_origen_id
 WHERE r.chute_original_id IS NULL;

-- Sin historial de movimiento: el chute actual es el original.
UPDATE rutas_proyeccion
   SET chute_original_id = chute_id
 WHERE chute_original_id IS NULL;

-- ------------------------------------------------------------
-- 3) Restricciones definitivas
-- ------------------------------------------------------------
ALTER TABLE rutas_proyeccion
    MODIFY COLUMN chute_original_id BIGINT NOT NULL
        COMMENT 'Chute inicial al cargar el Excel; no lo cambia el balanceo.',
    MODIFY COLUMN tipo VARCHAR(10) NOT NULL
        COMMENT 'Tipo de ruta: B2C o B2B.';

ALTER TABLE rutas_proyeccion
    ADD CONSTRAINT fk_ruta_chute_original
        FOREIGN KEY (chute_original_id) REFERENCES chutes (id),
    ADD INDEX idx_rutas_archivo_chute_original (archivo_id, chute_original_id),
    ADD CONSTRAINT uq_rutas_archivo_ruta_tipo
        UNIQUE (archivo_id, codigo_ruta, tipo);
