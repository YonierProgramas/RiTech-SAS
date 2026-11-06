package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.repository.*;
import co.edu.ucc.ritech.web.ApiDtos.DashboardView;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DashboardService {
    private final ClienteRepository clientes; private final ProductoRepository productos; private final LoteRepository lotes;
    private final PedidoRepository pedidos; private final ExportacionRepository exportaciones; private final InspeccionCalidadRepository inspecciones;
    private final JdbcTemplate jdbc;
    public DashboardService(ClienteRepository clientes, ProductoRepository productos, LoteRepository lotes, PedidoRepository pedidos,
                            ExportacionRepository exportaciones, InspeccionCalidadRepository inspecciones, JdbcTemplate jdbc) {
        this.clientes=clientes; this.productos=productos; this.lotes=lotes; this.pedidos=pedidos; this.exportaciones=exportaciones; this.inspecciones=inspecciones;
        this.jdbc = jdbc;
    }
    public DashboardView resumen() {
        return new DashboardView(clientes.count(), productos.count(), lotes.count(), pedidos.count(), exportaciones.count(), inspecciones.count());
    }

    public Map<String, Object> operativo() {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("clientesActivos", jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE activo=TRUE", Long.class));
        resultado.put("totalProductos", jdbc.queryForObject("SELECT COUNT(*) FROM productos", Long.class));
        resultado.put("totalLotes", jdbc.queryForObject("SELECT COUNT(*) FROM lotes", Long.class));
        resultado.put("lotesDisponibles", jdbc.queryForObject("SELECT COUNT(*) FROM lotes WHERE estado='ACTIVO' AND cantidad_disponible_kg > 0", Long.class));
        resultado.put("stockDisponibleKg", jdbc.queryForObject("SELECT COALESCE(SUM(cantidad_disponible_kg),0) FROM lotes", java.math.BigDecimal.class));
        resultado.put("totalPedidos", jdbc.queryForObject("SELECT COUNT(*) FROM pedidos", Long.class));
        resultado.put("pedidosConfirmados", jdbc.queryForObject("SELECT COUNT(*) FROM pedidos WHERE estado='CONFIRMADO'", Long.class));
        resultado.put("totalExportaciones", jdbc.queryForObject("SELECT COUNT(*) FROM exportaciones", Long.class));
        resultado.put("totalInspecciones", jdbc.queryForObject("SELECT COUNT(*) FROM inspecciones_calidad", Long.class));
        resultado.put("inspeccionesAprobadas", jdbc.queryForObject("SELECT COUNT(*) FROM inspecciones_calidad WHERE resultado='APROBADO'", Long.class));
        resultado.put("exportacionesPorEstado", jdbc.queryForList("SELECT estado, COUNT(*) AS total FROM exportaciones GROUP BY estado ORDER BY estado"));
        resultado.put("ultimosEventos", jdbc.queryForList("SELECT fecha_evento AS fecha, 'TRAZABILIDAD' AS origen, descripcion " +
            "FROM eventos_trazabilidad UNION ALL SELECT fecha_evento, 'ESTADO_EXPORTACION', observacion " +
            "FROM historial_estados UNION ALL SELECT fecha_inspeccion, 'CALIDAD', COALESCE(observaciones, resultado) " +
            "FROM inspecciones_calidad ORDER BY fecha DESC LIMIT 10"));
        return resultado;
    }
}
