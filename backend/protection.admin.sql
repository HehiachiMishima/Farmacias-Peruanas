-- Active: 1791152181635@@127.0.0.1@3306@farmacias_peruanas
-- ------------------------------------------------------------
-- USUARIO '@admin' (protección TOTAL por NOMBRE)
--   - No se puede eliminar.
--   - No se puede modificar NINGÚN campo.
--   - usuarios_roles: no se puede agregar ni quitar.
--
-- ORDEN: ejecutar DESPUES de arrancar el backend una vez (el arranque
-- crea al usuario semilla USER_ADMIN=@admin). Es re-ejecutable: primero
-- elimina los triggers si ya existen.
-- El backend replica estas reglas (Protecciones.USUARIOS_PROTEGIDOS) para
-- responder 403 limpio antes de llegar al SIGNAL de MySQL.
-- ------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_usuarios_admin_update;
DROP TRIGGER IF EXISTS trg_usuarios_admin_delete;
DROP TRIGGER IF EXISTS trg_usuarios_roles_admin_insert;
DROP TRIGGER IF EXISTS trg_usuarios_roles_admin_delete;
DROP TRIGGER IF EXISTS trg_usuarios_permisos_admin_insert;
DROP TRIGGER IF EXISTS trg_usuarios_permisos_admin_update;
DROP TRIGGER IF EXISTS trg_usuarios_permisos_admin_delete;

DELIMITER $$

CREATE TRIGGER trg_usuarios_admin_update
BEFORE UPDATE ON usuarios
FOR EACH ROW
BEGIN
    IF OLD.usuario = '@admin' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se puede modificar al usuario @admin.';
    END IF;
END$$

CREATE TRIGGER trg_usuarios_admin_delete
BEFORE DELETE ON usuarios
FOR EACH ROW
BEGIN
    IF OLD.usuario = '@admin' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se puede eliminar al usuario @admin.';
    END IF;
END$$

CREATE TRIGGER trg_usuarios_roles_admin_insert
BEFORE INSERT ON usuarios_roles
FOR EACH ROW
BEGIN
    DECLARE v_usuario VARCHAR(50);
    DECLARE v_roles INT;
    SELECT usuario INTO v_usuario FROM usuarios WHERE id = NEW.usuario_id;
    IF v_usuario = '@admin' THEN
        SELECT COUNT(*) INTO v_roles FROM usuarios_roles WHERE usuario_id = NEW.usuario_id;
        IF v_roles > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'No se puede modificar el rol de @admin.';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_usuarios_roles_admin_delete
BEFORE DELETE ON usuarios_roles
FOR EACH ROW
BEGIN
    DECLARE v_usuario VARCHAR(50);
    SELECT usuario INTO v_usuario FROM usuarios WHERE id = OLD.usuario_id;
    IF v_usuario = '@admin' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se puede modificar el rol de @admin.';
    END IF;
END$$

-- Bloquea INSERT de overrides para @admin
CREATE TRIGGER trg_usuarios_permisos_admin_insert
BEFORE INSERT ON usuarios_permisos
FOR EACH ROW
BEGIN
    DECLARE v_usuario VARCHAR(50);
    SELECT usuario INTO v_usuario FROM usuarios WHERE id = NEW.usuario_id;
    IF v_usuario = '@admin' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se pueden otorgar ni denegar permisos al usuario @admin.';
    END IF;
END$$

-- Bloquea UPDATE sobre overrides de @admin y también
-- impide "mover" un override hacia @admin cambiando usuario_id
CREATE TRIGGER trg_usuarios_permisos_admin_update
BEFORE UPDATE ON usuarios_permisos
FOR EACH ROW
BEGIN
    DECLARE v_old_usuario VARCHAR(50);
    DECLARE v_new_usuario VARCHAR(50);
    SELECT usuario INTO v_old_usuario FROM usuarios WHERE id = OLD.usuario_id;
    SELECT usuario INTO v_new_usuario FROM usuarios WHERE id = NEW.usuario_id;
    IF v_old_usuario = '@admin' OR v_new_usuario = '@admin' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se pueden modificar los permisos del usuario @admin.';
    END IF;
END$$

-- Bloquea DELETE de overrides de @admin
CREATE TRIGGER trg_usuarios_permisos_admin_delete
BEFORE DELETE ON usuarios_permisos
FOR EACH ROW
BEGIN
    DECLARE v_usuario VARCHAR(50);
    SELECT usuario INTO v_usuario FROM usuarios WHERE id = OLD.usuario_id;
    IF v_usuario = '@admin' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se pueden eliminar los permisos del usuario @admin.';
    END IF;
END$$

DELIMITER ;