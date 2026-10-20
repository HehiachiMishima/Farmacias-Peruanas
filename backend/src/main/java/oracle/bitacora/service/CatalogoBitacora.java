package oracle.bitacora.service;

import java.util.Map;
import java.util.stream.Collectors;
import oracle.bitacora.model.DataArea;
import oracle.bitacora.model.DataCentro;
import oracle.bitacora.model.DataIncidencia;
import oracle.bitacora.model.DataResponsable;
import oracle.bitacora.model.DataSistema;
import oracle.bitacora.repository.DataAreaRepository;
import oracle.bitacora.repository.DataCentroRepository;
import oracle.bitacora.repository.DataIncidenciaRepository;
import oracle.bitacora.repository.DataResponsableRepository;
import oracle.bitacora.repository.DataSistemaRepository;
import oracle.common.support.SnapshotCache;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cache de nombres de los catalogos del panel Bitacora (centros, sistemas,
 * tipos, areas, responsables) para resolver una pagina de incidencias sin
 * una consulta por fila. Los services de Control la invalidan tras cada CRUD.
 */
@Component
public class CatalogoBitacora {

    private record Snapshot(
            Map<Long, String> centros,
            Map<Long, String> sistemas,
            Map<Long, String> tipos,
            Map<Long, String> areas,
            Map<Long, String> responsables) {}

    private final SnapshotCache<Snapshot> cache;

    public CatalogoBitacora(
            DataCentroRepository centroRepo,
            DataSistemaRepository sistemaRepo,
            DataIncidenciaRepository tipoRepo,
            DataAreaRepository areaRepo,
            DataResponsableRepository responsableRepo) {
        this.cache = new SnapshotCache<>("catalogo-bitacora", () -> new Snapshot(
                centroRepo.findAll().stream()
                        .collect(Collectors.toUnmodifiableMap(DataCentro::getId, DataCentro::getCentro, (a, b) -> a)),
                sistemaRepo.findAll().stream()
                        .collect(Collectors.toUnmodifiableMap(DataSistema::getId, DataSistema::getSistema, (a, b) -> a)),
                tipoRepo.findAll().stream()
                        .collect(Collectors.toUnmodifiableMap(DataIncidencia::getId, DataIncidencia::getIncidencia, (a, b) -> a)),
                areaRepo.findAll().stream()
                        .collect(Collectors.toUnmodifiableMap(DataArea::getId, DataArea::getArea, (a, b) -> a)),
                responsableRepo.findAll().stream()
                        .collect(Collectors.toUnmodifiableMap(DataResponsable::getId,
                                r -> r.getResponsable() != null ? r.getResponsable() : "", (a, b) -> a))));
    }

    public Map<Long, String> centros() { return cache.obtener().centros(); }
    public Map<Long, String> sistemas() { return cache.obtener().sistemas(); }
    public Map<Long, String> tipos() { return cache.obtener().tipos(); }
    public Map<Long, String> areas() { return cache.obtener().areas(); }
    public Map<Long, String> responsables() { return cache.obtener().responsables(); }

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
