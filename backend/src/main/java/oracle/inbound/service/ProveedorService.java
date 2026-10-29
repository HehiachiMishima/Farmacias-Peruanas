package oracle.inbound.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import oracle.common.config.Transacciones;
import oracle.common.exception.ConflictoException;
import oracle.common.exception.RecursoNoEncontradoException;
import oracle.common.response.ApiResponse;
import oracle.common.response.PaginaResponse;
import oracle.common.support.ActorActual;
import oracle.common.support.Auditoria;
import oracle.common.support.Filtros;
import oracle.common.support.Listas;
import oracle.common.support.Paginacion;
import oracle.common.support.Trazas;
import oracle.inbound.model.DataProveedor;
import oracle.inbound.repository.DataProveedorRepository;
import oracle.inbound.request.ProveedorActualizarRequest;
import oracle.inbound.request.ProveedorCrearRequest;
import oracle.inbound.response.DataProveedorResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Catalogo de proveedores del panel Inbound. */
@Slf4j
@Service
public class ProveedorService {

    private static final int MAX_TAMANO_PAGINA = 100;

    private final DataProveedorRepository repo;
    private final CatalogoInbound catalogo;
    private final Transacciones transacciones;

    public ProveedorService(DataProveedorRepository repo, CatalogoInbound catalogo,
            Transacciones transacciones) {
        this.repo = repo;
        this.catalogo = catalogo;
        this.transacciones = transacciones;
    }

    public Mono<ApiResponse<List<DataProveedorResponse>>> listarTodos() {
        return Mono.fromCallable(() -> Trazas.medir(log, "ProveedorService.listarTodos", () -> transacciones.consultar(() -> {
                    List<DataProveedorResponse> lista = repo
                            .findAll(Listas.limitado(Sort.by(Sort.Direction.ASC, "proveedor")))
                            .stream().map(this::aResponse).toList();
                    Listas.avisarSiCorto(log, lista.size(), "ProveedorService.listarTodos");
                    return lista;
                })))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Proveedores listados.", lista));
    }

    public Mono<ApiResponse<PaginaResponse<DataProveedorResponse>>> listarPaginado(int pagina, int tamano,
            String texto) {
        PageRequest pageable = Paginacion.pagina(pagina, tamano, MAX_TAMANO_PAGINA, Paginacion.RECIENTES);
        Specification<DataProveedor> spec = Filtros.contiene("proveedor", texto);
        return Mono.fromCallable(() -> Trazas.medir(log, "ProveedorService.listarPaginado", () -> transacciones.consultar(() -> {
                    Page<DataProveedor> page = repo.findAll(spec, pageable);
                    return PaginaResponse.de(page, page.getContent().stream().map(this::aResponse).toList());
                })))
                .subscribeOn(Schedulers.boundedElastic())
                .map(d -> ApiResponse.ok("Proveedores listados.", d));
    }

    public Mono<ApiResponse<DataProveedorResponse>> crear(ProveedorCrearRequest req) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "ProveedorService.crear", () -> transacciones.ejecutar(() -> {
                    String nombre = req.proveedor().trim();
                    if (repo.existsByProveedorIgnoreCase(nombre)) {
                        throw new ConflictoException("Ya existe un proveedor con el nombre '" + nombre + "'.");
                    }
                    DataProveedor p = new DataProveedor();
                    p.setProveedor(nombre);
                    Auditoria.alCrear(p, actor);
                    DataProveedor guardado = repo.save(p);
                    catalogo.registrarCambio();
                    return ApiResponse.ok("Proveedor creado.", aResponse(guardado));
                })))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<DataProveedorResponse>> actualizar(Long id, ProveedorActualizarRequest req) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "ProveedorService.actualizar id=" + id, () -> transacciones.ejecutar(() -> {
                    DataProveedor p = obtener(id);
                    if (req.proveedor() != null && !req.proveedor().isBlank()) {
                        String nombre = req.proveedor().trim();
                        if (repo.existsByProveedorIgnoreCaseAndIdNot(nombre, id)) {
                            throw new ConflictoException("Ya existe otro proveedor con el nombre '" + nombre + "'.");
                        }
                        p.setProveedor(nombre);
                    }
                    Auditoria.alActualizar(p, actor);
                    DataProveedor guardado = repo.save(p);
                    catalogo.registrarCambio();
                    return ApiResponse.ok("Proveedor actualizado.", aResponse(guardado));
                })))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<Void>> eliminar(Long id) {
        return Mono.fromCallable(() -> Trazas.medir(log, "ProveedorService.eliminar id=" + id, () -> transacciones.ejecutar(() -> {
                    DataProveedor p = obtener(id);
                    long usos = repo.contarIncidencias(id);
                    if (usos > 0) {
                        throw new ConflictoException("No se puede eliminar el proveedor '" + p.getProveedor()
                                + "': tiene " + usos + " incidencia(s) registrada(s).");
                    }
                    repo.delete(p);
                    catalogo.registrarCambio();
                    return ApiResponse.<Void>ok("Proveedor eliminado.", null);
                })))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private DataProveedor obtener(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el proveedor con id " + id + "."));
    }

    private DataProveedorResponse aResponse(DataProveedor p) {
        return new DataProveedorResponse(p.getId(), p.getProveedor(), p.getCreadoEn(), p.getActualizadoEn(),
                p.getCreadoPor(), p.getActualizadoPor());
    }
}
