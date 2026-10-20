package oracle.admin.repository;

import java.util.List;
import oracle.admin.model.Permiso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermisoRepository extends JpaRepository<Permiso, Long> {

    boolean existsByNombre(String nombre);

    boolean existsByNombreAndIdNot(String nombre, Long id);

    /** Listado con filtros LIKE opcionales y combinados con AND. */
    @Query("""
            SELECT p FROM Permiso p
            WHERE (:nombre IS NULL OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')))
              AND (:descripcion IS NULL OR LOWER(p.descripcion) LIKE LOWER(CONCAT('%', :descripcion, '%')))
              AND (:activo IS NULL OR p.activo = :activo)
            """)
    List<Permiso> buscarConFiltros(
            @Param("nombre") String nombre,
            @Param("descripcion") String descripcion,
            @Param("activo") Boolean activo);
}
