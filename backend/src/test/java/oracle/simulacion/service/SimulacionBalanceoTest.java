package oracle.simulacion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
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
import oracle.simulacion.model.HistorialBalanceo;
import oracle.simulacion.model.ParametroChute;
import oracle.simulacion.model.ParametroChuteId;
import oracle.simulacion.model.ParametroCluster;
import oracle.simulacion.model.ParametroClusterId;
import oracle.simulacion.model.RutaProyeccion;
import oracle.simulacion.model.SimulacionConfig;
import oracle.simulacion.model.TipoRuta;
import oracle.simulacion.repository.ArchivoExcelRepository;
import oracle.simulacion.repository.CargaRutas;
import oracle.simulacion.repository.ChuteRepository;
import oracle.simulacion.repository.ClusterRepository;
import oracle.simulacion.repository.HistorialBalanceoRepository;
import oracle.simulacion.repository.MovimientoRutas;
import oracle.simulacion.repository.ParametroChuteRepository;
import oracle.simulacion.repository.ParametroClusterRepository;
import oracle.simulacion.repository.RutaProyeccionRepository;
import oracle.simulacion.repository.SimulacionConfigRepository;
import oracle.simulacion.request.LadoChute;
import oracle.simulacion.request.Limites;
import oracle.simulacion.response.BalanceoResponse;
import oracle.simulacion.response.ChuteComparativoResponse;
import oracle.simulacion.response.DashboardSimulacionResponse;
import oracle.simulacion.response.RutasDashboardResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    lenient()
        .when(transacciones.ejecutar(any()))
        .thenAnswer(invocacion -> ((Supplier<?>) invocacion.getArgument(0)).get());
    lenient()
        .when(transacciones.consultar(any()))
        .thenAnswer(invocacion -> ((Supplier<?>) invocacion.getArgument(0)).get());
  }

  @Test
  void separaCl1YCl2QueCompartenChuteAntesDeAtenderTiempos() {
    ArchivoExcel archivo = archivo();
    // Límite holgado: ningún cluster tiene déficit, solo la mezcla obliga a mover.
    prepararRegistro(
        archivo,
        configuracion("100", null),
        List.of(limite(1L, "100"), limite(2L, "100")),
        List.of(operarios(1L, 1), operarios(2L, 1)),
        List.of(cluster(1L, "CL1"), cluster(2L, "CL2")),
        List.of(chute(1L, "CHUTE 1"), chute(2L, "CHUTE 2")));
    RutaProyeccion cl1A = ruta(1L, 1L, 1L, "3030", "100");
    RutaProyeccion cl1B = ruta(2L, 1L, 1L, "3031", "100");
    RutaProyeccion cl2 = ruta(3L, 2L, 1L, "3032", "50");
    when(rutaRepo.findByArchivoIdOrderByIdAsc(10L)).thenReturn(List.of(cl1A, cl1B, cl2));
    when(historialRepo.saveAll(any())).thenReturn(List.of());

    BalanceoResponse resultado = servicio.balancear(10L).block().datos();

    assertEquals(1, resultado.rutasMovidas());
    assertEquals(2L, cl2.getChuteId());
    assertEquals(1L, cl1A.getChuteId());
    assertEquals(1L, cl1B.getChuteId());
    assertEquals(ArchivoExcel.ESTADO_BALANCEADO, archivo.getEstado());
  }

  @Test
  void avisaCuandoNoHayChuteParaSepararCl1YCl2() {
    ArchivoExcel archivo = archivo();
    prepararRegistro(
        archivo,
        configuracion("100", null),
        List.of(limite(1L, "100"), limite(2L, "100")),
        List.of(operarios(1L, 1)),
        List.of(cluster(1L, "CL1"), cluster(2L, "CL2")),
        List.of(chute(1L, "CHUTE 1")));
    RutaProyeccion cl2 = ruta(2L, 2L, 1L, "3031", "50");
    when(rutaRepo.findByArchivoIdOrderByIdAsc(10L))
        .thenReturn(List.of(ruta(1L, 1L, 1L, "3030", "100"), cl2));

    BalanceoResponse resultado = servicio.balancear(10L).block().datos();

    assertEquals(0, resultado.rutasMovidas());
    assertEquals(1L, cl2.getChuteId());
    assertTrue(resultado.mensaje().contains("No se pudo separar CL1 y CL2 en CHUTE 1"));
  }

  @Test
  void noMueveUnaRutaSiDejaAOtroClusterFueraDeSuLimite() {
    ArchivoExcel archivo = archivo();
    // CL1: 500 bultos / (100 × 2) = 2,5 h con límite 2 h. CHUTE 2 es de CL3
    // (1,5 h, límite 1,6 h): recibir la ruta de 300 lo dejaría en 3 h.
    prepararRegistro(
        archivo,
        configuracion("100", null),
        List.of(limite(1L, "2.00"), limite(3L, "1.60")),
        List.of(operarios(1L, 2), operarios(2L, 2)),
        List.of(cluster(1L, "CL1"), cluster(3L, "CL3")),
        List.of(chute(1L, "CHUTE 1"), chute(2L, "CHUTE 2")));
    RutaProyeccion grande = ruta(1L, 1L, 1L, "3030", "300");
    when(rutaRepo.findByArchivoIdOrderByIdAsc(10L))
        .thenReturn(List.of(grande, ruta(2L, 1L, 1L, "3031", "200"), ruta(3L, 3L, 2L, "4040", "300")));
    when(historialRepo.saveAll(any())).thenReturn(List.of());

    BalanceoResponse resultado = servicio.balancear(10L).block().datos();

    assertEquals(0, resultado.rutasMovidas());
    assertEquals(1, resultado.operariosAgregados());
    assertEquals(1L, grande.getChuteId());
  }

  @Test
  void conservaElEstadoSiNoHuboAjustes() {
    ArchivoExcel archivo = archivo();
    prepararRegistro(
        archivo,
        configuracion("100", null),
        List.of(limite(1L, "10")),
        List.of(operarios(1L, 1)),
        List.of(cluster(1L, "CL1")),
        List.of(chute(1L, "CHUTE 1")));
    when(rutaRepo.findByArchivoIdOrderByIdAsc(10L))
        .thenReturn(List.of(ruta(1L, 1L, 1L, "3030", "100")));

    BalanceoResponse resultado = servicio.balancear(10L).block().datos();

    assertEquals(0, resultado.rutasMovidas());
    assertEquals(ArchivoExcel.ESTADO_PROYECCION, archivo.getEstado());
  }

  @Test
  void reconoceSoloLosNombresDeClusterNumerados() {
    assertEquals(1, SimulacionConfiguracionService.numeroCluster("CL1"));
    assertEquals(1, SimulacionConfiguracionService.numeroCluster("cl 01"));
    assertEquals(2, SimulacionConfiguracionService.numeroCluster("C2"));
    assertNull(SimulacionConfiguracionService.numeroCluster("CP1"));
    assertNull(SimulacionConfiguracionService.numeroCluster("CB2"));
  }

  @Test
  void restablecerDevuelveRutasYOperariosAlOriginal() {
    ArchivoExcel archivo = archivo();
    archivo.setEstado(ArchivoExcel.ESTADO_BALANCEADO);
    ParametroChute chute1 = operarios(1L, 2);
    ParametroChute chute2 = operarios(2L, 2);
    prepararRegistro(
        archivo,
        configuracion("100", null),
        List.of(limite(1L, "2")),
        List.of(chute1, chute2),
        List.of(cluster(1L, "CL1")),
        List.of(chute(1L, "CHUTE 1"), chute(2L, "CHUTE 2")));
    // Original: 1 y 2 operarios. El balanceo pasó uno de CHUTE 2 a CHUTE 1 y
    // luego añadió otro a CHUTE 2.
    HistorialBalanceo reasignado = new HistorialBalanceo();
    reasignado.setTipoAccion(HistorialBalanceo.ACCION_MOVER_OPERARIO);
    reasignado.setChuteOrigenId(2L);
    reasignado.setChuteDestinoId(1L);
    HistorialBalanceo anadido = new HistorialBalanceo();
    anadido.setTipoAccion(HistorialBalanceo.ACCION_ANADIR_OPERARIO);
    anadido.setChuteModificadoId(2L);
    when(historialRepo.findByArchivoIdOrderByIdAsc(10L)).thenReturn(List.of(reasignado, anadido));

    servicio.restablecer(10L).block();

    verify(rutaRepo).restaurarChutes(10L);
    verify(historialRepo).borrarPorArchivo(10L);
    assertEquals(1, chute1.getOperarios());
    assertEquals(2, chute2.getOperarios());
    assertEquals(ArchivoExcel.ESTADO_PROYECCION, archivo.getEstado());
  }

  @Test
  @SuppressWarnings("unchecked")
  void rutasDelDashboardFiltranPorLadoDelChutePrefijoEscapadoYTipo() {
    ArchivoExcel archivo = archivo();
    when(archivoRepo.findById(10L)).thenReturn(Optional.of(archivo));
    when(configRepo.findById(10L)).thenReturn(Optional.of(configuracion("100", null)));
    when(parametroClusterRepo.findByIdArchivoId(10L)).thenReturn(List.of(limite(1L, "2")));
    when(parametroChuteRepo.findByIdArchivoId(10L)).thenReturn(List.of(operarios(1L, 1)));
    when(clusterRepo.findAllById(any())).thenReturn(List.of(cluster(1L, "CL1")));
    when(chuteRepo.findAllById(any())).thenReturn(List.of(chute(1L, "CHUTE 1")));
    RutaProyeccion ruta = ruta(1L, 1L, 1L, "30_1", "10");
    ruta.setTipo(TipoRuta.B2B);
    when(rutaRepo.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(ruta), PageRequest.of(0, 25), 1));

    RutasDashboardResponse resultado =
        servicio
            .rutasDashboard(
                10L, 1, 25, "30_", TipoRuta.B2B, List.of(1L, 2L), LadoChute.ANTES, null, false)
            .block()
            .datos();

    // "_" es comodín de LIKE: se escapa para buscar el texto literal.
    verify(rutaRepo).resumenFiltrado(10L, "30\\_%", "B2B", List.of(1L, 2L), true, null, false);
    assertEquals(0, resultado.resumen().rutas());
    assertEquals(1, resultado.rutas().contenido().size());
    assertEquals("B2B", resultado.rutas().contenido().get(0).tipo());
    assertEquals("CHUTE 1", resultado.rutas().contenido().get(0).chuteOriginal());
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

    when(archivoRepo.bloquear(10L)).thenReturn(Optional.of(archivo));
    when(configRepo.findById(10L)).thenReturn(Optional.of(config));
    when(parametroClusterRepo.findByIdArchivoId(10L)).thenReturn(List.of(parametroCluster));
    when(parametroChuteRepo.findByIdArchivoId(10L))
        .thenReturn(List.of(parametroChute1, parametroChute2));
    when(rutaRepo.findByArchivoIdOrderByIdAsc(10L))
        .thenReturn(List.of(rutaGrande, rutaPequena, rutaChuteRapido));
    when(clusterRepo.findAllById(any())).thenReturn(List.of(cluster));
    when(chuteRepo.findAllById(any())).thenReturn(List.of(chute1, chute2));
    when(historialRepo.saveAll(any())).thenReturn(List.of());
    when(archivoRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

    Mono<ApiResponse<BalanceoResponse>> respuesta = servicio.balancear(10L);
    BalanceoResponse resultado = respuesta.block().datos();

    assertEquals(1, resultado.rutasMovidas());
    assertEquals(0, resultado.operariosAgregados());
    assertEquals(0, resultado.operariosMovidos());
    assertEquals(2L, rutaGrande.getChuteId());
    assertEquals(1L, rutaPequena.getChuteId());
    assertEquals(ArchivoExcel.ESTADO_BALANCEADO, archivo.getEstado());
  }

  @Test
  void reasignaUnOperarioConHolguraAntesDeAgregar() {
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
    parametroCluster.setLimiteHoras(new BigDecimal("1.00"));

    ParametroChute parametroChute1 = new ParametroChute();
    parametroChute1.setId(new ParametroChuteId(10L, 1L));
    parametroChute1.setOperarios(2);
    ParametroChute parametroChute2 = new ParametroChute();
    parametroChute2.setId(new ParametroChuteId(10L, 2L));
    parametroChute2.setOperarios(3);

    RutaProyeccion cuello = ruta(1L, 1L, 1L, "3030", "300");
    RutaProyeccion rapida = ruta(2L, 1L, 2L, "3031", "200");

    when(archivoRepo.bloquear(10L)).thenReturn(Optional.of(archivo));
    when(configRepo.findById(10L)).thenReturn(Optional.of(config));
    when(parametroClusterRepo.findByIdArchivoId(10L)).thenReturn(List.of(parametroCluster));
    when(parametroChuteRepo.findByIdArchivoId(10L))
        .thenReturn(List.of(parametroChute1, parametroChute2));
    when(rutaRepo.findByArchivoIdOrderByIdAsc(10L)).thenReturn(List.of(cuello, rapida));
    when(clusterRepo.findAllById(any())).thenReturn(List.of(cluster));
    when(chuteRepo.findAllById(any())).thenReturn(List.of(chute1, chute2));
    when(historialRepo.saveAll(any())).thenReturn(List.of());
    when(archivoRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

    BalanceoResponse resultado = servicio.balancear(10L).block().datos();

    assertEquals(0, resultado.rutasMovidas());
    assertEquals(1, resultado.operariosMovidos());
    assertEquals(0, resultado.operariosAgregados());
    assertEquals(3, parametroChute1.getOperarios());
    assertEquals(2, parametroChute2.getOperarios());
    assertEquals(1L, cuello.getChuteId());
    assertEquals(2L, rapida.getChuteId());
  }

  @Test
  void respetaElTopeGlobalDeOperarios() {
    ArchivoExcel archivo = new ArchivoExcel();
    archivo.setId(10L);
    archivo.setEstado(ArchivoExcel.ESTADO_PROYECCION);

    SimulacionConfig config = new SimulacionConfig();
    config.setArchivoId(10L);
    config.setVelocidadBase(new BigDecimal("1"));
    config.setOperariosMaximos(2);

    ParametroCluster parametroCluster = new ParametroCluster();
    parametroCluster.setId(new ParametroClusterId(10L, 1L));
    parametroCluster.setLimiteHoras(new BigDecimal("0.01"));

    ParametroChute parametroChute = new ParametroChute();
    parametroChute.setId(new ParametroChuteId(10L, 1L));
    parametroChute.setOperarios(2);

    when(archivoRepo.bloquear(10L)).thenReturn(Optional.of(archivo));
    when(configRepo.findById(10L)).thenReturn(Optional.of(config));
    when(parametroClusterRepo.findByIdArchivoId(10L)).thenReturn(List.of(parametroCluster));
    when(parametroChuteRepo.findByIdArchivoId(10L)).thenReturn(List.of(parametroChute));
    when(rutaRepo.findByArchivoIdOrderByIdAsc(10L))
        .thenReturn(List.of(ruta(1L, 1L, 1L, "3030", "100000")));
    when(clusterRepo.findAllById(any())).thenReturn(List.of());
    when(chuteRepo.findAllById(any())).thenReturn(List.of());
    when(archivoRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

    BalanceoResponse resultado = servicio.balancear(10L).block().datos();

    assertEquals(0, resultado.operariosAgregados());
    assertEquals(0, resultado.operariosMovidos());
    assertEquals(2, parametroChute.getOperarios());
    assertTrue(resultado.mensaje().contains("máximo de 2"));
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

    when(archivoRepo.bloquear(10L)).thenReturn(Optional.of(archivo));
    when(configRepo.findById(10L)).thenReturn(Optional.of(config));
    when(parametroClusterRepo.findByIdArchivoId(10L)).thenReturn(List.of(parametroCluster));
    when(parametroChuteRepo.findByIdArchivoId(10L)).thenReturn(List.of(parametroChute));
    when(clusterRepo.findAllById(any())).thenReturn(List.of());
    when(chuteRepo.findAllById(any())).thenReturn(List.of());

    assertThrows(ConflictoException.class, () -> servicio.balancear(10L).block());
    assertEquals(ArchivoExcel.ESTADO_PROYECCION, archivo.getEstado());
  }

  @Test
  void noSuperaElMaximoDeOperariosPorChute() {
    ArchivoExcel archivo = new ArchivoExcel();
    archivo.setId(10L);
    archivo.setEstado(ArchivoExcel.ESTADO_PROYECCION);

    SimulacionConfig config = new SimulacionConfig();
    config.setArchivoId(10L);
    config.setVelocidadBase(new BigDecimal("1"));

    ParametroCluster parametroCluster = new ParametroCluster();
    parametroCluster.setId(new ParametroClusterId(10L, 1L));
    parametroCluster.setLimiteHoras(new BigDecimal("0.01"));

    ParametroChute parametroChute = new ParametroChute();
    parametroChute.setId(new ParametroChuteId(10L, 1L));
    parametroChute.setOperarios(2);

    when(archivoRepo.bloquear(10L)).thenReturn(Optional.of(archivo));
    when(configRepo.findById(10L)).thenReturn(Optional.of(config));
    when(parametroClusterRepo.findByIdArchivoId(10L)).thenReturn(List.of(parametroCluster));
    when(parametroChuteRepo.findByIdArchivoId(10L)).thenReturn(List.of(parametroChute));
    when(rutaRepo.findByArchivoIdOrderByIdAsc(10L))
        .thenReturn(List.of(ruta(1L, 1L, 1L, "3030", "100000")));
    when(clusterRepo.findAllById(any())).thenReturn(List.of());
    when(chuteRepo.findAllById(any())).thenReturn(List.of());
    when(historialRepo.saveAll(any())).thenReturn(List.of());
    when(archivoRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

    BalanceoResponse resultado = servicio.balancear(10L).block().datos();

    assertEquals(Limites.MAX_OPERARIOS - 2, resultado.operariosAgregados());
    assertEquals(Limites.MAX_OPERARIOS, parametroChute.getOperarios());
  }

  @Test
  void dashboardSeparaLosClustersDeCadaChuteAntesYDespues() {
    ArchivoExcel archivo = archivo();
    archivo.setEstado(ArchivoExcel.ESTADO_BALANCEADO);
    when(archivoRepo.findById(10L)).thenReturn(Optional.of(archivo));
    when(configRepo.findById(10L)).thenReturn(Optional.of(configuracion("100", null)));
    when(parametroClusterRepo.findByIdArchivoId(10L))
        .thenReturn(List.of(limite(1L, "10"), limite(2L, "10")));
    when(parametroChuteRepo.findByIdArchivoId(10L))
        .thenReturn(List.of(operarios(1L, 1), operarios(2L, 1)));
    when(clusterRepo.findAllById(any()))
        .thenReturn(List.of(cluster(1L, "CL1"), cluster(2L, "CL2")));
    when(chuteRepo.findAllById(any()))
        .thenReturn(List.of(chute(1L, "CHUTE 1"), chute(2L, "CHUTE 2")));
    when(historialRepo.findByArchivoIdOrderByIdAsc(10L)).thenReturn(List.of());
    // Al cargar, CHUTE 1 juntaba CL1 y CL2; el balanceo llevó la ruta de CL2 a CHUTE 2.
    when(rutaRepo.cargaOriginalPorArchivo(10L))
        .thenReturn(List.of(carga(1L, 1L, 3, "300"), carga(2L, 1L, 1, "50")));
    when(rutaRepo.cargaPorArchivo(10L))
        .thenReturn(List.of(carga(1L, 1L, 3, "300"), carga(2L, 2L, 1, "50")));
    when(rutaRepo.movimientosPorArchivo(10L)).thenReturn(List.of(movimiento(1L, 2L, 1)));

    DashboardSimulacionResponse dashboard = servicio.dashboard(10L).block().datos();

    ChuteComparativoResponse chute1 = dashboard.chutes().get(0);
    assertEquals(List.of("CL1", "CL2"), chute1.clustersOriginal());
    assertEquals(List.of("CL1"), chute1.clustersBalanceado());
    assertTrue(chute1.mezclaOriginal());
    assertFalse(chute1.mezclaBalanceada());
    assertEquals(1, chute1.rutasSalieron());
    assertFalse(dashboard.chutes().get(1).mezclaBalanceada());
    assertTrue(dashboard.mezclas().isEmpty());
    assertEquals(1, dashboard.movimientos().size());
    assertEquals(2L, dashboard.movimientos().get(0).destinoId());
  }

  private static CargaRutas carga(Long clusterId, Long chuteId, long rutas, String bultos) {
    return new CargaRutas() {
      public Long getClusterId() {
        return clusterId;
      }

      public Long getChuteId() {
        return chuteId;
      }

      public Long getRutas() {
        return rutas;
      }

      public BigDecimal getBultos() {
        return new BigDecimal(bultos);
      }

      public BigDecimal getUnidades() {
        return BigDecimal.ZERO;
      }

      public BigDecimal getLineas() {
        return BigDecimal.ZERO;
      }

      public BigDecimal getCod() {
        return BigDecimal.ZERO;
      }
    };
  }

  private static MovimientoRutas movimiento(Long origen, Long destino, long rutas) {
    return new MovimientoRutas() {
      public Long getOrigen() {
        return origen;
      }

      public Long getDestino() {
        return destino;
      }

      public Long getRutas() {
        return rutas;
      }
    };
  }

  private void prepararRegistro(
      ArchivoExcel archivo,
      SimulacionConfig config,
      List<ParametroCluster> limites,
      List<ParametroChute> operarios,
      List<Cluster> clusters,
      List<Chute> chutes) {
    when(archivoRepo.bloquear(10L)).thenReturn(Optional.of(archivo));
    when(configRepo.findById(10L)).thenReturn(Optional.of(config));
    when(parametroClusterRepo.findByIdArchivoId(10L)).thenReturn(limites);
    when(parametroChuteRepo.findByIdArchivoId(10L)).thenReturn(operarios);
    when(clusterRepo.findAllById(any())).thenReturn(clusters);
    when(chuteRepo.findAllById(any())).thenReturn(chutes);
    when(archivoRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
  }

  private static ArchivoExcel archivo() {
    ArchivoExcel archivo = new ArchivoExcel();
    archivo.setId(10L);
    archivo.setEstado(ArchivoExcel.ESTADO_PROYECCION);
    return archivo;
  }

  private static SimulacionConfig configuracion(String velocidad, Integer maxOperarios) {
    SimulacionConfig config = new SimulacionConfig();
    config.setArchivoId(10L);
    config.setVelocidadBase(new BigDecimal(velocidad));
    config.setOperariosMaximos(maxOperarios);
    return config;
  }

  private static ParametroCluster limite(Long clusterId, String horas) {
    ParametroCluster parametro = new ParametroCluster();
    parametro.setId(new ParametroClusterId(10L, clusterId));
    parametro.setLimiteHoras(new BigDecimal(horas));
    return parametro;
  }

  private static ParametroChute operarios(Long chuteId, int operarios) {
    ParametroChute parametro = new ParametroChute();
    parametro.setId(new ParametroChuteId(10L, chuteId));
    parametro.setOperarios(operarios);
    return parametro;
  }

  private static Cluster cluster(Long id, String nombre) {
    Cluster cluster = new Cluster();
    cluster.setId(id);
    cluster.setNombre(nombre);
    return cluster;
  }

  private static Chute chute(Long id, String nombre) {
    Chute chute = new Chute();
    chute.setId(id);
    chute.setNombre(nombre);
    return chute;
  }

  private static RutaProyeccion ruta(
      Long id, Long clusterId, Long chuteId, String codigo, String bultos) {
    RutaProyeccion ruta = new RutaProyeccion();
    ruta.setId(id);
    ruta.setArchivoId(10L);
    ruta.setClusterId(clusterId);
    ruta.setChuteId(chuteId);
    ruta.setChuteOriginalId(chuteId);
    ruta.setCodigoRuta(codigo);
    ruta.setTotalBultos(new BigDecimal(bultos));
    ruta.setTotalUnidades(new BigDecimal(bultos));
    ruta.setTotalLineas(new BigDecimal("1"));
    ruta.setConteoCod(1);
    return ruta;
  }
}
