package oracle.bitacora.service;

import oracle.common.config.CacheRespuestas;

import oracle.common.support.ActorActual;
import oracle.common.support.Auditoria;
import oracle.common.support.Listas;
import oracle.common.support.Trazas;

import java.util.List;
import oracle.common.exception.ConflictoException;
import oracle.common.exception.RecursoNoEncontradoException;
import oracle.bitacora.model.DataCentro;
import oracle.bitacora.repository.DataCentroRepository;
import oracle.bitacora.request.CentroActualizarRequest;
import oracle.bitacora.request.CentroCrearRequest;
import oracle.common.response.ApiResponse;
import oracle.bitacora.response.CentroResponse;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** CRUD de centros. Los deletes se pre-verifican porque las FK son RESTRICT. */
@Slf4j
@Service
public class CentroService {

    private final CatalogoBitacora catalogo;
    private final CacheRespuestas cacheRespuestas;

    private final DataCentroRepository dataCentroRepository;

    public CentroService(DataCentroRepository dataCentroRepository,
            CatalogoBitacora catalogo, CacheRespuestas cacheRespuestas) {
        this.catalogo = catalogo;
        this.cacheRespuestas = cacheRespuestas;
        this.dataCentroRepository = dataCentroRepository;
    }

    public Mono<ApiResponse<List<CentroResponse>>> listar() {
        return Mono.fromCallable(() -> Trazas.medir(log, "CentroService.listar", () -> {
                        List<CentroResponse> lista = dataCentroRepository.findAll(Listas.limitado()).stream()
                                .map(CentroResponse::of).toList();
                        Listas.avisarSiCorto(log, lista.size(), "CentroService.listar");
                        return lista;
                    }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Centros listados.", lista));
    }

    public Mono<ApiResponse<CentroResponse>> obtenerPorId(Long id) {
        return Mono.fromCallable(() -> Trazas.medir(log, "CentroService.obtenerPorId id=" + id, () ->
                        ApiResponse.ok("Centro encontrado.", CentroResponse.of(obtenerCentro(id)))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<CentroResponse>> crear(CentroCrearRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "CentroService.crear centro=" + peticion.centro(), () -> {
            if (dataCentroRepository.existsByCentro(peticion.centro())) {
                throw new ConflictoException("Ya existe un centro con el nombre '" + peticion.centro() + "'.");
            }

            DataCentro centro = new DataCentro();
            centro.setCentro(peticion.centro().trim());
            Auditoria.alCrear(centro, actor);
            dataCentroRepository.save(centro);
            cambioCatalogo();

            return ApiResponse.ok("Centro creado.", CentroResponse.of(centro));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<CentroResponse>> actualizar(Long id, CentroActualizarRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "CentroService.actualizar id=" + id, () -> {
            DataCentro centro = obtenerCentro(id);

            if (peticion.centro() != null && !peticion.centro().trim().equals(centro.getCentro())) {
                if (dataCentroRepository.existsByCentroAndIdNot(peticion.centro().trim(), id)) {
                    throw new ConflictoException("Ya existe otro centro con el nombre '" + peticion.centro() + "'.");
                }
                centro.setCentro(peticion.centro().trim());
            }

            Auditoria.alActualizar(centro, actor);
            dataCentroRepository.save(centro);
            cambioCatalogo();
            return ApiResponse.ok("Centro actualizado.", CentroResponse.of(centro));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<Void>> eliminar(Long id) {
        return Mono.<Void>fromRunnable(() -> Trazas.medir(log, "CentroService.eliminar id=" + id, () -> {
                    DataCentro centro = obtenerCentro(id);
                    long incidencias = dataCentroRepository.contarIncidencias(id);
                    if (incidencias > 0) {
                        throw new ConflictoException(
                                "No se puede eliminar el centro '" + centro.getCentro() + "': tiene "
                                        + incidencias + " incidencia(s) registrada(s).");
                    }
                    dataCentroRepository.delete(centro);
                    cambioCatalogo();
                    return null;
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>ok("Centro eliminado.", null)));
    }

    private DataCentro obtenerCentro(Long id) {
        return dataCentroRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el centro con id " + id + "."));
    }

    /** Un cambio de catalogo invalida los nombres cacheados y los agregados del dashboard. */
    private void cambioCatalogo() {
        catalogo.invalidar();
        cacheRespuestas.invalidarTodo();
    }
}
