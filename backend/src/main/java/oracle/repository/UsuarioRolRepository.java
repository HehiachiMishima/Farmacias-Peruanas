package oracle.repository;

import java.util.List;
import oracle.model.Rol;
import oracle.model.Usuario;
import oracle.model.UsuarioRol;
import oracle.model.UsuarioRol.UsuarioRolId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, UsuarioRolId> {

    boolean existsByUsuarioIdAndRolId(Long usuarioId, Long rolId);

    @Transactional
    @Modifying
    @Query("DELETE FROM UsuarioRol ur WHERE ur.usuario.id = :usuarioId AND ur.rol.id = :rolId")
    void eliminarPar(@Param("usuarioId") Long usuarioId, @Param("rolId") Long rolId);

    @Query("SELECT ur.rol FROM UsuarioRol ur WHERE ur.usuario.id = :usuarioId")
    List<Rol> rolesDeUsuario(@Param("usuarioId") Long usuarioId);

    @Query("SELECT ur.usuario FROM UsuarioRol ur WHERE ur.rol.id = :rolId")
    List<Usuario> usuariosDeRol(@Param("rolId") Long rolId);
}
