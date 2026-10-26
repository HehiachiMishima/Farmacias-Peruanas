package oracle.simulacion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import oracle.common.config.Transacciones;
import oracle.common.exception.ConflictoException;
import oracle.common.response.ApiResponse;
import oracle.simulacion.model.ArchivoExcel;
import oracle.simulacion.model.Chute;
import oracle.simulacion.model.Cluster;
import oracle.simulacion.model.ParametroChute;
import oracle.simulacion.model.ParametroChuteId;
import oracle.simulacion.model.ParametroCluster;
import oracle.simulacion.model.ParametroClusterId;
import oracle.simulacion.model.RutaProyeccion;
import oracle.simulacion.model.SimulacionConfig;
import oracle.simulacion.repository.ArchivoExcelRepository;
import oracle.simulacion.repository.ChuteRepository;
import oracle.simulacion.repository.ClusterRepository;
import oracle.simulacion.repository.HistorialBalanceoRepository;
import oracle.simulacion.repository.ParametroChuteRepository;
import oracle.simulacion.repository.ParametroClusterRepository;
import oracle.simulacion.repository.RutaProyeccionRepository;
import oracle.simulacion.repository.SimulacionConfigRepository;
import oracle.simulacion.response.BalanceoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class SimulacionBalanceoTest {

  @Mock private ArchivoExcelRepository archivoRepo;
  @Mock private SimulacionConfigRepository configRepo;
  @Mock private ClusterRepository clusterRepo;
  @Mock private ChuteRepository chuteRepo;
  @Mock private ParametroClusterRepository parametroClusterRepo;
  @Mock private ParametroChuteRepository parametroChuteRepo;
  @Mock private RutaProyeccionRepository rutaRepo;
  @Mock private HistorialBalanceoRepository historialRepo;
  @Mock private Transacciones transacciones;

  private SimulacionConfiguracionService servicio;
  private RutaProyeccion rutaGrande;
  private RutaProyeccion rutaPequena;
  private RutaProyeccion rutaChuteRapido;

  @BeforeEach
  void preparar() {
    servicio =
        new SimulacionConfiguracionService(
            archivoRepo,
            configRepo,
            clusterRepo,
            chuteRepo,
            parametroClusterRepo,
            parametroChuteRepo,
            rutaRepo,
            historialRepo,
            transacciones);
    when(transacciones.ejecutar(any()))
        .thenAnswer(invocacion -> ((Supplier<?>) invocacion.getArgument(0)).get());
  }

  @Test
  void mueveLaRutaMasGrandeDelCuelloDeBotellaAlChuteMasRapido() {
    ArchivoExcel archivo = new ArchivoExcel();
    archivo.setId(10L);
    archivo.setEstado(ArchivoExcel.ESTADO_PROYECCION);

    SimulacionConfig config = new SimulacionConfig();
    config.setArchivoId(10L);
    config.setVelocidadBase(new BigDecimal("100"));

    Cluster cluster = new Cluster();
    cluster.setId(1L);
    cluster.setNombre("CL1");
    Chute chute1 = new Chute();
    chute1.setId(1L);
    chute1.setNombre("CHUTE 1");
    Chute chute2 = new Chute();
    chute2.setId(2L);
    chute2.setNombre("CHUTE 2");

    ParametroCluster parametroCluster = new ParametroCluster();
    parametroCluster.setId(new ParametroClusterId(10L, 1L));
    parametroCluster.setLimiteHoras(new BigDecimal("2.00"));

    ParametroChute parametroChute1 = new ParametroChute();
    parametroChute1.setId(new ParametroChuteId(10L, 1L));
    parametroChute1.setOperarios(2);
    ParametroChute parametroChute2 = new ParametroChute();
    parametroChute2.setId(new ParametroChuteId(10L, 2L));
    parametroChute2.setOperarios(2);

    rutaGrande = ruta(1L, 1L, 1L, "3030", "300");
    rutaPequena = ruta(2L, 1L, 1L, "3031", "200");
    rutaChuteRapido = ruta(3L, 1L, 2L, "3032", "100");

    when(archivoRepo.findById(10L)).thenReturn(Optional.of(archivo));
    when(configRepo.findById(10L)).thenReturn(Optional.of(config));
    when(parametroClusterRepo.findByIdArchivoId(10L)).thenReturn(List.of(parametroCluster));
    when(parametroChuteRepo.findByIdArchivoId(10L))
        .thenReturn(List.of(parametroChute1, parametroChute2));
    when(rutaRepo.findByArchivoIdOrderByIdAsc(10L))
        .thenReturn(List.of(rutaGrande, rutaPequena, rutaChuteRapido));
    when(clusterRepo.findAllById(any())).thenReturn(List.of(cluster));
    when(chuteRepo.findAllById(any())).thenReturn(List.of(chute1, chute2));
    when(rutaRepo.saveAll(any())).thenReturn(List.of());
    when(historialRepo.saveAll(any())).thenReturn(List.of());
    when(archivoRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

    Mono<ApiResponse<BalanceoResponse>> respuesta = servicio.balancear(10L);
    BalanceoResponse resultado = respuesta.block().datos();

    assertEquals(1, resultado.rutasMovidas());
    assertEquals(0, resultado.operariosAgregados());
    assertEquals(2L, rutaGrande.getChuteId());
    assertEquals(1L, rutaPequena.getChuteId());
    assertEquals(ArchivoExcel.ESTADO_BALANCEADO, archivo.getEstado());
  }

  @Test
  void rechazaElBalanceoSiNoSeDefinioNingunLimite() {
    ArchivoExcel archivo = new ArchivoExcel();
    archivo.setId(10L);
    archivo.setEstado(ArchivoExcel.ESTADO_PROYECCION);

    SimulacionConfig config = new SimulacionConfig();
    config.setArchivoId(10L);
    config.setVelocidadBase(new BigDecimal("100"));

    ParametroCluster parametroCluster = new ParametroCluster();
    parametroCluster.setId(new ParametroClusterId(10L, 1L));
    parametroCluster.setLimiteHoras(BigDecimal.ZERO);

    ParametroChute parametroChute = new ParametroChute();
    parametroChute.setId(new ParametroChuteId(10L, 1L));
    parametroChute.setOperarios(2);

    when(archivoRepo.findById(10L)).thenReturn(Optional.of(archivo));
    when(configRepo.findById(10L)).thenReturn(Optional.of(config));
    when(parametroClusterRepo.findByIdArchivoId(10L)).thenReturn(List.of(parametroCluster));
    when(parametroChuteRepo.findByIdArchivoId(10L)).thenReturn(List.of(parametroChute));
    when(rutaRepo.findByArchivoIdOrderByIdAsc(10L))
        .thenReturn(List.of(ruta(1L, 1L, 1L, "3030", "100")));
    when(clusterRepo.findAllById(any())).thenReturn(List.of());
    when(chuteRepo.findAllById(any())).thenReturn(List.of());

    assertThrows(ConflictoException.class, () -> servicio.balancear(10L).block());
    assertEquals(ArchivoExcel.ESTADO_PROYECCION, archivo.getEstado());
  }

  private static RutaProyeccion ruta(
      Long id, Long clusterId, Long chuteId, String codigo, String bultos) {
    RutaProyeccion ruta = new RutaProyeccion();
    ruta.setId(id);
    ruta.setArchivoId(10L);
    ruta.setClusterId(clusterId);
    ruta.setChuteId(chuteId);
    ruta.setCodigoRuta(codigo);
    ruta.setTotalBultos(new BigDecimal(bultos));
    ruta.setTotalUnidades(new BigDecimal(bultos));
    ruta.setTotalLineas(new BigDecimal("1"));
    ruta.setConteoCod(1);
    return ruta;
  }
}
