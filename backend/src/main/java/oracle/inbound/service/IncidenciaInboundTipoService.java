package oracle.inbound.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import oracle.common.config.CacheRespuestas;
import oracle.common.exception.ConflictoException;
import oracle.common.exception.RecursoNoEncontradoException;
import oracle.common.response.ApiResponse;
import oracle.common.response.PaginaResponse;
import oracle.common.support.ActorActual;
import oracle.common.support.Auditoria;
import oracle.common.support.Filtros;
import oracle.common.support.Listas;
import oracle.common.support.Trazas;
import oracle.inbound.model.DataIncidenciaInbound;
import oracle.inbound.repository.DataIncidenciaInboundRepository;
import oracle.inbound.request.IncidenciaInboundTipoActualizarRequest;
import oracle.inbound.request.IncidenciaInboundTipoCrearRequest;
import oracle.inbound.response.DataIncidenciaInboundResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Catalogo de tipos de incidencia del panel Inbound. */
@Slf4j
@Service
public class IncidenciaInboundTipoService {

    private static final int MAX_TAMANO_PAGINA = 100;

    private final DataIncidenciaInboundRepository repo;
    private final CatalogoInbound catalogo;
    private final CacheRespuestas cacheRespuestas;

    public IncidenciaInboundTipoService(DataIncidenciaInboundRepository repo, CatalogoInbound catalogo,
            CacheRespuestas cacheRespuestas) {
        this.repo = repo;
        this.catalogo = catalogo;
        this.cacheRespuestas = cacheRespuestas;
    }

    public Mono<ApiResponse<List<DataIncidenciaInboundResponse>>> listarTodos() {
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaInboundTipoService.listarTodos", () -> {
                    List<DataIncidenciaInboundResponse> lista = repo
                            .findAll(Listas.limitado(Sort.by(Sort.Direction.ASC, "incidenciaInbound")))
                            .stream().map(this::aResponse).toList();
                    Listas.avisarSiCorto(log, lista.size(), "IncidenciaInboundTipoService.listarTodos");
                    return lista;
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Tipos de incidencia listados.", lista));
    }

    public Mono<ApiResponse<PaginaResponse<DataIncidenciaInboundResponse>>> listarPaginado(int pagina, int tamano,
            String texto) {
        PageRequest pageable = PageRequest.of(Math.max(pagina, 1) - 1,
                Math.min(Math.max(tamano, 1), MAX_TAMANO_PAGINA),
                Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id")));
        String termino = Filtros.normalizar(texto);
        Specification<DataIncidenciaInbound> spec = (root, query, cb) -> termino == null
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("incidenciaInbound")), "%" + termino.toLowerCase() + "%");
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaInboundTipoService.listarPaginado", () -> {
                    Page<DataIncidenciaInbound> page = repo.findAll(spec, pageable);
                    return PaginaResponse.de(page, page.getContent().stream().map(this::aResponse).toList());
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(d -> ApiResponse.ok("Tipos de incidencia listados.", d));
    }

    public Mono<ApiResponse<DataIncidenciaInboundResponse>> crear(IncidenciaInboundTipoCrearRequest req) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaInboundTipoService.crear", () -> {
                    String nombre = req.incidenciaInbound().trim();
                    if (repo.existsByIncidenciaInboundIgnoreCase(nombre)) {
                        throw new ConflictoException("Ya existe un tipo de incidencia con el nombre '" + nombre + "'.");
                    }
                    DataIncidenciaInbound p = new DataIncidenciaInbound();
                    p.setIncidenciaInbound(nombre);
                    Auditoria.alCrear(p, actor);
                    DataIncidenciaInbound guardado = repo.save(p);
                    cambioCatalogo();
                    return ApiResponse.ok("Tipo de incidencia creado.", aResponse(guardado));
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<DataIncidenciaInboundResponse>> actualizar(Long id, IncidenciaInboundTipoActualizarRequest req) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaInboundTipoService.actualizar id=" + id, () -> {
                    DataIncidenciaInbound p = obtener(id);
                    if (req.incidenciaInbound() != null && !req.incidenciaInbound().isBlank()) {
                        String nombre = req.incidenciaInbound().trim();
                        if (repo.existsByIncidenciaInboundIgnoreCaseAndIdNot(nombre, id)) {
                            throw new ConflictoException("Ya existe otro tipo de incidencia con el nombre '" + nombre + "'.");
                        }
                        p.setIncidenciaInbound(nombre);
                    }
                    Auditoria.alActualizar(p, actor);
                    DataIncidenciaInbound guardado = repo.save(p);
                    cambioCatalogo();
                    return ApiResponse.ok("Tipo de incidencia actualizado.", aResponse(guardado));
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<Void>> eliminar(Long id) {
        return Mono.fromCallable(() -> Trazas.medir(log, "IncidenciaInboundTipoService.eliminar id=" + id, () -> {
                    DataIncidenciaInbound p = obtener(id);
                    long usos = repo.contarIncidencias(id);
                    if (usos > 0) {
                        throw new ConflictoException("No se puede eliminar el tipo de incidencia '" + p.getIncidenciaInbound()
                                + "': tiene " + usos + " incidencia(s) registrada(s).");
                    }
                    repo.delete(p);
                    cambioCatalogo();
                    return ApiResponse.<Void>ok("Tipo de incidencia eliminado.", null);
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private DataIncidenciaInbound obtener(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el tipo de incidencia con id " + id + "."));
    }

    private DataIncidenciaInboundResponse aResponse(DataIncidenciaInbound p) {
        return new DataIncidenciaInboundResponse(p.getId(), p.getIncidenciaInbound(), p.getCreadoEn(), p.getActualizadoEn(),
                p.getCreadoPor(), p.getActualizadoPor());
    }

    /** Un cambio de catalogo invalida los nombres cacheados y los agregados del dashboard. */
    private void cambioCatalogo() {
        catalogo.invalidar();
        cacheRespuestas.invalidarTodo();
    }
}
