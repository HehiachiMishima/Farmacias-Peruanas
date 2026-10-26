package oracle.simulacion.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import oracle.simulacion.service.SimulacionExcel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Escrituras masivas del registro de un archivo de simulación.
 *
 * Se usa JdbcTemplate en lugar de JPA porque las entidades con id IDENTITY
 * desactivan el batching de Hibernate (100k rutas serían 100k INSERT) y las
 * de clave asignada harían un SELECT previo por fila. Con
 * rewriteBatchedStatements cada lote viaja como un INSERT multi-fila.
 */
@Repository
public class SimulacionLotesRepository {

  private static final int LOTE = 2_000;
  private static final int LOTE_IN = 500;

  private final JdbcTemplate jdbc;

  public SimulacionLotesRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Ids de los clusters por clave de nombre; crea los que falten. */
  public Map<String, Long> asegurarClusters(Collection<String> nombres) {
    return asegurarCatalogo("clusters", nombres);
  }

  /** Ids de los chutes por clave de nombre; crea los que falten. */
  public Map<String, Long> asegurarChutes(Collection<String> nombres) {
    return asegurarCatalogo("chutes", nombres);
  }

  public void insertarConfig(Long archivoId, BigDecimal velocidad, Long actor) {
    jdbc.update(
        """
        INSERT INTO simulacion_config (archivo_id, velocidad_base, creado_por, actualizado_por)
        VALUES (?, ?, ?, ?)
        """,
        archivoId,
        velocidad,
        actor,
        actor);
  }

  public void insertarParametrosCluster(Long archivoId, Collection<Long> clusterIds) {
    jdbc.batchUpdate(
        "INSERT INTO parametros_clusters (archivo_id, cluster_id, limite_horas) VALUES (?, ?, 0)",
        new ArrayList<>(clusterIds),
        LOTE,
        (ps, clusterId) -> {
          ps.setLong(1, archivoId);
          ps.setLong(2, clusterId);
        });
  }

  public void insertarParametrosChute(Long archivoId, Collection<Long> chuteIds, int operarios) {
    jdbc.batchUpdate(
        "INSERT INTO parametros_chutes (archivo_id, chute_id, operarios) VALUES (?, ?, ?)",
        new ArrayList<>(chuteIds),
        LOTE,
        (ps, chuteId) -> {
          ps.setLong(1, archivoId);
          ps.setLong(2, chuteId);
          ps.setInt(3, operarios);
        });
  }

  public void insertarRutas(
      Long archivoId,
      List<SimulacionExcel.FilaRuta> rutas,
      Map<String, Long> clusters,
      Map<String, Long> chutes) {
    jdbc.batchUpdate(
        """
        INSERT INTO rutas_proyeccion
            (archivo_id, chute_id, cluster_id, codigo_ruta, total_bultos, total_unidades,
             total_lineas, conteo_cod)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        rutas,
        LOTE,
        (ps, ruta) -> {
          ps.setLong(1, archivoId);
          ps.setLong(2, chutes.get(SimulacionExcel.clave(ruta.chute())));
          ps.setLong(3, clusters.get(SimulacionExcel.clave(ruta.cluster())));
          ps.setString(4, ruta.codigoRuta());
          ps.setBigDecimal(5, ruta.bultos());
          ps.setBigDecimal(6, ruta.unidades());
          ps.setBigDecimal(7, ruta.lineas());
          ps.setInt(8, ruta.conteoCod());
        });
  }

  /**
   * INSERT IGNORE + lectura con bloqueo compartido: si dos cargas crean el
   * mismo nombre a la vez, la segunda espera a la primera y reutiliza su
   * fila en lugar de fallar con la restricción UNIQUE. FOR SHARE lee lo
   * último confirmado, no la instantánea de la transacción.
   */
  private Map<String, Long> asegurarCatalogo(String tabla, Collection<String> nombres) {
    Map<String, String> porClave = new HashMap<>();
    nombres.forEach((nombre) -> porClave.putIfAbsent(SimulacionExcel.clave(nombre), nombre.trim()));
    List<String> unicos = new ArrayList<>(porClave.values());
    jdbc.batchUpdate(
        "INSERT IGNORE INTO " + tabla + " (nombre, activo) VALUES (?, TRUE)",
        unicos,
        LOTE,
        (ps, nombre) -> ps.setString(1, nombre));

    Map<String, Long> ids = new HashMap<>();
    for (int i = 0; i < unicos.size(); i += LOTE_IN) {
      List<String> bloque = unicos.subList(i, Math.min(i + LOTE_IN, unicos.size()));
      String marcadores = String.join(",", bloque.stream().map((n) -> "?").toList());
      jdbc.query(
          "SELECT id, nombre FROM " + tabla + " WHERE nombre IN (" + marcadores + ") FOR SHARE",
          (rs) -> {
            ids.put(SimulacionExcel.clave(rs.getString("nombre")), rs.getLong("id"));
          },
          bloque.toArray());
    }
    porClave.keySet().stream()
        .filter(Predicate.not(ids::containsKey))
        .findFirst()
        .ifPresent(
            (faltante) -> {
              throw new IllegalStateException("No se pudo registrar " + tabla + " " + faltante);
            });
    return ids;
  }
}
