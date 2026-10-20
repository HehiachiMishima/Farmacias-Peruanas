package oracle.admin.repository;

import java.util.List;
import java.util.Optional;
import oracle.admin.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Login: busca por nombre de usuario o por email. */
    Optional<Usuario> findByUsuarioOrEmail(String usuario, String email);

    boolean existsByUsuario(String usuario);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByUsuarioAndIdNot(String usuario, Long id);

    /**
     * Listado con filtros LIKE opcionales y combinados con AND.
     * Cada campo se ignora si llega null o vacio.
     */
    @Query("""
            SELECT u FROM Usuario u
            WHERE (:usuario IS NULL OR LOWER(u.usuario) LIKE LOWER(CONCAT('%', :usuario, '%')))
              AND (:nombre IS NULL OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')))
              AND (:apellido IS NULL OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :apellido, '%')))
              AND (:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')))
              AND (:activo IS NULL OR u.activo = :activo)
            """)
    List<Usuario> buscarConFiltros(
            @Param("usuario") String usuario,
            @Param("nombre") String nombre,
            @Param("apellido") String apellido,
            @Param("email") String email,
            @Param("activo") Boolean activo);
}
