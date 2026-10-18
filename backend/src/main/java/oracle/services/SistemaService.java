package oracle.services;

import java.util.List;
import oracle.exception.ConflictoException;
import oracle.exception.RecursoNoEncontradoException;
import oracle.model.DataIncidencia;
import oracle.model.DataSistema;
import oracle.repository.DataIncidenciaRepository;
import oracle.repository.DataSistemaRepository;
import oracle.request.SistemaActualizarRequest;
import oracle.request.SistemaCrearRequest;
import oracle.request.TipoIncidenciaActualizarRequest;
import oracle.request.TipoIncidenciaCrearRequest;
import oracle.response.ApiResponse;
import oracle.response.SistemaResponse;
import oracle.response.TipoIncidenciaResponse;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** CRUD de sistemas + sub-CRUD de tipos de incidencia (hijos 1:N). */
@Slf4j
@Service
public class SistemaService {

    private final DataSistemaRepository dataSistemaRepository;
    private final DataIncidenciaRepository dataIncidenciaRepository;

    public SistemaService(DataSistemaRepository dataSistemaRepository,
            DataIncidenciaRepository dataIncidenciaRepository) {
        this.dataSistemaRepository = dataSistemaRepository;
        this.dataIncidenciaRepository = dataIncidenciaRepository;
    }

    public Mono<ApiResponse<List<SistemaResponse>>> listar() {
        return Mono.fromCallable(() -> Trazas.medir(log, "SistemaService.listar", () ->
                        dataSistemaRepository.findAll().stream().map(SistemaResponse::of).toList()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Sistemas listados.", lista));
    }

    public Mono<ApiResponse<SistemaResponse>> obtenerPorId(Long id) {
        return Mono.fromCallable(() -> Trazas.medir(log, "SistemaService.obtenerPorId id=" + id, () ->
                        ApiResponse.ok("Sistema encontrado.", SistemaResponse.of(obtenerSistema(id)))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<SistemaResponse>> crear(SistemaCrearRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "SistemaService.crear sistema=" + peticion.sistema(), () -> {
            if (dataSistemaRepository.existsBySistema(peticion.sistema())) {
                throw new ConflictoException("Ya existe un sistema con el nombre '" + peticion.sistema() + "'.");
            }

            DataSistema sistema = new DataSistema();
            sistema.setSistema(peticion.sistema().trim());
            Auditoria.alCrear(sistema, actor);
            dataSistemaRepository.save(sistema);

            return ApiResponse.ok("Sistema creado.", SistemaResponse.of(sistema));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<SistemaResponse>> actualizar(Long id, SistemaActualizarRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "SistemaService.actualizar id=" + id, () -> {
            DataSistema sistema = obtenerSistema(id);

            if (peticion.sistema() != null && !peticion.sistema().trim().equals(sistema.getSistema())) {
                if (dataSistemaRepository.existsBySistemaAndIdNot(peticion.sistema().trim(), id)) {
                    throw new ConflictoException("Ya existe otro sistema con el nombre '" + peticion.sistema() + "'.");
                }
                sistema.setSistema(peticion.sistema().trim());
            }

            Auditoria.alActualizar(sistema, actor);
            dataSistemaRepository.save(sistema);
            return ApiResponse.ok("Sistema actualizado.", SistemaResponse.of(sistema));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<Void>> eliminar(Long id) {
        return Mono.<Void>fromRunnable(() -> Trazas.medir(log, "SistemaService.eliminar id=" + id, () -> {
                    DataSistema sistema = obtenerSistema(id);
                    long tipos = dataIncidenciaRepository.countByIdDataSistema(id);
                    long incidencias = dataSistemaRepository.contarIncidencias(id);
                    if (tipos > 0 || incidencias > 0) {
                        throw new ConflictoException(
                                "No se puede eliminar el sistema '" + sistema.getSistema() + "': tiene "
                                        + tipos + " tipo(s) de incidencia y "
                                        + incidencias + " incidencia(s) registrada(s).");
                    }
                    dataSistemaRepository.delete(sistema);
                    return null;
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>ok("Sistema eliminado.", null)));
    }

    public Mono<ApiResponse<List<TipoIncidenciaResponse>>> tiposDe(Long sistemaId) {
        return Mono.fromCallable(() -> Trazas.medir(log, "SistemaService.tiposDe sistemaId=" + sistemaId, () -> {
                    obtenerSistema(sistemaId);
                    return dataIncidenciaRepository.findByIdDataSistemaOrderById(sistemaId).stream()
                            .map(TipoIncidenciaResponse::of).toList();
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Tipos de incidencia del sistema.", lista));
    }

    public Mono<ApiResponse<TipoIncidenciaResponse>> crearTipo(Long sistemaId, TipoIncidenciaCrearRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log,
                "SistemaService.crearTipo sistemaId=" + sistemaId + " incidencia=" + peticion.incidencia(), () -> {
                    obtenerSistema(sistemaId);
                    if (dataIncidenciaRepository.existsByIdDataSistemaAndIncidencia(sistemaId, peticion.incidencia())) {
                        throw new ConflictoException(
                                "Ya existe el tipo de incidencia '" + peticion.incidencia() + "' para este sistema.");
                    }

                    DataIncidencia tipo = new DataIncidencia();
                    tipo.setIdDataSistema(sistemaId);
                    tipo.setIncidencia(peticion.incidencia().trim());
                    Auditoria.alCrear(tipo, actor);
                    dataIncidenciaRepository.save(tipo);

                    return ApiResponse.ok("Tipo de incidencia creado.", TipoIncidenciaResponse.of(tipo));
                })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<TipoIncidenciaResponse>> actualizarTipo(Long sistemaId, Long tipoId,
            TipoIncidenciaActualizarRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "SistemaService.actualizarTipo tipoId=" + tipoId, () -> {
            DataIncidencia tipo = obtenerTipo(sistemaId, tipoId);

            if (peticion.incidencia() != null && !peticion.incidencia().trim().equals(tipo.getIncidencia())) {
                if (dataIncidenciaRepository.existsByIdDataSistemaAndIncidenciaAndIdNot(
                        sistemaId, peticion.incidencia().trim(), tipoId)) {
                    throw new ConflictoException(
                            "Ya existe otro tipo de incidencia '" + peticion.incidencia() + "' para este sistema.");
                }
                tipo.setIncidencia(peticion.incidencia().trim());
            }

            Auditoria.alActualizar(tipo, actor);
            dataIncidenciaRepository.save(tipo);
            return ApiResponse.ok("Tipo de incidencia actualizado.", TipoIncidenciaResponse.of(tipo));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<Void>> eliminarTipo(Long sistemaId, Long tipoId) {
        return Mono.<Void>fromRunnable(() -> Trazas.medir(log, "SistemaService.eliminarTipo tipoId=" + tipoId, () -> {
                    DataIncidencia tipo = obtenerTipo(sistemaId, tipoId);
                    long incidencias = dataIncidenciaRepository.contarIncidencias(tipoId);
                    if (incidencias > 0) {
                        throw new ConflictoException(
                                "No se puede eliminar el tipo de incidencia '" + tipo.getIncidencia() + "': tiene "
                                        + incidencias + " incidencia(s) registrada(s).");
                    }
                    dataIncidenciaRepository.delete(tipo);
                    return null;
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>ok("Tipo de incidencia eliminado.", null)));
    }

    private DataSistema obtenerSistema(Long id) {
        return dataSistemaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el sistema con id " + id + "."));
    }

    private DataIncidencia obtenerTipo(Long sistemaId, Long tipoId) {
        DataIncidencia tipo = dataIncidenciaRepository.findById(tipoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el tipo de incidencia con id " + tipoId + "."));
        if (!sistemaId.equals(tipo.getIdDataSistema())) {
            throw new RecursoNoEncontradoException(
                    "El tipo de incidencia con id " + tipoId + " no pertenece al sistema con id " + sistemaId + ".");
        }
        return tipo;
    }
}
