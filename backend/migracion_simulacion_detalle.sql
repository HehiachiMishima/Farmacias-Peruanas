-- ============================================================
-- Migración del panel Simulación: tope de operarios por chute,
-- operarios originales e historial detallado
--
-- Aplica SOLO sobre una base ya existente (panel.simulacion.sql ya crea
-- estas columnas desde cero). Requiere haber aplicado antes
-- migracion_simulacion_operarios.sql y migracion_simulacion_tipo.sql.
-- Ejecutar una vez:
--   mysql -u <usuario> -p farmacias_peruanas < migracion_simulacion_detalle.sql
--
-- Cambios:
--   1. simulacion_config.operarios_por_chute: máximo de operarios en un
--      chute (opcional; NULL = reparto parejo del máximo global).
--   2. parametros_chutes.operarios_original: operarios fijados por el
--      usuario. Se reconstruyen restando lo que añadió o reasignó el
--      balanceo según el historial.
--   3. historial_balanceo: motivo y operarios después de cada acción;
--      índice por (archivo_id, id), que es el orden en que se lee.
--   4. archivos_excel.estado pasa a NOT NULL.
--   5. rutas_proyeccion: se quitan índices que ninguna consulta usa.
-- ============================================================
USE farmacias_peruanas;

-- ------------------------------------------------------------
-- 1) Tope de operarios por chute
-- ------------------------------------------------------------
ALTER TABLE simulacion_config
    ADD COLUMN operarios_por_chute INT NULL
        COMMENT 'Máximo de operarios en un chute; NULL = reparto parejo (máximo global ÷ chutes).'
        AFTER operarios_maximos;

-- ------------------------------------------------------------
-- 2) Operarios originales = actuales − neto del balanceo
-- ------------------------------------------------------------
ALTER TABLE parametros_chutes
    ADD COLUMN operarios_original INT NULL
        COMMENT 'Operarios fijados por el usuario: base del comparativo y de Restablecer.'
        AFTER operarios;

UPDATE parametros_chutes p
  LEFT JOIN (
        SELECT archivo_id, chute_id, SUM(delta) AS neto
          FROM (
                SELECT archivo_id, chute_modificado_id AS chute_id, 1 AS delta
                  FROM historial_balanceo
                 WHERE tipo_accion = 'AÑADIR_OPERARIO' AND chute_modificado_id IS NOT NULL
                UNION ALL
                SELECT archivo_id, chute_destino_id, 1
                  FROM historial_balanceo
                 WHERE tipo_accion = 'MOVER_OPERARIO' AND chute_destino_id IS NOT NULL
                UNION ALL
                SELECT archivo_id, chute_origen_id, -1
                  FROM historial_balanceo
                 WHERE tipo_accion = 'MOVER_OPERARIO' AND chute_origen_id IS NOT NULL
               ) d
         GROUP BY archivo_id, chute_id
       ) n ON n.archivo_id = p.archivo_id AND n.chute_id = p.chute_id
   SET p.operarios_original = GREATEST(1, p.operarios - COALESCE(n.neto, 0));

ALTER TABLE parametros_chutes
    MODIFY COLUMN operarios_original INT NOT NULL DEFAULT 1
        COMMENT 'Operarios fijados por el usuario: base del comparativo y de Restablecer.';

-- ------------------------------------------------------------
-- 3) Historial detallado
-- ------------------------------------------------------------
ALTER TABLE historial_balanceo
    ADD COLUMN motivo ENUM('BALANCEO', 'SEPARACION', 'MANUAL') NOT NULL DEFAULT 'BALANCEO'
        COMMENT 'BALANCEO: ajuste de tiempos; SEPARACION: CL1 y CL2 no comparten chute; MANUAL: movido por el usuario.'
        AFTER chute_modificado_id,
    ADD COLUMN operarios_origen INT NULL
        COMMENT 'Operarios del chute de origen después de la acción.'
        AFTER motivo,
    ADD COLUMN operarios_destino INT NULL
        COMMENT 'Operarios del chute de destino (o modificado) después de la acción.'
        AFTER operarios_origen,
    ADD INDEX idx_historial_archivo_id (archivo_id, id),
    DROP INDEX idx_historial_archivo_fecha,
    COMMENT = 'Movimientos de rutas y operarios hechos por el balanceo o a mano.';

-- ------------------------------------------------------------
-- 4) Estado obligatorio
-- ------------------------------------------------------------
UPDATE archivos_excel SET estado = 'PROYECCION' WHERE estado IS NULL;
ALTER TABLE archivos_excel
    MODIFY COLUMN estado ENUM('PROYECCION', 'BALANCEADO') NOT NULL DEFAULT 'PROYECCION'
        COMMENT 'Indica si los datos actuales son los originales o ya fueron modificados.';

-- ------------------------------------------------------------
-- 5) Índices sin uso
-- ------------------------------------------------------------
ALTER TABLE rutas_proyeccion
    DROP INDEX idx_ruta_codigo,
    DROP INDEX idx_ruta_bultos,
    DROP INDEX ft_rutas_busqueda;
