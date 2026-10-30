-- Active: 1791152181635@@127.0.0.1@3306@farmacias_peruanas
-- ============================================================
-- DATOS DE BITÁCORA (base nueva)
-- ============================================================
-- Catálogos e incidencias de ejemplo con ids explícitos, para que los
-- ids coincidan en cualquier base nueva. Un INSERT por tabla.
--
-- ORDEN: después de instalacion.sql y de arrancar el backend una vez
-- (crea el usuario semilla id = 1; incidencias tiene FK a usuarios).
--
--   mysql -h <host> -P <puerto> -u <usuario> -p farmacias_peruanas < backend/data.sql
--
-- Fechas: creado_en / actualizado_en en UTC (hora de Lima + 5 h);
-- hora_inicio / hora_fin en hora de Lima; tiempo_solucion_seg en segundos.
-- Los nombres de los centros 1-3 son provisionales: renómbralos desde
-- Control > Centros.
-- ============================================================

SET NAMES utf8mb4;

-- Todo o nada: si algo falla (p. ej. aún no existe el usuario id = 1),
-- no queda nada insertado.
START TRANSACTION;

-- Centros
INSERT INTO data_centro (id, centro, creado_por, actualizado_por)
VALUES
    (1, 'CENTRO 1', 1, 1),
    (2, 'CENTRO 2', 1, 1),
    (3, 'CENTRO 3', 1, 1);

-- Sistemas
INSERT INTO data_sistema (id, sistema, creado_por, actualizado_por)
VALUES
    (1, 'IR-TRACK', 1, 1),
    (2, 'KISOFT', 1, 1),
    (3, 'PMM', 1, 1),
    (4, 'SGT', 1, 1),
    (5, 'AMR', 1, 1),
    (6, 'AFRAME', 1, 1),
    (7, 'PSYS', 1, 1),
    (8, 'IP6', 1, 1),
    (9, 'WMS', 1, 1);

-- Tipos de incidencia (cada tipo pertenece a un sistema)
INSERT INTO data_incidencia (id, id_data_sistema, incidencia, creado_por, actualizado_por)
VALUES
    (1, 1, 'Error en Liberación', 1, 1),
    (2, 1, 'Falla en interfaz', 1, 1),
    (3, 1, 'Interfaz WMS', 1, 1),
    (4, 1, 'Diferencia en unidades', 1, 1),
    (5, 2, 'Falla de Servidor', 1, 1),
    (6, 3, 'Falla en interfaz', 1, 1),
    (7, 3, 'Interfaz WMS', 1, 1),
    (8, 3, 'Base de Datos', 1, 1),
    (9, 3, 'No se visualiza la OD del día en PMM', 1, 1),
    (10, 3, 'Orden que no migra a WMS', 1, 1),
    (11, 4, 'Falla Funcionamiento', 1, 1),
    (12, 5, 'Base de Datos', 1, 1),
    (13, 6, 'Falla de Servidor', 1, 1),
    (14, 6, 'Lentitud en la migracion a Ip6', 1, 1),
    (15, 7, 'Interfaz WMS', 1, 1),
    (16, 7, 'Caida del microservicio', 1, 1),
    (17, 8, 'Falla de Servidor', 1, 1),
    (18, 8, 'Falla Funcionamiento', 1, 1),
    (19, 8, 'Falla en interfaz', 1, 1),
    (20, 8, 'Caida del servidor 9', 1, 1),
    (21, 8, 'No reserva de cubetas de prepicking', 1, 1),
    (22, 9, 'No migración de pedidos B2C a WMS', 1, 1),
    (23, 9, 'Interfaz WMS', 1, 1),
    (24, 9, 'Orden que no migra a WMS', 1, 1),
    (25, 9, 'Traslado (TSC) no migra a WMS', 1, 1);

COMMIT;
