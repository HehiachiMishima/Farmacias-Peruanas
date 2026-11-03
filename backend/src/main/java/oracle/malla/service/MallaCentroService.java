package oracle.malla.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import oracle.common.config.Transacciones;
import oracle.common.exception.ConflictoException;
import oracle.common.exception.RecursoNoEncontradoException;
import oracle.common.exception.SolicitudInvalidaException;
import oracle.common.response.ApiResponse;
import oracle.common.support.ActorActual;
import oracle.common.support.Trazas;
import oracle.malla.repository.MallaCentroRepository;
import oracle.malla.request.CentroMallaRequest;
import oracle.malla.request.CentroMallaRequest.Capacidad;
import oracle.malla.request.CentroMallaRequest.Dotacion;
import oracle.malla.request.CentroMallaRequest.DotacionReal;
import oracle.malla.request.CentroMallaRequest.Flujo;
import oracle.malla.request.CentroMallaRequest.Ola;
import oracle.malla.request.CentroMallaRequest.Turno;
import oracle.malla.request.CentroMallaRequest.Zona;
import oracle.malla.response.CentroMallaResponse;
import oracle.malla.response.CentroMallaResumenResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Datos de la malla de cada centro: el módulo Configuración los edita (a
 * mano o importando un Excel) y el módulo Malla los muestra.
 */
@Slf4j
@Service
public class MallaCentroService {

    private final MallaCentroRepository centros;
    private final Transacciones transacciones;

    public MallaCentroService(MallaCentroRepository centros, Transacciones transacciones) {
        this.centros = centros;
        this.transacciones = transacciones;
    }

    public Mono<ApiResponse<List<CentroMallaResumenResponse>>> listar() {
        return leer("listar", centros::listar, "Centros de la malla.");
    }

    public Mono<ApiResponse<CentroMallaResponse>> obtener(Long id) {
        return leer("obtener id=" + id, () -> buscar(id), "Malla del centro.");
    }

    /** Crea el centro y guarda su malla en la misma transacción. */
    public Mono<ApiResponse<CentroMallaResponse>> crear(CentroMallaRequest req) {
        Long actor = ActorActual.id();
        return escribir("crear", () -> {
            validar(req, null);
            Long id = centros.crear(req.codigo().trim(), req.nombre().trim(), actor);
            centros.guardar(id, req, actor);
            return buscar(id);
        }, "Centro creado.");
    }

    public Mono<ApiResponse<CentroMallaResponse>> guardar(Long id, CentroMallaRequest req) {
        Long actor = ActorActual.id();
        return escribir("guardar id=" + id, () -> {
            if (!centros.bloquear(id)) {
                throw noExiste(id);
            }
            validar(req, id);
            centros.guardar(id, req, actor);
            return buscar(id);
        }, "Malla guardada.");
    }

    public Mono<ApiResponse<Void>> eliminar(Long id) {
        return escribir("eliminar id=" + id, () -> {
            if (!centros.eliminar(id)) {
                throw noExiste(id);
            }
            return null;
        }, "Centro eliminado.");
    }

    /** Lee el Excel y devuelve sus datos sin guardarlos: el formulario los muestra para revisar. */
    public Mono<ApiResponse<CentroMallaRequest>> importar(MultipartFile archivo) {
        return Mono.fromCallable(() -> Trazas.medir(log, "MallaCentroService.importar",
                        () -> MallaExcel.leer(archivo)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(datos -> ApiResponse.ok("Excel leído: revisa los datos y guarda.", datos));
    }

    /** Excel con la malla del centro (exportación) o, sin centro, la plantilla con un ejemplo. */
    public Mono<byte[]> excel(Long id) {
        return Mono.fromCallable(() -> Trazas.medir(log, "MallaCentroService.excel id=" + id,
                        () -> MallaExcel.escribir(id == null ? ejemplo() : transacciones.consultar(() -> {
                            CentroMallaResponse c = buscar(id);
                            return new CentroMallaRequest(c.codigo(), c.nombre(), c.turnos(), c.flujos(),
                                    c.olas(), c.zonas(), c.capacidad(), c.dotacion(), c.dotacionReal());
                        }))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Reglas que la validación de campos no cubre: códigos únicos, dotación
     * de turnos que existen y olas y zonas sin repetir.
     */
    void validar(CentroMallaRequest req, Long id) {
        centros.idPorCodigo(req.codigo().trim())
                .filter(otro -> !otro.equals(id))
                .ifPresent(otro -> {
                    throw new ConflictoException("Ya existe un centro con el código " + req.codigo().trim() + ".");
                });
        Set<String> turnos = new HashSet<>();
        for (Turno turno : req.turnos()) {
            if (!turnos.add(MallaCentroRepository.clave(turno.codigo()))) {
                throw new SolicitudInvalidaException("El turno " + turno.codigo().trim() + " está repetido.");
            }
        }
        Set<String> dotacion = new HashSet<>();
        for (Dotacion fila : req.dotacion()) {
            String turno = MallaCentroRepository.clave(fila.turno());
            if (!turnos.contains(turno)) {
                throw new SolicitudInvalidaException(
                        "La dotación usa el turno " + fila.turno().trim() + ", que no está en la lista de turnos.");
            }
            if (!dotacion.add(turno + "|" + fila.proceso())) {
                throw new SolicitudInvalidaException(
                        "La dotación de " + fila.proceso() + " en " + fila.turno().trim() + " está repetida.");
            }
        }
        Set<String> olas = new HashSet<>();
        for (Ola ola : req.olas()) {
            if (!olas.add(minusculas(ola.cluster()) + "|" + minusculas(ola.nombre()))) {
                throw new SolicitudInvalidaException(
                        "La " + ola.nombre().trim() + " del cluster " + ola.cluster().trim() + " está repetida.");
            }
        }
        Set<String> zonas = new HashSet<>();
        for (Zona zona : req.zonas()) {
            if (!zonas.add(minusculas(zona.nombre()))) {
                throw new SolicitudInvalidaException("La zona " + zona.nombre().trim() + " está repetida.");
            }
        }
    }

    /** Plantilla: un ejemplo corto de cada hoja para que se vea el formato. */
    private static CentroMallaRequest ejemplo() {
        return new CentroMallaRequest("9040", "Punta Negra",
                List.of(new Turno("T1", "Turno 1", "07:00", "16:00", "13:00", "Almuerzo", null, true),
                        new Turno("T2", "Turno 2", "13:00", "22:00", "19:00", "Cena", null, true),
                        new Turno("T3", "Turno 3", "22:00", "06:00", "01:00", "Cena", null, true)),
                List.of(new Flujo("REC", "IL", "06:30", null, "Validar citas y ASN del día", false),
                        new Flujo("REC", "OPS", "07:00", "17:00", "Descarga y verificación", false),
                        new Flujo("OLA", "IL", "07:00", null, "Lanzar olas · ola 1", false),
                        new Flujo("OLA", "OPS", "07:00", "13:00", "Picking ola 1", false),
                        new Flujo("DES", "OPS", "15:00", null, "Despacho ola 1", false)),
                List.of(new Ola("1", "Ola 1", "05:00", "07:00", "13:00", "15:00", false, null),
                        new Ola("1", "Ola 2", "13:00", "15:00", "21:00", "23:00", false, null)),
                List.of(new Zona("GTP", 4890, null), new Zona("A-Frame", 3787, null)),
                new Capacidad(null, null, null, null),
                List.of(),
                new DotacionReal(28, 378, 59, 464, "S44"));
    }

    private CentroMallaResponse buscar(Long id) {
        return centros.obtener(id).orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(Long id) {
        return new RecursoNoEncontradoException("No existe el centro de malla con id " + id + ".");
    }

    private static String minusculas(String texto) {
        return texto.trim().toLowerCase(Locale.ROOT);
    }

    private <T> Mono<ApiResponse<T>> leer(String operacion, Supplier<T> trabajo, String mensaje) {
        return Mono.fromCallable(() -> Trazas.medir(log, "MallaCentroService." + operacion,
                        () -> ApiResponse.ok(mensaje, transacciones.consultar(trabajo))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** La respuesta se arma dentro: un resultado nulo (eliminar) no deja el Mono vacío. */
    private <T> Mono<ApiResponse<T>> escribir(String operacion, Supplier<T> trabajo, String mensaje) {
        return Mono.fromCallable(() -> Trazas.medir(log, "MallaCentroService." + operacion, () -> {
                    try {
                        return ApiResponse.ok(mensaje, transacciones.ejecutar(trabajo));
                    } catch (DuplicateKeyException ex) {
                        throw new ConflictoException("Hay datos repetidos en la malla: revisa turnos, olas y zonas.");
                    }
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
