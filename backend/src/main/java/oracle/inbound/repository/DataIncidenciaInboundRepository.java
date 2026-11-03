package oracle.inbound.repository;

import oracle.inbound.model.DataIncidenciaInbound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataIncidenciaInboundRepository
        extends JpaRepository<DataIncidenciaInbound, Long>, JpaSpecificationExecutor<DataIncidenciaInbound> {

    /** Igualdad exacta: la columna usa utf8mb4_unicode_ci (ver DataProveedorRepository). */
    boolean existsByIncidenciaInbound(String incidenciaInbound);

    boolean existsByIncidenciaInboundAndIdNot(String incidenciaInbound, Long id);

    /** Incidencias inbound que referencian este tipo (FK RESTRICT). */
    @Query(value = "SELECT COUNT(*) FROM incidencias_inbound WHERE id_data_incidencia_inbound = :id",
            nativeQuery = true)
    long contarIncidencias(@Param("id") Long id);
}
