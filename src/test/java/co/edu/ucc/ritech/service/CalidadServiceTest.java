package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.domain.*;
import co.edu.ucc.ritech.repository.*;
import co.edu.ucc.ritech.web.ApiDtos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static co.edu.ucc.ritech.support.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Calidad: inspecciones, inspector e historial del lote")
class CalidadServiceTest {
    @Mock InspeccionCalidadRepository inspecciones;
    @Mock LoteRepository lotes;
    @Mock CurrentUserService currentUser;
    @InjectMocks CalidadService service;
    @Captor ArgumentCaptor<InspeccionCalidad> inspeccionCaptor;

    private InspeccionCalidad inspection() {
        InspeccionCalidad i = new InspeccionCalidad(); i.setId(5L); i.setLote(lote(2L));
        i.setInspector(usuario()); i.setFechaInspeccion(TIME); i.setResultado("APROBADO"); return i;
    }

    @Test void createInspection_whenLotMissing_shouldNotResolveInspectorOrSave() {
        var req = new InspeccionRequest(99L, null, null, null, null, null, "APROBADO");

        assertEquals("Lote no encontrado", assertThrowsExactly(IllegalArgumentException.class, () -> service.crear(req)).getMessage());

        verifyNoInteractions(inspecciones, currentUser);
    }

    @Test void createInspection_whenValid_shouldPersistMeasurementsIdentityAndNormalizedResult() {
        Lote l = lote(2L); Usuario actor = usuario();
        when(lotes.findById(2L)).thenReturn(Optional.of(l)); when(currentUser.get()).thenReturn(actor);
        when(inspecciones.save(any())).thenAnswer(inv -> { InspeccionCalidad i = inv.getArgument(0); i.setId(5L); return i; });

        var result = service.crear(new InspeccionRequest(2L, new BigDecimal("12.5"), "Buena", "Frutal", "Ninguno", "Nota", "aprobado"));

        verify(inspecciones).save(inspeccionCaptor.capture()); var saved = inspeccionCaptor.getValue();
        assertAll(() -> assertSame(l, saved.getLote()), () -> assertSame(actor, saved.getInspector()),
                () -> assertNotNull(saved.getFechaInspeccion()), () -> assertEquals("APROBADO", result.resultado()),
                () -> assertEquals("Ana Inspectora", result.inspector()), () -> assertEquals(new BigDecimal("12.5"), result.humedadPorcentaje()),
                () -> assertEquals("Buena", result.evaluacionTaza()), () -> assertEquals("Frutal", result.aroma()),
                () -> assertEquals("Ninguno", result.defectos()), () -> assertEquals("Nota", result.observaciones()));
    }

    @Test void listInspections_shouldMapLotAndInspectorInRepositoryOrder() {
        when(inspecciones.findAllByOrderByFechaInspeccionDesc()).thenReturn(List.of(inspection()));

        var result = service.listar().getFirst();

        assertEquals("LOTE-2", result.lote()); assertEquals("Ana Inspectora", result.inspector()); assertEquals(TIME, result.fechaInspeccion());
    }

    @Test void lotInspectionHistory_whenLotMissing_shouldNotReadInspections() {
        assertEquals("Lote no encontrado", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.historialPorLote(99L)).getMessage());

        verifyNoInteractions(inspecciones);
    }

    @Test void lotInspectionHistory_shouldScopeResultsAndAllowEmptyHistory() {
        when(lotes.existsById(2L)).thenReturn(true);
        when(inspecciones.findByLoteIdOrderByFechaInspeccionDesc(2L)).thenReturn(List.of(inspection()), List.of());

        assertEquals(5L, service.historialPorLote(2L).getFirst().id());
        assertTrue(service.historialPorLote(2L).isEmpty());

        verify(inspecciones, times(2)).findByLoteIdOrderByFechaInspeccionDesc(2L);
    }
}
