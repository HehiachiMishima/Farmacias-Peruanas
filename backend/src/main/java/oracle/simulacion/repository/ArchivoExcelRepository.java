package oracle.simulacion.repository;

import java.util.List;
import oracle.simulacion.model.ArchivoExcel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ArchivoExcelRepository
    extends JpaRepository<ArchivoExcel, Long>, JpaSpecificationExecutor<ArchivoExcel> {

  List<ArchivoExcel> findAllByOrderByCreadoEnDescIdDesc();

  @Query("SELECT COUNT(DISTINCT a.etiqueta) FROM ArchivoExcel a WHERE a.etiqueta IS NOT NULL AND a.etiqueta <> ''")
  long contarEtiquetas();
}
