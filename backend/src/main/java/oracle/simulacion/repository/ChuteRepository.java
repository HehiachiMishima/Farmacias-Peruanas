package oracle.simulacion.repository;

import java.util.List;
import java.util.Optional;
import oracle.simulacion.model.Chute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChuteRepository extends JpaRepository<Chute, Long> {

  List<Chute> findAllByOrderByNombreAsc();

  Optional<Chute> findByNombreIgnoreCase(String nombre);

  boolean existsByNombreIgnoreCase(String nombre);
}
