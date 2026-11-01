-- ============================================================
-- Migración del panel Simulación: tope global de operarios
-- y reasignación de operarios entre chutes
--
-- Aplica SOLO sobre una base ya existente (instalacion.sql crea el
-- esquema nuevo desde cero). Ejecutar una vez:
--   mysql -u <usuario> -p farmacias_peruanas < migracion_simulacion_operarios.sql
--
-- Cambios:
--   1. simulacion_config.operarios_maximos: tope global de operarios
--      disponibles por registro. Se rellena con la suma de operarios
--      ya asignados para no cambiar el comportamiento de registros
--      existentes.
--   2. historial_balanceo.tipo_accion admite MOVER_OPERARIO: un
--      operario pasa del chute_origen_id al chute_destino_id.
-- ============================================================
USE farmacias_peruanas;

-- ------------------------------------------------------------
-- 1) Tope global de operarios
-- ------------------------------------------------------------
ALTER TABLE simulacion_config
    ADD COLUMN operarios_maximos INT NULL
        COMMENT 'Tope global de operarios disponibles; NULL = sin tope global.'
        AFTER velocidad_base;

UPDATE simulacion_config c
   SET c.operarios_maximos = (
       SELECT COALESCE(SUM(p.operarios), 0)
         FROM parametros_chutes p
        WHERE p.archivo_id = c.archivo_id
   );

-- ------------------------------------------------------------
-- 2) Reasignación de operarios en el historial
-- ------------------------------------------------------------
ALTER TABLE historial_balanceo
    MODIFY COLUMN tipo_accion ENUM(
        'MOVER_RUTA',
        'AÑADIR_OPERARIO',
        'MOVER_OPERARIO'
    ) NOT NULL;
