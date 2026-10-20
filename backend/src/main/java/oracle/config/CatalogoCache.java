package oracle.config;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import oracle.model.*;
import oracle.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cache en memoria de los catalogos (centros, sistemas, areas, responsables,
 * tipos, proveedores) para resolver nombres sin una consulta por fila.
 *
 * El snapshot se refresca en segundo plano con un scheduler; las peticiones
 * NUNCA recargan la cache, de modo que quede un unico cargador (sin thundering
 * herd) y ninguna request pague el coste de 7 findAll. Si el refresco falla, se
 * conserva el snapshot anterior.
 */
@Slf4j
@Component
public class CatalogoCache {

    private final AtomicReference<Snapshot> ref = new AtomicReference<>();

    private record Snapshot(
            long ts,
            java.util.Map<Long, String> proveedores,
            java.util.Map<Long, String> tiposInbound,
            java.util.Map<Long, String> centros,
            java.util.Map<Long, String> sistemas,
            java.util.Map<Long, String> tipos,
            java.util.Map<Long, String> areas,
            java.util.Map<Long, String> responsables) {}

    private final DataProveedorRepository proveedorRepo;
    private final DataIncidenciaInboundRepository tipoInboundRepo;
    private final DataCentroRepository centroRepo;
    private final DataSistemaRepository sistemaRepo;
    private final DataIncidenciaRepository tipoRepo;
    private final DataAreaRepository areaRepo;
    private final DataResponsableRepository responsableRepo;

    public CatalogoCache(
            DataProveedorRepository proveedorRepo,
            DataIncidenciaInboundRepository tipoInboundRepo,
            DataCentroRepository centroRepo,
            DataSistemaRepository sistemaRepo,
            DataIncidenciaRepository tipoRepo,
            DataAreaRepository areaRepo,
            DataResponsableRepository responsableRepo) {
        this.proveedorRepo = proveedorRepo;
        this.tipoInboundRepo = tipoInboundRepo;
        this.centroRepo = centroRepo;
        this.sistemaRepo = sistemaRepo;
        this.tipoRepo = tipoRepo;
        this.areaRepo = areaRepo;
        this.responsableRepo = responsableRepo;
        cargar();
    }

    public synchronized void cargar() {
        java.util.Map<Long, String> proveedores = proveedorRepo.findAll().stream()
                .collect(Collectors.toMap(DataProveedor::getId, DataProveedor::getProveedor, (a,b)->a));
        java.util.Map<Long, String> tiposInbound = tipoInboundRepo.findAll().stream()
                .collect(Collectors.toMap(DataIncidenciaInbound::getId, DataIncidenciaInbound::getIncidenciaInbound, (a,b)->a));
        java.util.Map<Long, String> centros = centroRepo.findAll().stream()
                .collect(Collectors.toMap(DataCentro::getId, DataCentro::getCentro, (a,b)->a));
        java.util.Map<Long, String> sistemas = sistemaRepo.findAll().stream()
                .collect(Collectors.toMap(DataSistema::getId, DataSistema::getSistema, (a,b)->a));
        java.util.Map<Long, String> tipos = tipoRepo.findAll().stream()
                .collect(Collectors.toMap(DataIncidencia::getId, DataIncidencia::getIncidencia, (a,b)->a));
        java.util.Map<Long, String> areas = areaRepo.findAll().stream()
                .collect(Collectors.toMap(DataArea::getId, DataArea::getArea, (a,b)->a));
        java.util.Map<Long, String> responsables = responsableRepo.findAll().stream()
                .collect(Collectors.toMap(DataResponsable::getId, r -> r.getResponsable() != null ? r.getResponsable() : "", (a,b)->a));
        ref.set(new Snapshot(System.currentTimeMillis(), proveedores, tiposInbound, centros, sistemas, tipos, areas, responsables));
    }

    private Snapshot snap() {
        Snapshot s = ref.get();
        if (s != null) {
            return s;
        }
        cargar();
        return ref.get();
    }

    /** Refresco periodico en segundo plano (por defecto cada 5 min). */
    @Scheduled(
            fixedDelayString = "${app.catalogo.cache.refresh-ms:300000}",
            initialDelayString = "${app.catalogo.cache.refresh-ms:300000}")
    public void refrescar() {
        try {
            cargar();
        } catch (RuntimeException ex) {
            log.warn("No se pudo refrescar la cache de catalogos; se conserva el snapshot previo.", ex);
        }
    }

    public java.util.Map<Long, String> getProveedores() { return snap().proveedores(); }
    public java.util.Map<Long, String> getTiposInbound() { return snap().tiposInbound(); }
    public java.util.Map<Long, String> getCentros() { return snap().centros(); }
    public java.util.Map<Long, String> getSistemas() { return snap().sistemas(); }
    public java.util.Map<Long, String> getTipos() { return snap().tipos(); }
    public java.util.Map<Long, String> getAreas() { return snap().areas(); }
    public java.util.Map<Long, String> getResponsables() { return snap().responsables(); }
}
