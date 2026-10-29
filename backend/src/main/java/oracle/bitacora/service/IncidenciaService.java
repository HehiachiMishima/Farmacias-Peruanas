package oracle.bitacora.service;

import oracle.bitacora.repository.AnaliticaIncidenciaRepository;
import oracle.bitacora.response.AnaliticaBitacoraResponse;
import oracle.common.support.EjecutorAnalitica;

import oracle.common.support.ActorActual;
import oracle.common.support.Auditoria;
import oracle.common.support.ExportacionJson;
import oracle.common.support.Paginacion;
import oracle.common.support.Trazas;

import jakarta.persistence.criteria.Predicate;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import oracle.common.exception.ConflictoException;
import oracle.common.exception.RecursoNoEncontradoException;
import oracle.bitacora.model.DataIncidencia;
import oracle.bitacora.model.DataResponsable;
import oracle.bitacora.model.Incidencia;
import oracle.bitacora.repository.DataAreaRepository;
import oracle.bitacora.repository.DataCentroRepository;
import oracle.bitacora.repository.DataIncidenciaRepository;
import oracle.bitacora.repository.DataResponsableRepository;
import oracle.bitacora.repository.DataSistemaRepository;
import oracle.bitacora.repository.EstadisticaIncidenciaRepository;
import oracle.bitacora.repository.IncidenciaRepository;
import oracle.bitacora.request.FiltroIncidencia;
import oracle.bitacora.request.IncidenciaActualizarRequest;
import oracle.bitacora.request.IncidenciaCrearRequest;
import oracle.common.response.ApiResponse;
import oracle.bitacora.response.EstadisticaResponse;
import oracle.bitacora.response.IncidenciaResponse;
import oracle.common.response.PaginaResponse;
import oracle.common.config.CacheRespuestas;
import oracle.common.config.Transacciones;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Registro (bitácora) de incidencias. Valida las FK compuestas del esquema,
 * compone creado_en con la fecha indicada + hora de inicio y calcula el
 * tiempo de solución cuando hay hora de fin.
 *
 * El listado se pagina y filtra en el servidor: solo se devuelve la página
 * solicitada y los agregados del dashboard se calculan con SQL (GROUP BY),
 * de modo que el cliente nunca descarga la tabla completa.
 */
@Slf4j
@Service
public class IncidenciaService {

    /** Tope de filas por página que acepta el servidor. */
    private static final int MAX_TAMANO_PAGINA = 200;

    private final IncidenciaRepository incidenciaRepository;
    private final DataCentroRepository dataCentroRepository;
    private final DataSistemaRepository dataSistemaRepository;
    private final DataIncidenciaRepository dataIncidenciaRepository;
    private final DataAreaRepository dataAreaRepository;
    private final DataResponsableRepository dataResponsableRepository;
    private final EstadisticaIncidenciaRepository estadisticaRepository;
    private final CatalogoBitacora catalogo;
    private final CacheRespuestas cacheRespuestas;
    private final Transacciones transacciones;
    private final ObjectMapper json;
    private final AnaliticaIncidenciaRepository analiticaRepository;
    private final EjecutorAnalitica ejecutor;

    public IncidenciaService(IncidenciaRepository incidenciaRepository,
            DataCentroRepository dataCentroRepository,
            DataSistemaRepository dataSistemaRepository,
            DataIncidenciaRepository dataIncidenciaRepository,
            DataAreaRepository dataAreaRepository,
            DataResponsableRepository dataResponsableRepository,
            EstadisticaIncidenciaRepository estadisticaRepository,
        CatalogoBitacora catalogo,
        CacheRespuestas cacheRespuestas,
        Transacciones transacciones,
        ObjectMapper json,
        AnaliticaIncidenciaRepository analiticaRepository,
        EjecutorAnalitica ejecutor) {
        this.analiticaRepository = analiticaRepository;
        this.ejecutor = ejecutor;
        this.incidenciaRepository = incidenciaRepository;
        this.dataCentroRepository = dataCentroRepository;
        this.dataSistemaRepository = dataSistemaRepository;
        this.dataIncidenciaRepository = dataIncidenciaRepository;
        this.dataAreaRepository = dataAreaRepository;
        this.dataResponsableRepository = dataResponsableRepository;
        this.estadisticaRepository = estadisticaRepository;
        this.catalogo = catalogo;
        this.cacheRespuestas = cacheRespuestas;
        this.transacciones = transacciones;
        this.json = json;
    }

    public Mono<ApiResponse<PaginaResponse<IncidenciaResponse>>> listar(FiltroIncidencia filtro, int pagina,
            int tamano) {
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaService.listar", () -> {
                    Page<Incidencia> resultado = incidenciaRepository.findAll(especificacion(filtro),
                            Paginacion.pagina(pagina, tamano, MAX_TAMANO_PAGINA, Paginacion.RECIENTES));
                    List<IncidenciaResponse> contenido = resolverLista(resultado.getContent());
                    return ApiResponse.ok("Incidencias listadas.",
                            PaginaResponse.de(resultado, contenido));
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Exporta el filtro completo escribiendo el JSON directamente al flujo de
     * respuesta: lee por lotes de {@value ExportacionJson#TAMANO_LOTE} filas y nunca
     * materializa la lista completa, así el pico de memoria queda acotado
     * aunque el filtro devuelva cientos de miles de filas.
     *
     * Produce exactamente el mismo envoltorio que
     * {@code ApiResponse&lt;List&lt;IncidenciaResponse&gt;&gt;} (exito, mensaje,
     * datos, timestamp), así el contrato con el frontend no cambia.
     */
    public StreamingResponseBody exportarEnFlujo(FiltroIncidencia filtro) {
        Specification<Incidencia> spec = especificacion(filtro);
        return ExportacionJson.enLotes(json, "Incidencias exportadas.", Paginacion.RECIENTES,
                pageable -> incidenciaRepository.findAll(spec, pageable), this::conversor, log, "incidencias");
    }

    public Mono<ApiResponse<EstadisticaResponse>> estadisticas(FiltroIncidencia filtro) {
        String clave = "incidencia:" + filtro;
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaService.estadisticas", () ->
                        ApiResponse.ok("Estadísticas calculadas.",
                                cacheRespuestas.obtener(clave, () -> calcularEstadisticas(filtro)))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Analitica completa del dashboard: 6 agregaciones en paralelo sobre el
     * ejecutor analitico, cacheadas por filtro (single-flight por clave).
     */
    public Mono<ApiResponse<AnaliticaBitacoraResponse>> analitica(FiltroIncidencia filtro) {
        String clave = "bitacora-analitica:" + filtro;
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaService.analitica", () ->
                        cacheRespuestas.obtener(clave, () -> ejecutor.esperar(calcularAnalitica(filtro)))))
                .subscribeOn(Schedulers.boundedElastic())
                .map(datos -> ApiResponse.ok("Analitica calculada.", datos));
    }

    private Mono<AnaliticaBitacoraResponse> calcularAnalitica(FiltroIncidencia filtro) {
        return Mono.zip(
                        ejecutor.consulta(() -> analiticaRepository.kpis(filtro)),
                        ejecutor.consulta(() -> analiticaRepository.porSistema(filtro)),
                        ejecutor.consulta(() -> analiticaRepository.porCentro(filtro)),
                        ejecutor.consulta(() -> analiticaRepository.porArea(filtro)),
                        ejecutor.consulta(() -> analiticaRepository.porTipo(filtro)),
                        ejecutor.consulta(() -> analiticaRepository.centroPorSistema(filtro)))
                .map(r -> new AnaliticaBitacoraResponse(
                        r.getT1(), r.getT2(), r.getT3(), r.getT4(), r.getT5(), r.getT6()));
    }

    private EstadisticaResponse calcularEstadisticas(FiltroIncidencia filtro) {
        long[] totales = estadisticaRepository.totales(filtro);
        return new EstadisticaResponse(
                totales[0], totales[1],
                estadisticaRepository.porSistema(filtro),
                estadisticaRepository.porTipo(filtro),
                estadisticaRepository.porCentro(filtro),
                estadisticaRepository.porArea(filtro));
    }

    public Mono<ApiResponse<IncidenciaResponse>> obtenerPorId(Long id) {
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaService.obtenerPorId id=" + id, () -> {
                    Incidencia incidencia = obtenerIncidencia(id);
                    return ApiResponse.ok("Incidencia encontrada.", resolver(incidencia));
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<IncidenciaResponse>> crear(IncidenciaCrearRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaService.crear ticket=" + peticion.ticket(), () -> transacciones.ejecutar(() -> {
            validarReferencias(peticion.idDataCentro(), peticion.idDataSistema(), peticion.idDataIncidencia(),
                    peticion.idDataArea(), peticion.idDataResponsable());
            if (incidenciaRepository.existsByTicket(peticion.ticket().trim())) {
                throw new ConflictoException("Ya existe una incidencia con el ticket '" + peticion.ticket() + "'.");
            }

            Incidencia incidencia = new Incidencia();
            aplicarCampos(incidencia, peticion.idDataCentro(), peticion.idDataSistema(), peticion.idDataIncidencia(),
                    peticion.ticket(), peticion.idDataArea(), peticion.idDataResponsable(), peticion.descripcion(),
                    peticion.horaInicio(), peticion.horaFin());
            Auditoria.alCrear(incidencia, actor);
            incidencia.setCreadoEn(componerCreadoEn(peticion.fechaCreacion(), peticion.horaInicio()));
            incidenciaRepository.save(incidencia);
            transacciones.alConfirmar(cacheRespuestas::invalidarTodo);

            return ApiResponse.ok("Incidencia registrada.", resolver(incidencia));
        }))).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<IncidenciaResponse>> actualizar(Long id, IncidenciaActualizarRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaService.actualizar id=" + id, () -> transacciones.ejecutar(() -> {
            Incidencia incidencia = obtenerIncidencia(id);
            validarReferencias(peticion.idDataCentro(), peticion.idDataSistema(), peticion.idDataIncidencia(),
                    peticion.idDataArea(), peticion.idDataResponsable());
            if (incidenciaRepository.existsByTicketAndIdNot(peticion.ticket().trim(), id)) {
                throw new ConflictoException("Ya existe otra incidencia con el ticket '" + peticion.ticket() + "'.");
            }

            aplicarCampos(incidencia, peticion.idDataCentro(), peticion.idDataSistema(), peticion.idDataIncidencia(),
                    peticion.ticket(), peticion.idDataArea(), peticion.idDataResponsable(), peticion.descripcion(),
                    peticion.horaInicio(), peticion.horaFin());
            Auditoria.alActualizar(incidencia, actor);
            incidencia.setCreadoEn(componerCreadoEn(peticion.fechaCreacion(), peticion.horaInicio()));
            incidenciaRepository.save(incidencia);
            transacciones.alConfirmar(cacheRespuestas::invalidarTodo);

            return ApiResponse.ok("Incidencia actualizada.", resolver(incidencia));
        }))).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<Void>> eliminar(Long id) {
        return Mono.<Void>fromRunnable(() -> Trazas.medir(log, "IncidenciaService.eliminar id=" + id, () -> transacciones.ejecutar(() -> {
                    Incidencia incidencia = obtenerIncidencia(id);
                    incidenciaRepository.delete(incidencia);
                    transacciones.alConfirmar(cacheRespuestas::invalidarTodo);
                    return null;
                })))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>ok("Incidencia eliminada.", null)));
    }

    /**
     * Construye el WHERE dinámico del listado. Solo añade las condiciones
     * informadas y respeta el orden de los índices (id_data_* antes de
     * creado_en) para que MySQL los aproveche.
     */
    private Specification<Incidencia> especificacion(FiltroIncidencia filtro) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (filtro != null) {
                if (filtro.centro() != null) {
                    condiciones.add(cb.equal(root.get("idDataCentro"), filtro.centro()));
                }
                if (filtro.sistema() != null) {
                    condiciones.add(cb.equal(root.get("idDataSistema"), filtro.sistema()));
                }
                if (filtro.area() != null) {
                    condiciones.add(cb.equal(root.get("idDataArea"), filtro.area()));
                }
                if (filtro.responsable() != null) {
                    condiciones.add(cb.equal(root.get("idDataResponsable"), filtro.responsable()));
                }
                if (filtro.tipo() != null) {
                    condiciones.add(cb.equal(root.get("idDataIncidencia"), filtro.tipo()));
                }
                if (filtro.desde() != null) {
                    condiciones.add(cb.greaterThanOrEqualTo(root.get("creadoEn"),
                            filtro.desde().atStartOfDay()));
                }
                if (filtro.hasta() != null) {
                    condiciones.add(cb.lessThan(root.get("creadoEn"),
                            filtro.hasta().plusDays(1).atStartOfDay()));
                }
                if (filtro.texto() != null && !filtro.texto().isBlank()) {
                    String patron = "%" + filtro.texto().trim().toLowerCase() + "%";
                    condiciones.add(cb.or(
                            cb.like(cb.lower(root.get("ticket")), patron),
                            cb.like(cb.lower(root.get("descripcion")), patron)));
                }
            }
            return condiciones.isEmpty() ? cb.conjunction() : cb.and(condiciones.toArray(new Predicate[0]));
        };
    }

    /** Resuelve los nombres de catálogo de una página en un solo lote de consultas. */
    private List<IncidenciaResponse> resolverLista(List<Incidencia> incidencias) {
        if (incidencias.isEmpty()) {
            return List.of();
        }
        return incidencias.stream().map(conversor()).toList();
    }

    /** Convierte incidencias a respuesta con un mismo snapshot de nombres de catálogo. */
    private Function<Incidencia, IncidenciaResponse> conversor() {
        Map<Long, String> centros = catalogo.centros();
        Map<Long, String> sistemas = catalogo.sistemas();
        Map<Long, String> tipos = catalogo.tipos();
        Map<Long, String> areas = catalogo.areas();
        Map<Long, String> responsables = catalogo.responsables();
        return i -> IncidenciaResponse.of(i,
                centros.get(i.getIdDataCentro()),
                sistemas.get(i.getIdDataSistema()),
                tipos.get(i.getIdDataIncidencia()),
                areas.get(i.getIdDataArea()),
                responsables.get(i.getIdDataResponsable()));
    }

    private Incidencia obtenerIncidencia(Long id) {
        return incidenciaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la incidencia con id " + id + "."));
    }

    private void aplicarCampos(Incidencia incidencia, Long idCentro, Long idSistema, Long idTipo, String ticket,
            Long idArea, Long idResponsable, String descripcion, LocalTime horaInicio, LocalTime horaFin) {
        incidencia.setIdDataCentro(idCentro);
        incidencia.setIdDataSistema(idSistema);
        incidencia.setIdDataIncidencia(idTipo);
        incidencia.setTicket(ticket.trim());
        incidencia.setIdDataArea(idArea);
        incidencia.setIdDataResponsable(idResponsable);
        incidencia.setDescripcion(descripcion.trim());
        incidencia.setHoraInicio(horaInicio);
        incidencia.setHoraFin(horaFin);
        incidencia.setTiempoSolucion(calcularTiempoSolucion(horaInicio, horaFin));
    }

    /**
     * Verifica que las referencias existen y que las FK compuestas del esquema
     * son coherentes (tipo pertenece al sistema, responsable pertenece al área).
     */
    private void validarReferencias(Long idCentro, Long idSistema, Long idTipo, Long idArea, Long idResponsable) {
        dataCentroRepository.findById(idCentro)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el centro con id " + idCentro + "."));
        dataSistemaRepository.findById(idSistema)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el sistema con id " + idSistema + "."));
        dataAreaRepository.findById(idArea)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el área con id " + idArea + "."));

        DataIncidencia tipo = dataIncidenciaRepository.findById(idTipo)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el tipo de incidencia con id " + idTipo + "."));
        if (!idSistema.equals(tipo.getIdDataSistema())) {
            throw new ConflictoException("El tipo de incidencia con id " + idTipo
                    + " no pertenece al sistema con id " + idSistema + ".");
        }

        DataResponsable responsable = dataResponsableRepository.findById(idResponsable)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el responsable con id " + idResponsable + "."));
        if (!idArea.equals(responsable.getIdDataArea())) {
            throw new ConflictoException("El responsable con id " + idResponsable
                    + " no pertenece al área con id " + idArea + ".");
        }
    }

    /** Resuelve una incidencia con los nombres del catalogo cacheado (sin consultas extra). */
    private IncidenciaResponse resolver(Incidencia incidencia) {
        return resolverLista(List.of(incidencia)).get(0);
    }

    private LocalDateTime componerCreadoEn(LocalDate fecha, LocalTime horaInicio) {
        return fecha.atTime(horaInicio);
    }

    /** Tiempo de solución = hora de fin - hora de inicio (cruce de medianoche +24h). */
    private LocalTime calcularTiempoSolucion(LocalTime inicio, LocalTime fin) {
        if (inicio == null || fin == null) {
            return null;
        }
        long segundos = Duration.between(inicio, fin).getSeconds();
        if (segundos < 0) {
            segundos += 86_400L;
        }
        if (segundos >= 86_400L) {
            return null;
        }
        return LocalTime.ofSecondOfDay(segundos);
    }
}
