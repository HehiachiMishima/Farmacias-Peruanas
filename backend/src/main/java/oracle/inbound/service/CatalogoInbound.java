package oracle.inbound.service;

import java.util.Map;
import java.util.stream.Collectors;
import oracle.common.support.SnapshotCache;
import oracle.inbound.model.DataIncidenciaInbound;
import oracle.inbound.model.DataProveedor;
import oracle.inbound.repository.DataIncidenciaInboundRepository;
import oracle.inbound.repository.DataProveedorRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cache de nombres de los catalogos del panel Inbound (proveedores y tipos de
 * incidencia). Los services de Control la invalidan tras cada CRUD.
 */
@Component
public class CatalogoInbound {

    private record Snapshot(Map<Long, String> proveedores, Map<Long, String> tipos) {}

    private final SnapshotCache<Snapshot> cache;

    public CatalogoInbound(DataProveedorRepository proveedorRepo, DataIncidenciaInboundRepository tipoRepo) {
        this.cache = new SnapshotCache<>("catalogo-inbound", () -> new Snapshot(
                proveedorRepo.findAll().stream()
                        .collect(Collectors.toUnmodifiableMap(DataProveedor::getId, DataProveedor::getProveedor, (a, b) -> a)),
                tipoRepo.findAll().stream()
                        .collect(Collectors.toUnmodifiableMap(DataIncidenciaInbound::getId,
                                DataIncidenciaInbound::getIncidenciaInbound, (a, b) -> a))));
    }

    public Map<Long, String> proveedores() { return cache.obtener().proveedores(); }
    public Map<Long, String> tipos() { return cache.obtener().tipos(); }

    public void invalidar() {
        cache.invalidar();
    }

    @Scheduled(
            fixedDelayString = "${app.catalogo.cache.refresh-ms:300000}",
            initialDelayString = "${app.catalogo.cache.refresh-ms:300000}")
    void refrescar() {
        cache.refrescar();
    }
}
