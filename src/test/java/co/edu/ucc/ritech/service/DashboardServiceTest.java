package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.repository.*;
import co.edu.ucc.ritech.web.ApiDtos.DashboardView;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dashboard: conteos y agregaciones delegadas a SQL")
class DashboardServiceTest {
    @Mock ClienteRepository clientes;
    @Mock ProductoRepository productos;
    @Mock LoteRepository lotes;
    @Mock PedidoRepository pedidos;
    @Mock ExportacionRepository exportaciones;
    @Mock InspeccionCalidadRepository inspecciones;
    @Mock JdbcTemplate jdbc;
    @InjectMocks DashboardService service;

    @Test void summary_shouldMapEveryRepositoryCountToItsOwnField() {
        when(clientes.count()).thenReturn(1L); when(productos.count()).thenReturn(2L); when(lotes.count()).thenReturn(3L);
        when(pedidos.count()).thenReturn(4L); when(exportaciones.count()).thenReturn(5L); when(inspecciones.count()).thenReturn(6L);

        assertEquals(new DashboardView(1, 2, 3, 4, 5, 6), service.resumen());

        verifyNoInteractions(jdbc);
    }

    @Test void summary_whenRepositoriesEmpty_shouldReturnAllZeros() {
        assertEquals(new DashboardView(0, 0, 0, 0, 0, 0), service.resumen());

        verify(clientes).count(); verify(productos).count(); verify(lotes).count();
        verify(pedidos).count(); verify(exportaciones).count(); verify(inspecciones).count();
    }

    @Test void operationalSummary_shouldKeepFilteredCountsDecimalStockAndTimeline() {
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Long.class))).thenReturn(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
        when(jdbc.queryForObject(contains("SUM(cantidad_disponible_kg)"), eq(BigDecimal.class))).thenReturn(new BigDecimal("125.375"));
        var states = List.<Map<String, Object>>of(Map.of("estado", "ADUANA", "total", 2L));
        var events = List.<Map<String, Object>>of(Map.of("origen", "CALIDAD", "descripcion", "Aprobado"));
        when(jdbc.queryForList(contains("GROUP BY estado"))).thenReturn(states);
        when(jdbc.queryForList(contains("UNION ALL"))).thenReturn(events);

        var result = service.operativo();

        assertEquals(List.of(1L,2L,3L,4L,new BigDecimal("125.375"),5L,6L,7L,8L,9L,states,events), new ArrayList<>(result.values()));
        verify(jdbc).queryForObject(contains("estado='ACTIVO' AND cantidad_disponible_kg > 0"), eq(Long.class));
        verify(jdbc).queryForObject(contains("estado='CONFIRMADO'"), eq(Long.class));
        verify(jdbc).queryForObject(contains("resultado='APROBADO'"), eq(Long.class));
        verify(jdbc).queryForList(contains("ORDER BY fecha DESC LIMIT 10"));
    }

    @Test void operationalSummary_whenDatabaseAggregatesEmpty_shouldKeepZeroStockAndEmptyGroups() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(jdbc.queryForObject(contains("COALESCE(SUM(cantidad_disponible_kg),0)"), eq(BigDecimal.class))).thenReturn(BigDecimal.ZERO);

        var result = service.operativo();

        assertEquals(BigDecimal.ZERO, result.get("stockDisponibleKg")); assertEquals(0L, result.get("clientesActivos"));
        assertEquals(List.of(), result.get("exportacionesPorEstado")); assertEquals(List.of(), result.get("ultimosEventos"));
    }
}
