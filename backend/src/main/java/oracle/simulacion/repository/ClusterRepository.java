package oracle.simulacion.repository;

import java.util.List;
import java.util.Optional;
import oracle.simulacion.model.Cluster;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClusterRepository extends JpaRepository<Cluster, Long> {

  List<Cluster> findAllByOrderByNombreAsc();

  Optional<Cluster> findByNombreIgnoreCase(String nombre);

  boolean existsByNombreIgnoreCase(String nombre);
}
