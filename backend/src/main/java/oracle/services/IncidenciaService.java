package oracle.services;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
import oracle.repository.IncidenciaRepository;
import oracle.request.IncidenciaActualizarRequest;
import oracle.request.IncidenciaCrearRequest;
import oracle.response.ApiResponse;
import oracle.response.IncidenciaResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Registro (bitácora) de incidencias. Valida las FK compuestas del esquema,
 * compone creado_en con la fecha indicada + hora de inicio y calcula el
 * tiempo de solución cuando hay hora de fin.
 */
@Slf4j
@Service
public class IncidenciaService {

    private final IncidenciaRepository incidenciaRepository;
    private final DataCentroRepository dataCentroRepository;
    private final DataSistemaRepository dataSistemaRepository;
    private final DataIncidenciaRepository dataIncidenciaRepository;
    private final DataAreaRepository dataAreaRepository;
    private final DataResponsableRepository dataResponsableRepository;

    public IncidenciaService(IncidenciaRepository incidenciaRepository,
            DataCentroRepository dataCentroRepository,
            DataSistemaRepository dataSistemaRepository,
            DataIncidenciaRepository dataIncidenciaRepository,
            DataAreaRepository dataAreaRepository,
            DataResponsableRepository dataResponsableRepository) {
        this.incidenciaRepository = incidenciaRepository;
        this.dataCentroRepository = dataCentroRepository;
        this.dataSistemaRepository = dataSistemaRepository;
        this.dataIncidenciaRepository = dataIncidenciaRepository;
        this.dataAreaRepository = dataAreaRepository;
        this.dataResponsableRepository = dataResponsableRepository;
    }

    public Mono<ApiResponse<List<IncidenciaResponse>>> listar() {
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaService.listar", () -> {
                    Map<Long, String> centros = dataCentroRepository.findAll().stream()
                            .collect(Collectors.toMap(DataCentro::getId, DataCentro::getCentro));
                    Map<Long, String> sistemas = dataSistemaRepository.findAll().stream()
                            .collect(Collectors.toMap(DataSistema::getId, DataSistema::getSistema));
                    Map<Long, String> tipos = dataIncidenciaRepository.findAll().stream()
                            .collect(Collectors.toMap(DataIncidencia::getId, DataIncidencia::getIncidencia));
                    Map<Long, String> areas = dataAreaRepository.findAll().stream()
                            .collect(Collectors.toMap(DataArea::getId, DataArea::getArea));
                    Map<Long, String> responsables = dataResponsableRepository.findAll().stream()
                            .collect(Collectors.toMap(DataResponsable::getId, DataResponsable::getResponsable));

                    Sort orden = Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id"));
                    return incidenciaRepository.findAll(orden).stream()
                            .map(i -> IncidenciaResponse.of(i,
                                    centros.get(i.getIdDataCentro()),
                                    sistemas.get(i.getIdDataSistema()),
                                    tipos.get(i.getIdDataIncidencia()),
                                    areas.get(i.getIdDataArea()),
                                    responsables.get(i.getIdDataResponsable())))
                            .toList();
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Incidencias listadas.", lista));
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
