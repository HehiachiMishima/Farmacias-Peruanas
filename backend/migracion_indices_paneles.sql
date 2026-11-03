-- ############################################################
-- Migración: índices de los paneles Bitácora e Inbound (aplicar sobre una
-- base creada con la versión anterior de panel.bitacora.sql y
-- panel.inbound.sql). Es idempotente: se puede correr más de una vez.
--
--   1. Nuevos:
--      - incidencias.idx_incidencias_analitica: índice cubriente de los
--        agregados del dashboard por rango de fechas (KPIs, rankings por
--        sistema/centro/área/tipo y la matriz centro x sistema) para que
--        MySQL no lea la fila completa (ticket + descripción) de cada
--        incidencia del rango.
--      - data_responsable.idx_data_responsable_responsable: el backend
--        valida que el nombre del responsable sea único en todas las áreas
--        (WHERE responsable = ?); el único existente empieza por id_data_area
--        y no servía.
--   2. Quitados (sin uso o redundantes; cada uno cuesta en cada escritura):
--      - FULLTEXT de los catálogos: ninguna consulta usa MATCH ... AGAINST
--        (las búsquedas son LIKE '%texto%').
--      - *_actualizado_en: nadie filtra ni ordena por esa columna.
--      - idx_data_incidencia_sistema_creado_en e
--        idx_data_responsable_area_creado_en: su primera columna ya la
--        cubren los únicos (id_data_sistema, incidencia) e
--        (id_data_area, responsable), que también sostienen las FK.
--      - idx_incidencias_hora_fin_creado_en: ninguna consulta filtra por
--        hora_fin.
--
-- Los ALTER de índices secundarios son in-place (sin bloquear escrituras).
-- ############################################################

USE farmacias_peruanas;

DROP PROCEDURE IF EXISTS tmp_quitar_indice;
DROP PROCEDURE IF EXISTS tmp_crear_indice;

DELIMITER $$

-- Quita el índice solo si existe (MySQL no tiene DROP INDEX IF EXISTS).
CREATE PROCEDURE tmp_quitar_indice(IN p_tabla VARCHAR(64), IN p_indice VARCHAR(64))
BEGIN
    IF EXISTS (SELECT 1
                 FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = p_tabla
                  AND index_name = p_indice) THEN
        SET @sql_indice = CONCAT('ALTER TABLE `', p_tabla, '` DROP INDEX `', p_indice, '`');
        PREPARE sentencia FROM @sql_indice;
        EXECUTE sentencia;
        DEALLOCATE PREPARE sentencia;
    END IF;
END$$

-- Crea el índice solo si no existe (p_definicion: columnas y COMMENT).
CREATE PROCEDURE tmp_crear_indice(IN p_tabla VARCHAR(64), IN p_indice VARCHAR(64), IN p_definicion VARCHAR(1000))
BEGIN
    IF NOT EXISTS (SELECT 1
                     FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = p_tabla
                      AND index_name = p_indice) THEN
        SET @sql_indice = CONCAT('ALTER TABLE `', p_tabla, '` ADD INDEX `', p_indice, '` ', p_definicion);
        PREPARE sentencia FROM @sql_indice;
        EXECUTE sentencia;
        DEALLOCATE PREPARE sentencia;
    END IF;
END$$

DELIMITER ;

-- ------------------------------------------------------------
-- 1. Índices nuevos
-- ------------------------------------------------------------
CALL tmp_crear_indice('incidencias', 'idx_incidencias_analitica',
    '(creado_en, id_data_centro, id_data_sistema, id_data_incidencia, id_data_area, id_data_responsable, tiempo_solucion_seg) COMMENT ''Cubre los agregados del dashboard por rango de fechas sin leer la fila completa.''');

CALL tmp_crear_indice('data_responsable', 'idx_data_responsable_responsable',
    '(responsable) COMMENT ''Unicidad global del nombre que valida el backend (WHERE responsable = ?).''');

-- ------------------------------------------------------------
-- 2. Índices sin uso o redundantes
-- ------------------------------------------------------------
-- Bitácora: catálogos
CALL tmp_quitar_indice('data_centro', 'ft_data_centro_centro');
CALL tmp_quitar_indice('data_centro', 'idx_data_centro_actualizado_en');
CALL tmp_quitar_indice('data_sistema', 'ft_data_sistema_sistema');
CALL tmp_quitar_indice('data_sistema', 'idx_data_sistema_actualizado_en');
CALL tmp_quitar_indice('data_area', 'ft_data_area_area');
CALL tmp_quitar_indice('data_area', 'idx_data_area_actualizado_en');
CALL tmp_quitar_indice('data_responsable', 'ft_data_responsable_responsable');
CALL tmp_quitar_indice('data_responsable', 'idx_data_responsable_actualizado_en');
CALL tmp_quitar_indice('data_responsable', 'idx_data_responsable_area_creado_en');
CALL tmp_quitar_indice('data_incidencia', 'ft_data_incidencia_incidencia');
CALL tmp_quitar_indice('data_incidencia', 'idx_data_incidencia_actualizado_en');
CALL tmp_quitar_indice('data_incidencia', 'idx_data_incidencia_sistema_creado_en');

-- Bitácora: incidencias
CALL tmp_quitar_indice('incidencias', 'idx_incidencias_hora_fin_creado_en');

-- Inbound
CALL tmp_quitar_indice('data_proveedor', 'ft_data_proveedor_proveedor');
CALL tmp_quitar_indice('data_proveedor', 'idx_data_proveedor_actualizado_en');
CALL tmp_quitar_indice('data_incidencia_inbound', 'ft_data_incidencia_inbound_incidencia');
CALL tmp_quitar_indice('data_incidencia_inbound', 'idx_data_incidencia_inbound_actualizado_en');
CALL tmp_quitar_indice('incidencias_inbound', 'idx_incidencias_inbound_actualizado_en');

DROP PROCEDURE IF EXISTS tmp_quitar_indice;
DROP PROCEDURE IF EXISTS tmp_crear_indice;

-- Estadísticas frescas para que el optimizador considere los cambios.
ANALYZE TABLE incidencias, data_responsable, data_incidencia, incidencias_inbound;
