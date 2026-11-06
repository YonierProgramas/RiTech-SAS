package co.edu.ucc.ritech.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static co.edu.ucc.ritech.support.TestFixtures.TIME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Trazabilidad: eventos asociados y ausencia de resultados")
class TrazabilidadServiceTest {
    @Mock JdbcTemplate jdbc;
    @InjectMocks TrazabilidadService service;

    @Test void traceLot_shouldPreserveEventFieldsAndOrderProvidedByDatabase() {
        var events = List.<Map<String, Object>>of(
                Map.of("tipo_evento", "RECEPCION", "fecha_evento", TIME, "usuario_id", 7L, "referencia_id", 1L),
                Map.of("tipo_evento", "CALIDAD", "fecha_evento", TIME.plusHours(1), "usuario_id", 8L, "referencia_id", 2L));
        when(jdbc.queryForList(contains("v_trazabilidad_lote"), eq("LOTE-2"))).thenReturn(events);

        var result = service.consultar("LOTE-2");

        assertEquals("LOTE-2", result.codigoLote()); assertSame(events, result.eventos());
        assertEquals(8L, result.eventos().getLast().get("usuario_id"));
        verify(jdbc).queryForList(contains("ORDER BY fecha_evento, origen_evento, referencia_id"), eq("LOTE-2"));
    }

    @Test void traceLot_whenNoEvents_shouldRejectInsteadOfReturningInventedData() {
        assertEquals("No se encontró trazabilidad para el lote indicado", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.consultar("SIN-EVENTOS")).getMessage());

        verify(jdbc).queryForList(anyString(), eq("SIN-EVENTOS"));
    }

    @Test void traceLot_whenDatabaseFails_shouldNotDisguiseFailureAsMissingLot() {
        var cause = new DataAccessResourceFailureException("Connection unavailable");
        when(jdbc.queryForList(anyString(), eq("LOTE-2"))).thenThrow(cause);

        assertSame(cause, assertThrowsExactly(DataAccessResourceFailureException.class, () -> service.consultar("LOTE-2")));
    }
}
