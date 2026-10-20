package oracle.inbound.repository;

import oracle.inbound.model.DataProveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataProveedorRepository
        extends JpaRepository<DataProveedor, Long>, JpaSpecificationExecutor<DataProveedor> {

    boolean existsByProveedorIgnoreCase(String proveedor);

    boolean existsByProveedorIgnoreCaseAndIdNot(String proveedor, Long id);

    /** Incidencias inbound que referencian este proveedor (FK RESTRICT). */
    @Query(value = "SELECT COUNT(*) FROM incidencias_inbound WHERE id_data_proveedor = :id", nativeQuery = true)
    long contarIncidencias(@Param("id") Long id);
}
