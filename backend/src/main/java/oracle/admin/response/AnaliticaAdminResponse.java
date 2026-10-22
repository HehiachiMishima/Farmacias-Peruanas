package oracle.admin.response;

import java.util.List;
import oracle.common.response.PuntoSerie;

/** Indicadores ejecutivos del gobierno de accesos (RBAC + overrides). */
public record AnaliticaAdminResponse(
        Kpis kpis,
        List<Conteo> usuariosPorRol,
        List<Conteo> permisosMasOtorgados,
        List<Conteo> coberturaPaneles,
        List<PuntoSerie> altasPorMes) {

    /**
     * @param usuariosSinRol riesgo: cuentas que solo dependen de overrides o no tienen acceso.
     * @param overridesDeny / overridesGrant excepciones individuales al RBAC.
     */
    public record Kpis(
            long usuarios,
            long usuariosActivos,
            long usuariosSinRol,
            long roles,
            long rolesActivos,
            long permisos,
            long permisosActivos,
            long overridesGrant,
            long overridesDeny) {
    }

    public record Conteo(String etiqueta, long valor) {
    }
}
