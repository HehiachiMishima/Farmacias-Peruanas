package oracle.services;

import reactor.core.scheduler.Schedulers;
import oracle.model.DataProveedor;
import oracle.repository.DataProveedorRepository;
import oracle.request.ProveedorActualizarRequest;
import oracle.request.ProveedorCrearRequest;
import oracle.exception.ConflictoException;
import oracle.exception.RecursoNoEncontradoException;
import oracle.response.ApiResponse;
import oracle.response.DataProveedorResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProveedorService {
  private final DataProveedorRepository repo;

  public ProveedorService(DataProveedorRepository repo) {
    this.repo = repo;
  }

  public Mono<ApiResponse<java.util.List<DataProveedorResponse>>> listarTodos() {
    return Mono.fromCallable(() -> repo.findAll(Sort.by(Sort.Direction.ASC, "proveedor")))
        .map(list -> list.stream().map(this::aResponse).toList())
        .subscribeOn(Schedulers.boundedElastic())
        .map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<oracle.response.PaginaResponse<DataProveedorResponse>>> listarPaginado(int pagina, int tamano, String texto) {
    var pageable = PageRequest.of(Math.max(pagina, 1) - 1, Math.min(Math.max(tamano, 1), 100),
        Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id")));
    String termino = texto == null ? "" : texto.trim().toLowerCase();
    Specification<DataProveedor> spec = (root, query, cb) ->
        cb.like(cb.lower(root.get("proveedor")), "%" + termino + "%");
    return Mono.fromCallable(() -> repo.findAll(spec, pageable))
        .map(page -> new oracle.response.PaginaResponse<DataProveedorResponse>(page.getContent().stream().map(this::aResponse).toList(),
            page.getTotalElements(), pagina, page.getSize(), page.getTotalPages()))
        .subscribeOn(Schedulers.boundedElastic())
        .map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<DataProveedorResponse>> crear(ProveedorCrearRequest req) {
    Long actor = oracle.services.ActorActual.id();
    return Mono.fromCallable(() -> {
      String nombre = req.proveedor().trim();
      if (repo.existsByProveedorIgnoreCase(nombre))
        throw new ConflictoException("Ya existe un proveedor con ese nombre");
      DataProveedor p = new DataProveedor();
      p.setProveedor(nombre);
      oracle.services.Auditoria.alCrear(p, actor);
      return repo.save(p);
    }).map(this::aResponse).subscribeOn(Schedulers.boundedElastic()).map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<DataProveedorResponse>> actualizar(Long id, ProveedorActualizarRequest req) {
    Long actor = oracle.services.ActorActual.id();
    return Mono.fromCallable(() -> {
      DataProveedor p = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Proveedor no encontrado"));
      if (req.proveedor() != null && !req.proveedor().isBlank()) {
        String nombre = req.proveedor().trim();
        if (repo.existsByProveedorIgnoreCaseAndIdNot(nombre, id))
          throw new ConflictoException("Ya existe un proveedor con ese nombre");
        p.setProveedor(nombre);
      }
      oracle.services.Auditoria.alActualizar(p, actor);
      return repo.save(p);
    }).map(this::aResponse).subscribeOn(Schedulers.boundedElastic()).map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<Void>> eliminar(Long id) {
    return Mono.fromCallable(() -> {
      if (!repo.existsById(id)) throw new RecursoNoEncontradoException("Proveedor no encontrado");
      repo.deleteById(id);
      return null;
    }).subscribeOn(Schedulers.boundedElastic()).thenReturn(ApiResponse.ok("OK", null));
  }

  private DataProveedorResponse aResponse(DataProveedor p) {
    return new DataProveedorResponse(p.getId(), p.getProveedor(), p.getCreadoEn(), p.getActualizadoEn(),
        p.getCreadoPor(), p.getActualizadoPor());
  }
}
