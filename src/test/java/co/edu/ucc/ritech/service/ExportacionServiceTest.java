package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.domain.*;
import co.edu.ucc.ritech.repository.*;
import co.edu.ucc.ritech.web.ApiDtos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.*;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.*;
import static co.edu.ucc.ritech.support.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Exportaciones: expediente, checklist, revisión y despacho")
class ExportacionServiceTest {
    @Mock ExportacionRepository exportaciones;
    @Mock PedidoRepository pedidos;
    @Mock RequisitoDocumentalRepository requisitos;
    @Mock ExportacionRequisitoRepository exportacionRequisitos;
    @Mock HistorialEstadoRepository historial;
    @Mock CurrentUserService currentUser;
    @Mock JdbcTemplate jdbc;
    @InjectMocks ExportacionService service;
    @Captor ArgumentCaptor<ExportacionRequisito> requisitoCaptor;
    @Captor ArgumentCaptor<HistorialEstado> historialCaptor;

    private Exportacion givenExport() {
        Exportacion e = exportacion(4L); when(exportaciones.findById(4L)).thenReturn(Optional.of(e)); return e;
    }
    private void givenStatePersistence() {
        when(exportaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(currentUser.get()).thenReturn(usuario());
    }
    private void givenChecklist(boolean complete) {
        doAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class); when(rs.next()).thenReturn(true);
            when(rs.getBoolean(1)).thenReturn(complete);
            return inv.<ResultSetExtractor<Boolean>>getArgument(1).extractData(rs);
        }).when(jdbc).query(contains("checklist_completo"), ArgumentMatchers.<ResultSetExtractor<Boolean>>any(), eq(4L));
    }
    private void givenDocumentMembership(int count) {
        when(exportaciones.existsById(4L)).thenReturn(true);
        doAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class); when(rs.next()).thenReturn(true); when(rs.getInt(1)).thenReturn(count);
            return inv.<ResultSetExtractor<Integer>>getArgument(1).extractData(rs);
        }).when(jdbc).query(contains("SELECT COUNT(*) FROM documentos_exportacion"),
                ArgumentMatchers.<ResultSetExtractor<Integer>>any(), eq(8L), eq(4L));
    }

    @Test void listExports_shouldMapOrderClientAndResponsible() {
        when(exportaciones.findAllByOrderByCreadoEnDesc()).thenReturn(List.of(exportacion(4L)));

        var result = service.listar().getFirst();

        assertAll(() -> assertEquals("EXP-4", result.codigo()), () -> assertEquals("PED-3", result.pedido()),
                () -> assertEquals("Cliente 1", result.cliente()), () -> assertEquals("Ana Inspectora", result.responsable()));
    }

    @Test void getExport_whenExisting_shouldExposeDocumentState() {
        givenExport();

        var result = service.detalle(4L);

        assertEquals(4L, result.id()); assertEquals("PREPARACION", result.estado()); assertFalse(result.requisitosRevisados());
    }

    @ParameterizedTest @ValueSource(strings = {"detalle", "checklist", "tramites", "documento", "estado", "crearTramite"})
    void exportOperation_whenMissing_shouldRejectWithoutWriting(String operation) {
        var error = assertThrowsExactly(IllegalArgumentException.class, () -> {
            switch (operation) {
                case "detalle" -> service.detalle(99L);
                case "checklist" -> service.checklist(99L);
                case "tramites" -> service.tramites(99L);
                case "documento" -> service.cambiarDocumento(99L, 8L, new DocumentoEstadoRequest("APROBADO", null));
                case "estado" -> service.cambiarEstado(99L, new CambioEstadoRequest("ADUANA", "Cambio"));
                default -> service.crearTramite(99L, new TramiteRequest("A", "B", "C", "D", null));
            }
        });

        assertEquals("Exportación no encontrada", error.getMessage());
        verify(exportaciones, never()).save(any()); verifyNoInteractions(jdbc, currentUser, historial);
    }

    @Test void createExport_whenOrderMissing_shouldNotCreateAnything() {
        var error = assertThrowsExactly(IllegalArgumentException.class,
                () -> service.crear(new ExportacionRequest(99L, DATE)));

        assertEquals("Pedido no encontrado", error.getMessage());
        verifyNoInteractions(exportaciones, requisitos, exportacionRequisitos, historial, currentUser);
    }

    @Test void createExport_whenOrderAlreadyHasExport_shouldRejectDuplicate() {
        when(pedidos.findById(3L)).thenReturn(Optional.of(pedido(3L)));
        when(exportaciones.existsByPedidoId(3L)).thenReturn(true);

        var error = assertThrowsExactly(IllegalArgumentException.class,
                () -> service.crear(new ExportacionRequest(3L, DATE)));

        assertEquals("El pedido ya tiene una exportación asociada", error.getMessage());
        verify(exportaciones, never()).save(any()); verifyNoInteractions(exportacionRequisitos, historial, currentUser);
    }

    @Test void createExport_whenOrderNotConfirmed_shouldNotSave() {
        Pedido p = pedido(3L); p.setEstado("CANCELADO"); when(pedidos.findById(3L)).thenReturn(Optional.of(p));

        assertEquals("El pedido debe estar CONFIRMADO", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.crear(new ExportacionRequest(3L, DATE))).getMessage());

        verify(exportaciones, never()).save(any()); verifyNoInteractions(requisitos, historial, currentUser);
    }

    @Test void createExport_whenValid_shouldCopyApplicableRequirementsAndInitialAudit() {
        when(pedidos.findById(3L)).thenReturn(Optional.of(pedido(3L)));
        when(currentUser.get()).thenReturn(usuario());
        when(exportaciones.save(any())).thenAnswer(inv -> { Exportacion e = inv.getArgument(0); e.setId(4L); return e; });
        when(requisitos.findByActivoTrueOrderByIdAsc()).thenReturn(List.of(requisito(1L, null), requisito(2L, "es"), requisito(3L, "US")));

        var result = service.crear(new ExportacionRequest(3L, DATE));

        verify(exportacionRequisitos, times(2)).save(requisitoCaptor.capture());
        verify(historial).save(historialCaptor.capture());
        var saved = requisitoCaptor.getAllValues(); var audit = historialCaptor.getValue();
        assertAll(() -> assertEquals("PREPARACION", result.estado()), () -> assertFalse(result.requisitosRevisados()),
                () -> assertTrue(result.codigo().matches("EXP-[A-F0-9]{8}")),
                () -> assertEquals(DATE, result.fechaSalidaPrevista()),
                () -> assertEquals(List.of(1L, 2L), saved.stream().map(r -> r.getRequisito().getId()).toList()),
                () -> assertEquals("Documento 1", saved.getFirst().getNombreRequisito()),
                () -> assertTrue(saved.getFirst().getObligatorio()), () -> assertNotNull(saved.getFirst().getCreadoEn()),
                () -> assertSame(saved.getFirst().getExportacion(), audit.getExportacion()),
                () -> assertNull(audit.getEstadoAnterior()), () -> assertEquals("PREPARACION", audit.getEstadoNuevo()),
                () -> assertEquals(7L, audit.getUsuario().getId()), () -> assertNotNull(audit.getFechaEvento()));
    }

    @Test void requirements_shouldProjectTheActiveCatalogueContract() {
        when(requisitos.findByActivoTrueOrderByIdAsc()).thenReturn(List.of(requisito(1L, null)));

        var result = service.requisitos().getFirst();

        assertEquals(new RequisitoView(1L, "REQ-1", "Documento 1", true, true), result);
    }

    @Test void checklist_whenExportExists_shouldBindExportIdAndReturnViewRows() {
        when(exportaciones.existsById(4L)).thenReturn(true);
        var rows = List.<Map<String, Object>>of(Map.of("exportacion_requisito_id", 8L, "estado", "PENDIENTE"));
        when(jdbc.queryForList(contains("v_checklist_exportacion"), eq(4L))).thenReturn(rows);

        assertSame(rows, service.checklist(4L));

        verify(jdbc).queryForList(contains("WHERE c.exportacion_id=? ORDER BY c.exportacion_requisito_id"), eq(4L));
    }

    @ParameterizedTest @ValueSource(strings = {"APROBADO", "RECHAZADO"})
    void reviewDocument_shouldRecordReviewerAndTrimObservation(String state) {
        givenDocumentMembership(1); when(currentUser.get()).thenReturn(usuario());
        when(jdbc.update(contains("revisado_por=?"), eq(state), eq(7L), eq("Revisado"), eq(8L))).thenReturn(1);
        var view = Map.<String, Object>of("estado", state);
        when(jdbc.queryForMap(contains("v_checklist_exportacion"), eq(8L))).thenReturn(view);

        assertSame(view, service.cambiarDocumento(4L, 8L, new DocumentoEstadoRequest(state, " Revisado ")));

        verify(jdbc).update(contains("revisado_en=UTC_TIMESTAMP(6)"), eq(state), eq(7L), eq("Revisado"), eq(8L));
    }

    @Test void setDocumentUnderReview_shouldAllowNullObservationWithoutReviewer() {
        givenDocumentMembership(1);
        when(jdbc.update(contains("SET estado=?, observacion_revision=?"), eq("EN_REVISION"), isNull(), eq(8L))).thenReturn(1);
        when(jdbc.queryForMap(anyString(), eq(8L))).thenReturn(Map.of("estado", "EN_REVISION"));

        assertEquals("EN_REVISION", service.cambiarDocumento(4L, 8L,
                new DocumentoEstadoRequest("EN_REVISION", null)).get("estado"));

        verifyNoInteractions(currentUser);
    }

    @Test void reviewDocument_whenNotOwnedByExport_shouldRejectBeforeUpdate() {
        givenDocumentMembership(0);

        var error = assertThrowsExactly(IllegalArgumentException.class,
                () -> service.cambiarDocumento(4L, 8L, new DocumentoEstadoRequest("APROBADO", null)));

        assertEquals("Documento no encontrado en la exportación", error.getMessage());
        verify(jdbc, never()).update(anyString(), any(Object[].class)); verifyNoInteractions(currentUser);
    }

    @Test void updateDocument_whenNoRowUpdated_shouldNotReadAStaleResult() {
        givenDocumentMembership(1);

        assertEquals("No se pudo actualizar el documento", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.cambiarDocumento(4L, 8L, new DocumentoEstadoRequest("CARGADO", null))).getMessage());

        verify(jdbc, never()).queryForMap(anyString(), any(Object[].class));
    }

    @Test void createExternalProcedure_shouldPersistTrimmedDataActorAndReturnLastProcedure() {
        when(exportaciones.existsById(4L)).thenReturn(true); when(currentUser.get()).thenReturn(usuario());
        when(jdbc.update(contains("INSERT INTO tramites_externos"), eq(4L), eq("ICA"), eq("Registro"), eq("REF"), eq("RADICADO"), isNull(), eq(7L))).thenReturn(1);
        var first = new TramiteView(1L, "ICA", "Registro", "OLD", "RADICADO", null, TIME, "Ana");
        var last = new TramiteView(2L, "ICA", "Registro", "REF", "RADICADO", null, TIME, "Ana");
        when(jdbc.query(contains("FROM tramites_externos"), ArgumentMatchers.<RowMapper<TramiteView>>any(), eq(4L))).thenReturn(List.of(first, last));

        assertSame(last, service.crearTramite(4L, new TramiteRequest(" ICA ", " Registro ", " REF ", " RADICADO ", null)));
    }

    @Test void createExternalProcedure_whenInsertFails_shouldNotListProcedures() {
        when(exportaciones.existsById(4L)).thenReturn(true); when(currentUser.get()).thenReturn(usuario());

        assertEquals("No se pudo registrar el trámite externo", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.crearTramite(4L, new TramiteRequest("ICA", "Registro", "REF", "RADICADO", null))).getMessage());

        verify(jdbc, never()).query(anyString(), ArgumentMatchers.<RowMapper<TramiteView>>any(), anyLong());
    }

    @Test void listProcedures_shouldMapSqlColumnsAndUseStableChronologicalOrder() {
        when(exportaciones.existsById(4L)).thenReturn(true);
        doAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class); when(rs.getLong(1)).thenReturn(2L);
            when(rs.getString(2)).thenReturn("ICA"); when(rs.getString(3)).thenReturn("Registro");
            when(rs.getString(4)).thenReturn("REF"); when(rs.getString(5)).thenReturn("RADICADO");
            when(rs.getString(6)).thenReturn("Nota"); when(rs.getTimestamp(7)).thenReturn(Timestamp.valueOf(TIME));
            when(rs.getString(8)).thenReturn("Ana");
            return List.of(inv.<RowMapper<TramiteView>>getArgument(1).mapRow(rs, 0));
        }).when(jdbc).query(contains("ORDER BY t.registrado_en, t.id"), ArgumentMatchers.<RowMapper<TramiteView>>any(), eq(4L));

        assertEquals(new TramiteView(2L, "ICA", "Registro", "REF", "RADICADO", "Nota", TIME, "Ana"), service.tramites(4L).getFirst());
    }

    @Test void stateHistory_shouldMapAuditIdentityAndPreserveRepositoryOrder() {
        HistorialEstado h = new HistorialEstado(); h.setId(6L); h.setUsuario(usuario()); h.setFechaEvento(TIME);
        h.setEstadoAnterior("PREPARACION"); h.setEstadoNuevo("ADUANA"); h.setObservacion("Cambio");
        when(historial.findByExportacionIdOrderByFechaEventoAscIdAsc(4L)).thenReturn(List.of(h));

        assertEquals(new HistorialView(6L, "PREPARACION", "ADUANA", TIME, "Ana Inspectora", "Cambio"), service.historial(4L).getFirst());
    }

    @Test void changeState_whenUnchanged_shouldNotCheckDocumentsOrWriteHistory() {
        givenExport();

        assertEquals("El nuevo estado debe ser diferente al actual", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.cambiarEstado(4L, new CambioEstadoRequest("PREPARACION", "Sin cambio"))).getMessage());

        verifyNoInteractions(jdbc, historial, currentUser); verify(exportaciones, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"LISTA_DESPACHO", "ADUANA", "EN_TRANSITO", "ENTREGADA"})
    void changeState_whenMandatoryDocumentsPending_shouldBlockEveryDispatchStage(String state) {
        Exportacion e = givenExport(); givenChecklist(false);

        var error = assertThrowsExactly(IllegalArgumentException.class,
                () -> service.cambiarEstado(4L, new CambioEstadoRequest(state, "Cambio")));

        assertEquals("Existen requisitos documentales obligatorios pendientes", error.getMessage());
        assertEquals("PREPARACION", e.getEstado()); verify(exportaciones, never()).save(any());
        verifyNoInteractions(historial, currentUser);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void startTransit_shouldSetDepartureOnceAndRecordStateHistory(boolean alreadyDeparted) {
        Exportacion e = givenExport(); if (alreadyDeparted) e.setFechaSalidaReal(TIME);
        givenChecklist(true); givenStatePersistence();

        var result = service.cambiarEstado(4L, new CambioEstadoRequest("EN_TRANSITO", " Inicio "));

        assertNotNull(result.fechaSalidaReal()); if (alreadyDeparted) assertEquals(TIME, result.fechaSalidaReal());
        verify(historial).save(historialCaptor.capture()); var h = historialCaptor.getValue();
        assertAll(() -> assertEquals("PREPARACION", h.getEstadoAnterior()), () -> assertEquals("EN_TRANSITO", h.getEstadoNuevo()),
                () -> assertEquals("Inicio", h.getObservacion()), () -> assertSame(e, h.getExportacion()),
                () -> assertNotNull(h.getFechaEvento()), () -> assertEquals(7L, h.getUsuario().getId()));
    }

    @Test void changeState_whenDocumentControlViewHasNoRow_shouldFailClosed() {
        givenExport();
        doAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class); when(rs.next()).thenReturn(false);
            return inv.<ResultSetExtractor<Boolean>>getArgument(1).extractData(rs);
        }).when(jdbc).query(contains("checklist_completo"), ArgumentMatchers.<ResultSetExtractor<Boolean>>any(), eq(4L));

        assertEquals("Existen requisitos documentales obligatorios pendientes", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.cambiarEstado(4L, new CambioEstadoRequest("LISTA_DESPACHO", "Cambio"))).getMessage());

        verify(exportaciones, never()).save(any()); verifyNoInteractions(historial, currentUser);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void deliverExport_shouldSetArrivalAndPreserveExistingDeparture(boolean alreadyDeparted) {
        Exportacion e = givenExport(); if (alreadyDeparted) e.setFechaSalidaReal(TIME);
        givenChecklist(true); givenStatePersistence();

        var result = service.cambiarEstado(4L, new CambioEstadoRequest("ENTREGADA", "Entregada"));

        assertNotNull(result.fechaEntrega()); assertNotNull(result.fechaSalidaReal());
        if (alreadyDeparted) assertEquals(TIME, result.fechaSalidaReal());
        verify(historial).save(any());
    }

    @Test void cancelExport_shouldBypassDispatchChecklistButKeepAuditTrail() {
        givenExport(); givenStatePersistence();

        assertEquals("CANCELADA", service.cambiarEstado(4L, new CambioEstadoRequest("CANCELADA", "Cancelacion")).estado());

        verifyNoInteractions(jdbc); verify(historial).save(any());
    }
}
