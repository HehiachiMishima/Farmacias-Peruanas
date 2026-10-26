package oracle.simulacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import oracle.common.model.Auditable;

/** Registro de un archivo Excel cargado para simulación. */
@Entity
@Table(name = "archivos_excel")
@Getter
@Setter
public class ArchivoExcel extends Auditable {

  public static final String ESTADO_PROYECCION = "PROYECCION";
  public static final String ESTADO_BALANCEADO = "BALANCEADO";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "nombre_registro", length = 150, nullable = false)
  private String nombreRegistro;

  @Column(name = "nombre_archivo", length = 255, nullable = false)
  private String nombreArchivo;

  @Column(name = "etiqueta", length = 50)
  private String etiqueta;

  @Column(name = "color_hex", length = 10)
  private String colorHex;

  @Column(name = "estado", length = 20, nullable = false)
  private String estado;
}
