package oracle.services;

import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import oracle.exception.ConflictoException;
import oracle.exception.RecursoNoEncontradoException;
import oracle.model.DataArea;
import oracle.model.DataCentro;
import oracle.model.DataIncidencia;
import oracle.model.DataResponsable;
import oracle.model.DataSistema;
import oracle.model.Incidencia;
import oracle.repository.DataAreaRepository;
import oracle.repository.DataCentroRepository;
import oracle.repository.DataIncidenciaRepository;
import oracle.repository.DataResponsableRepository;
import oracle.repository.DataSistemaRepository;
import oracle.repository.EstadisticaIncidenciaRepository;
import oracle.config.CatalogoCache;
import oracle.repository.IncidenciaRepository;
import oracle.request.FiltroIncidencia;
import oracle.request.IncidenciaActualizarRequest;
import oracle.request.IncidenciaCrearRequest;
import oracle.response.ApiResponse;
import oracle.response.EstadisticaResponse;
import oracle.response.IncidenciaResponse;
import oracle.response.PaginaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
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
    /** Tope de filas para una exportación completa (protege la memoria del JVM). */
    private static final int MAX_FILAS_EXPORT = 100_000;

    private final IncidenciaRepository incidenciaRepository;
    private final DataCentroRepository dataCentroRepository;
    private final DataSistemaRepository dataSistemaRepository;
    private final DataIncidenciaRepository dataIncidenciaRepository;
    private final DataAreaRepository dataAreaRepository;
    private final DataResponsableRepository dataResponsableRepository;
    private final EstadisticaIncidenciaRepository estadisticaRepository;
    private final CatalogoCache catalogoCache;

    public IncidenciaService(IncidenciaRepository incidenciaRepository,
            DataCentroRepository dataCentroRepository,
            DataSistemaRepository dataSistemaRepository,
            DataIncidenciaRepository dataIncidenciaRepository,
            DataAreaRepository dataAreaRepository,
            DataResponsableRepository dataResponsableRepository,
            EstadisticaIncidenciaRepository estadisticaRepository,
        CatalogoCache catalogoCache) {
        this.incidenciaRepository = incidenciaRepository;
        this.dataCentroRepository = dataCentroRepository;
        this.dataSistemaRepository = dataSistemaRepository;
        this.dataIncidenciaRepository = dataIncidenciaRepository;
        this.dataAreaRepository = dataAreaRepository;
        this.dataResponsableRepository = dataResponsableRepository;
        this.estadisticaRepository = estadisticaRepository;
        this.catalogoCache = catalogoCache;
    }

    public Mono<ApiResponse<PaginaResponse<IncidenciaResponse>>> listar(FiltroIncidencia filtro, int pagina,
            int tamano) {
        int paginaSegura = Math.max(1, pagina);
        int tamanoSeguro = Math.min(Math.max(1, tamano), MAX_TAMANO_PAGINA);
        Sort orden = Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id"));
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaService.listar", () -> {
                    Page<Incidencia> resultado = incidenciaRepository.findAll(
                            especificacion(filtro), PageRequest.of(paginaSegura - 1, tamanoSeguro, orden));
                    List<IncidenciaResponse> contenido = resolverLista(resultado.getContent());
                    return ApiResponse.ok("Incidencias listadas.",
                            PaginaResponse.de(resultado, contenido));
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** Todas las filas que cumplen el filtro, acotadas, para exportar. */
    public Mono<ApiResponse<List<IncidenciaResponse>>> listarParaExportar(FiltroIncidencia filtro) {
        Sort orden = Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id"));
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaService.listarParaExportar", () -> {
                    List<Incidencia> filas = incidenciaRepository
                            .findAll(especificacion(filtro), PageRequest.of(0, MAX_FILAS_EXPORT, orden))
                            .getContent();
                    return ApiResponse.ok("Incidencias exportadas.", resolverLista(filas));
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<EstadisticaResponse>> estadisticas(FiltroIncidencia filtro) {
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaService.estadisticas", () -> {
                    long[] totales = estadisticaRepository.totales(filtro);
                    return ApiResponse.ok("Estadísticas calculadas.", new EstadisticaResponse(
                            totales[0], totales[1],
                            estadisticaRepository.porSistema(filtro),
                            estadisticaRepository.porTipo(filtro),
                            estadisticaRepository.porCentro(filtro),
                            estadisticaRepository.porArea(filtro)));
                }))
                .subscribeOn(Schedulers.boundedElastic());
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
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaService.crear ticket=" + peticion.ticket(), () -> {
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

            return ApiResponse.ok("Incidencia registrada.", resolver(incidencia));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<IncidenciaResponse>> actualizar(Long id, IncidenciaActualizarRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaService.actualizar id=" + id, () -> {
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

            return ApiResponse.ok("Incidencia actualizada.", resolver(incidencia));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<Void>> eliminar(Long id) {
        return Mono.<Void>fromRunnable(() -> Trazas.medir(log, "IncidenciaService.eliminar id=" + id, () -> {
                    Incidencia incidencia = obtenerIncidencia(id);
                    incidenciaRepository.delete(incidencia);
                    return null;
                }))
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
        Map<Long, String> centros = catalogoCache.getCentros();
        Map<Long, String> sistemas = catalogoCache.getSistemas();
        Map<Long, String> tipos = catalogoCache.getTipos();
        Map<Long, String> areas = catalogoCache.getAreas();
        Map<Long, String> responsables = catalogoCache.getResponsables();

        return incidencias.stream()
                .map(i -> IncidenciaResponse.of(i,
                        centros.get(i.getIdDataCentro()),
                        sistemas.get(i.getIdDataSistema()),
                        tipos.get(i.getIdDataIncidencia()),
                        areas.get(i.getIdDataArea()),
                        responsables.get(i.getIdDataResponsable())))
                .toList();
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

    private IncidenciaResponse resolver(Incidencia incidencia) {
        String centro = dataCentroRepository.findById(incidencia.getIdDataCentro()).map(DataCentro::getCentro)
                .orElse(null);
        String sistema = dataSistemaRepository.findById(incidencia.getIdDataSistema()).map(DataSistema::getSistema)
                .orElse(null);
        String tipo = dataIncidenciaRepository.findById(incidencia.getIdDataIncidencia())
                .map(DataIncidencia::getIncidencia).orElse(null);
        String area = dataAreaRepository.findById(incidencia.getIdDataArea()).map(DataArea::getArea).orElse(null);
        String responsable = dataResponsableRepository.findById(incidencia.getIdDataResponsable())
                .map(DataResponsable::getResponsable).orElse(null);
        return IncidenciaResponse.of(incidencia, centro, sistema, tipo, area, responsable);
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
