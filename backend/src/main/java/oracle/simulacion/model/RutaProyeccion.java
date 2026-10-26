package oracle.simulacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Ruta proyectada de un archivo (agrupada desde el Excel). */
@Entity
@Table(name = "rutas_proyeccion")
@Getter
@Setter
public class RutaProyeccion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "archivo_id", nullable = false)
  private Long archivoId;

  @Column(name = "chute_id", nullable = false)
  private Long chuteId;

  @Column(name = "cluster_id", nullable = false)
  private Long clusterId;

  @Column(name = "codigo_ruta", length = 50, nullable = false)
  private String codigoRuta;

  @Column(name = "total_bultos", precision = 12, scale = 2, nullable = false)
  private BigDecimal totalBultos;

  @Column(name = "total_unidades", precision = 12, scale = 2, nullable = false)
  private BigDecimal totalUnidades;

  @Column(name = "total_lineas", precision = 12, scale = 2, nullable = false)
  private BigDecimal totalLineas;

  @Column(name = "conteo_cod", nullable = false)
  private Integer conteoCod;
}
