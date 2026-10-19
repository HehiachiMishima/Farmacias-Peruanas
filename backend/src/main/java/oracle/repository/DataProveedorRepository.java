package oracle.repository;

import oracle.model.DataProveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DataProveedorRepository extends JpaRepository<DataProveedor, Long>, JpaSpecificationExecutor<DataProveedor> {
  boolean existsByProveedorIgnoreCase(String proveedor);
  boolean existsByProveedorIgnoreCaseAndIdNot(String proveedor, Long id);
}
