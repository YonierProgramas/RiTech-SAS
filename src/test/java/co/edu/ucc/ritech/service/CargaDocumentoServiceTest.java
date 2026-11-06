package co.edu.ucc.ritech.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import static co.edu.ucc.ritech.support.TestFixtures.usuario;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

@ExtendWith(MockitoExtension.class)
@DisplayName("Carga documental: versionado, autoría y compensación de archivos")
class CargaDocumentoServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock CurrentUserService currentUser;
    @Mock DocumentoStorageService storage;
    @InjectMocks CargaDocumentoService service;
    private final MockMultipartFile file = new MockMultipartFile("file", "factura.txt", "text/plain", new byte[]{97,98,99});
    private final DocumentoStorageService.StoredFile stored = new DocumentoStorageService.StoredFile(
            "factura.txt", "4/9/file.txt", "text/plain", 3L,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Path.of("virtual-storage", "4", "9", "file.txt").toAbsolutePath());

    private void givenRequirement() {
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Long.class), eq(9L), eq(4L))).thenReturn(1L);
        when(jdbc.queryForObject(contains("FOR UPDATE"), eq(Long.class), eq(9L))).thenReturn(9L);
        when(currentUser.get()).thenReturn(usuario());
    }
    private void givenStoredFile(Integer firstVersion, Integer... nextVersions) {
        givenRequirement();
        when(storage.store(eq(4L), eq(9L), eq("factura.txt"), eq("text/plain"), aryEq(new byte[]{97,98,99}))).thenReturn(stored);
        when(jdbc.queryForObject(contains("COALESCE(MAX(numero_version),0)+1"), eq(Integer.class), eq(9L))).thenReturn(firstVersion, nextVersions);
    }

    @Test void uploadDocument_shouldLockBeforeAssigningVersionsAndPreserveOldRows() {
        givenStoredFile(1, 2);
        when(jdbc.queryForMap(contains("SELECT id, exportacion_requisito_id"), eq(9L), anyInt()))
                .thenAnswer(inv -> Map.of("numero_version", inv.getArgument(2), "vigente", true, "estado", "CARGADO"));

        var first = service.cargar(4L, 9L, file);
        var second = service.cargar(4L, 9L, file);

        assertAll(() -> assertEquals(1, first.get("numero_version")), () -> assertEquals(2, second.get("numero_version")),
                () -> assertEquals(true, second.get("vigente")), () -> assertEquals("CARGADO", second.get("estado")));
        InOrder order = inOrder(jdbc, storage, currentUser);
        for (int version = 1; version <= 2; version++) {
            order.verify(jdbc).queryForObject(contains("COUNT(*)"), eq(Long.class), eq(9L), eq(4L));
            order.verify(jdbc).queryForObject(contains("FOR UPDATE"), eq(Long.class), eq(9L));
            order.verify(currentUser).get();
            order.verify(storage).store(eq(4L), eq(9L), eq("factura.txt"), eq("text/plain"), any(byte[].class));
            order.verify(jdbc).queryForObject(contains("COALESCE(MAX(numero_version),0)+1"), eq(Integer.class), eq(9L));
            order.verify(jdbc).update(contains("SET vigente=FALSE WHERE exportacion_requisito_id=? AND vigente=TRUE"), eq(9L));
            order.verify(jdbc).update(contains("VALUES (?,?,TRUE,?,?,?,?,?,'CARGADO',?,UTC_TIMESTAMP(6))"),
                    eq(9L), eq(version), eq(stored.name()), eq(stored.relativePath()), eq(stored.mimeType()),
                    eq(stored.size()), eq(stored.sha256()), eq(7L));
            order.verify(jdbc).queryForMap(contains("SELECT id, exportacion_requisito_id"), eq(9L), eq(version));
        }
        verify(jdbc, never()).update(startsWith("DELETE"), any(Object[].class));
        verify(storage, never()).delete(any());
        ArgumentCaptor<String> projection = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForMap(projection.capture(), eq(9L), anyInt());
        assertTrue(projection.getAllValues().stream().noneMatch(sql -> sql.contains("ruta_archivo")),
                "La proyección pública del documento no debe devolver rutas físicas");
    }

    @Test void uploadDocument_whenFileMissingOrEmpty_shouldRejectBeforeDatabaseAccess() {
        var empty = new MockMultipartFile("file", new byte[0]);

        assertAll(() -> assertEquals("Debe seleccionar un archivo", assertThrowsExactly(IllegalArgumentException.class,
                        () -> service.cargar(4L, 9L, null)).getMessage()),
                () -> assertEquals("Debe seleccionar un archivo", assertThrowsExactly(IllegalArgumentException.class,
                        () -> service.cargar(4L, 9L, empty)).getMessage()));

        verifyNoInteractions(jdbc, storage, currentUser);
    }

    @Test void uploadDocument_whenRequirementNotOwnedByExport_shouldNotLockOrStore() {
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Long.class), eq(9L), eq(4L))).thenReturn(0L);

        assertEquals("Requisito no encontrado en la exportación", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.cargar(4L, 9L, file)).getMessage());

        verify(jdbc, never()).queryForObject(contains("FOR UPDATE"), eq(Long.class), anyLong());
        verifyNoInteractions(storage, currentUser); verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void uploadDocument_whenReadingBytesFails_shouldPreserveCauseWithoutStorageWrites() throws IOException {
        givenRequirement(); MultipartFile broken = mock(MultipartFile.class);
        IOException cause = new IOException("Read failure"); when(broken.getBytes()).thenThrow(cause);

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.cargar(4L, 9L, broken));

        assertEquals("No se pudo cargar el documento", error.getMessage()); assertSame(cause, error.getCause());
        verifyNoInteractions(storage); verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void uploadDocument_whenStorageRejectsFile_shouldPreserveBusinessException() {
        givenRequirement(); var cause = new IllegalArgumentException("MIME no permitido");
        when(storage.store(anyLong(), anyLong(), anyString(), anyString(), any(byte[].class))).thenThrow(cause);

        assertSame(cause, assertThrowsExactly(IllegalArgumentException.class, () -> service.cargar(4L, 9L, file)));

        verify(storage, never()).delete(any()); verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void uploadDocument_whenStorageFails_shouldWrapCauseAndNotChangeVersions() {
        givenRequirement(); var cause = new IllegalStateException("Disk failure");
        when(storage.store(anyLong(), anyLong(), anyString(), anyString(), any(byte[].class))).thenThrow(cause);

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.cargar(4L, 9L, file));

        assertSame(cause, error.getCause()); verify(storage, never()).delete(any());
        verify(jdbc, never()).queryForObject(contains("MAX(numero_version)"), eq(Integer.class), anyLong());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void uploadDocument_whenInsertFails_shouldDeleteOnlyNewPhysicalFile() {
        givenStoredFile(1); var cause = new DataAccessResourceFailureException("Insert failure");
        when(jdbc.update(contains("SET vigente=FALSE"), eq(9L))).thenReturn(1);
        when(jdbc.update(contains("INSERT INTO documentos_exportacion"), any(Object[].class))).thenThrow(cause);

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.cargar(4L, 9L, file));

        assertSame(cause, error.getCause()); verify(storage).delete(stored.physicalPath());
        verify(jdbc, never()).queryForMap(anyString(), any(Object[].class));
    }

    @Test void uploadDocument_whenResultReadFails_shouldCompensateStoredFileAndPropagateCause() {
        givenStoredFile(1); var cause = new DataAccessResourceFailureException("Select failure");
        when(jdbc.queryForMap(anyString(), eq(9L), eq(1))).thenThrow(cause);

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.cargar(4L, 9L, file));

        assertSame(cause, error.getCause()); verify(storage).delete(stored.physicalPath());
    }
}
