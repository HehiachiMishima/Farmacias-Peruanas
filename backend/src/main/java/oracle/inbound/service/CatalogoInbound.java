package oracle.inbound.service;

import java.util.Map;
import java.util.stream.Collectors;
import oracle.common.config.CacheRespuestas;
import oracle.common.config.Transacciones;
import oracle.common.support.CatalogoCacheado;
import oracle.inbound.model.DataIncidenciaInbound;
import oracle.inbound.model.DataProveedor;
import oracle.inbound.repository.DataIncidenciaInboundRepository;
import oracle.inbound.repository.DataProveedorRepository;
import org.springframework.stereotype.Component;

/**
 * Cache de nombres de los catalogos del panel Inbound (proveedores y tipos de
 * incidencia). Los services de Control registran cada CRUD con registrarCambio().
 */
@Component
public class CatalogoInbound extends CatalogoCacheado<CatalogoInbound.Snapshot> {

    record Snapshot(Map<Long, String> proveedores, Map<Long, String> tipos) {}

    public CatalogoInbound(DataProveedorRepository proveedorRepo, DataIncidenciaInboundRepository tipoRepo,
            Transacciones transacciones, CacheRespuestas cacheRespuestas) {
        super("catalogo-inbound", () -> new Snapshot(
                proveedorRepo.findAll().stream()
                        .collect(Collectors.toUnmodifiableMap(DataProveedor::getId, DataProveedor::getProveedor, (a, b) -> a)),
                tipoRepo.findAll().stream()
                        .collect(Collectors.toUnmodifiableMap(DataIncidenciaInbound::getId,
                                DataIncidenciaInbound::getIncidenciaInbound, (a, b) -> a))),
                transacciones, cacheRespuestas);
    }

    public Map<Long, String> proveedores() { return snapshot().proveedores(); }
    public Map<Long, String> tipos() { return snapshot().tipos(); }
}
