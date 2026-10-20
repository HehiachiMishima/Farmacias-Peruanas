package oracle.services;

import java.util.List;
import oracle.exception.ConflictoException;
import oracle.exception.RecursoNoEncontradoException;
import oracle.model.DataArea;
import oracle.model.DataResponsable;
import oracle.repository.DataAreaRepository;
import oracle.repository.DataResponsableRepository;
import oracle.request.AreaActualizarRequest;
import oracle.request.AreaCrearRequest;
import oracle.request.ResponsableActualizarRequest;
import oracle.request.ResponsableCrearRequest;
import oracle.response.ApiResponse;
import oracle.response.AreaResponse;
import oracle.response.ResponsableResponse;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** CRUD de areas + sub-CRUD de responsables (hijos 1:N). */
@Slf4j
@Service
public class AreaService {

    private final DataAreaRepository dataAreaRepository;
    private final DataResponsableRepository dataResponsableRepository;

    public AreaService(DataAreaRepository dataAreaRepository,
            DataResponsableRepository dataResponsableRepository) {
        this.dataAreaRepository = dataAreaRepository;
        this.dataResponsableRepository = dataResponsableRepository;
    }

    public Mono<ApiResponse<List<AreaResponse>>> listar() {
        return Mono.fromCallable(() -> Trazas.medir(log, "AreaService.listar", () -> {
                        List<AreaResponse> lista = dataAreaRepository.findAll(Listas.limitado()).stream()
                                .map(AreaResponse::of).toList();
                        Listas.avisarSiCorto(log, lista.size(), "AreaService.listar");
                        return lista;
                    }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Areas listadas.", lista));
    }

    public Mono<ApiResponse<AreaResponse>> obtenerPorId(Long id) {
        return Mono.fromCallable(() -> Trazas.medir(log, "AreaService.obtenerPorId id=" + id, () ->
                        ApiResponse.ok("Area encontrada.", AreaResponse.of(obtenerArea(id)))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<AreaResponse>> crear(AreaCrearRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "AreaService.crear area=" + peticion.area(), () -> {
            if (dataAreaRepository.existsByArea(peticion.area())) {
                throw new ConflictoException("Ya existe un area con el nombre '" + peticion.area() + "'.");
            }

            DataArea area = new DataArea();
            area.setArea(peticion.area().trim());
            Auditoria.alCrear(area, actor);
            dataAreaRepository.save(area);

            return ApiResponse.ok("Area creada.", AreaResponse.of(area));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<AreaResponse>> actualizar(Long id, AreaActualizarRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "AreaService.actualizar id=" + id, () -> {
            DataArea area = obtenerArea(id);

            if (peticion.area() != null && !peticion.area().trim().equals(area.getArea())) {
                if (dataAreaRepository.existsByAreaAndIdNot(peticion.area().trim(), id)) {
                    throw new ConflictoException("Ya existe otro area con el nombre '" + peticion.area() + "'.");
                }
                area.setArea(peticion.area().trim());
            }

            Auditoria.alActualizar(area, actor);
            dataAreaRepository.save(area);
            return ApiResponse.ok("Area actualizada.", AreaResponse.of(area));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<Void>> eliminar(Long id) {
        return Mono.<Void>fromRunnable(() -> Trazas.medir(log, "AreaService.eliminar id=" + id, () -> {
                    DataArea area = obtenerArea(id);
                    long responsables = dataResponsableRepository.countByIdDataArea(id);
                    long incidencias = dataAreaRepository.contarIncidencias(id);
                    if (responsables > 0 || incidencias > 0) {
                        throw new ConflictoException(
                                "No se puede eliminar el area '" + area.getArea() + "': tiene "
                                        + responsables + " responsable(s) e "
                                        + incidencias + " incidencia(s) registrada(s).");
                    }
                    dataAreaRepository.delete(area);
                    return null;
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>ok("Area eliminada.", null)));
    }

    public Mono<ApiResponse<List<ResponsableResponse>>> responsablesDe(Long areaId) {
        return Mono.fromCallable(() -> Trazas.medir(log, "AreaService.responsablesDe areaId=" + areaId, () -> {
                    obtenerArea(areaId);
                    return dataResponsableRepository.findByIdDataAreaOrderById(areaId).stream()
                            .map(ResponsableResponse::of).toList();
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Responsables del area.", lista));
    }

    public Mono<ApiResponse<ResponsableResponse>> crearResponsable(Long areaId, ResponsableCrearRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log,
                "AreaService.crearResponsable areaId=" + areaId + " responsable=" + peticion.responsable(), () -> {
                    obtenerArea(areaId);
                    if (dataResponsableRepository.existsByResponsable(peticion.responsable())) {
                        throw new ConflictoException(
                                "Ya existe un responsable con el nombre '" + peticion.responsable()
                                        + "' (puede pertenecer a otra area).");
                    }

                    DataResponsable responsable = new DataResponsable();
                    responsable.setIdDataArea(areaId);
                    responsable.setResponsable(peticion.responsable().trim());
                    Auditoria.alCrear(responsable, actor);
                    dataResponsableRepository.save(responsable);

                    return ApiResponse.ok("Responsable creado.", ResponsableResponse.of(responsable));
                })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<ResponsableResponse>> actualizarResponsable(Long areaId, Long responsableId,
            ResponsableActualizarRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "AreaService.actualizarResponsable id=" + responsableId, () -> {
            DataResponsable responsable = obtenerResponsable(areaId, responsableId);

            if (peticion.responsable() != null && !peticion.responsable().trim().equals(responsable.getResponsable())) {
                if (dataResponsableRepository.existsByResponsableAndIdNot(peticion.responsable().trim(), responsableId)) {
                    throw new ConflictoException(
                            "Ya existe otro responsable con el nombre '" + peticion.responsable()
                                    + "' (puede pertenecer a otra area).");
                }
                responsable.setResponsable(peticion.responsable().trim());
            }

            Auditoria.alActualizar(responsable, actor);
            dataResponsableRepository.save(responsable);
            return ApiResponse.ok("Responsable actualizado.", ResponsableResponse.of(responsable));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<Void>> eliminarResponsable(Long areaId, Long responsableId) {
        return Mono.<Void>fromRunnable(() -> Trazas.medir(log,
                "AreaService.eliminarResponsable id=" + responsableId, () -> {
                    DataResponsable responsable = obtenerResponsable(areaId, responsableId);
                    long incidencias = dataResponsableRepository.contarIncidencias(responsableId);
                    if (incidencias > 0) {
                        throw new ConflictoException(
                                "No se puede eliminar el responsable '" + responsable.getResponsable() + "': tiene "
                                        + incidencias + " incidencia(s) registrada(s).");
                    }
                    dataResponsableRepository.delete(responsable);
                    return null;
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>ok("Responsable eliminado.", null)));
    }

    private DataArea obtenerArea(Long id) {
        return dataAreaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el area con id " + id + "."));
    }

    private DataResponsable obtenerResponsable(Long areaId, Long responsableId) {
        DataResponsable responsable = dataResponsableRepository.findById(responsableId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el responsable con id " + responsableId + "."));
        if (!areaId.equals(responsable.getIdDataArea())) {
            throw new RecursoNoEncontradoException(
                    "El responsable con id " + responsableId + " no pertenece al area con id " + areaId + ".");
        }
        return responsable;
    }
}
