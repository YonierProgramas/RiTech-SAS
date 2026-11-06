package co.edu.ucc.ritech.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Archivos: tipos, firmas, límites, rutas y SHA-256")
class DocumentoStorageServiceTest {
    @TempDir Path temp;

    static Stream<Arguments> permittedFiles() {
        return Stream.of(
                Arguments.of("pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)),
                Arguments.of("png", "image/png", new byte[]{(byte)137,80,78,71,13,10,26,10}),
                Arguments.of("jpg", "image/jpeg", new byte[]{(byte)255,(byte)216,(byte)255}),
                Arguments.of("jpeg", "image/jpeg", new byte[]{(byte)255,(byte)216,(byte)255}),
                Arguments.of("txt", "text/plain", "abc".getBytes(StandardCharsets.US_ASCII)),
                Arguments.of("csv", "text/csv", "a,b\n1,2".getBytes(StandardCharsets.US_ASCII)));
    }

    @ParameterizedTest @MethodSource("permittedFiles")
    void storeDocument_whenTypeAndSignatureValid_shouldWriteExactBytesAndSafeMetadata(String ext, String mime, byte[] bytes) throws Exception {
        var service = new DocumentoStorageService(temp.toString(), 1024);

        var saved = service.store(4L, 9L, "factura." + ext, mime, bytes);

        assertAll(() -> assertEquals("factura." + ext, saved.name()), () -> assertEquals(mime, saved.mimeType()),
                () -> assertEquals(bytes.length, saved.size()), () -> assertArrayEquals(bytes, Files.readAllBytes(saved.physicalPath())),
                () -> assertFalse(Path.of(saved.relativePath()).isAbsolute()),
                () -> assertTrue(saved.physicalPath().startsWith(temp)),
                () -> assertTrue(saved.relativePath().startsWith("4/9/")), () -> assertEquals(64, saved.sha256().length()));
    }

    @Test void storeDocument_shouldCalculateKnownSha256Digest() {
        var saved = new DocumentoStorageService(temp.toString(), 10).store(1L, 2L, "a.txt", "text/plain", new byte[]{97,98,99});

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", saved.sha256());
    }

    @Test
    @DisplayName("PNG: rechazar un encabezado que solo coincide en los primeros cuatro bytes")
    void validateFile_whenPngHeaderOnlyMatchesFirstFourBytes_shouldRejectFile() throws Exception {
        var service = new DocumentoStorageService(temp.toString(), 100);
        byte[] prefixOnly = {(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0};

        var error = assertThrowsExactly(IllegalArgumentException.class,
                () -> service.store(1L, 2L, "invalid.png", "image/png", prefixOnly));

        assertEquals("El contenido del archivo no coincide con su tipo", error.getMessage());
        byte[] validHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        for (int index = 4; index < validHeader.length; index++) {
            byte[] invalidHeader = validHeader.clone();
            invalidHeader[index] = 0;
            assertThrowsExactly(IllegalArgumentException.class,
                    () -> service.store(1L, 2L, "invalid.png", "image/png", invalidHeader),
                    "Cada byte de la segunda mitad de la firma debe validarse: índice " + index);
        }
        try (var paths = Files.list(temp)) { assertEquals(0, paths.count(), "No deben almacenarse los PNG rechazados"); }
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"archivo", "archivo.", "archivo.exe"})
    void storeDocument_whenNameOrExtensionInvalid_shouldRejectWithoutWriting(String name) throws Exception {
        var service = new DocumentoStorageService(temp.toString(), 10);

        assertEquals("Extensión de archivo no permitida", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.store(1L, 2L, name, "text/plain", new byte[]{1})).getMessage());

        try (var paths = Files.list(temp)) { assertEquals(0, paths.count()); }
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"text/plain"})
    void storeDocument_whenMimeMissingOrMismatched_shouldReject(String mime) {
        var service = new DocumentoStorageService(temp.toString(), 100);

        assertEquals("El MIME type no corresponde con la extensión", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.store(1L, 2L, "x.pdf", mime, "%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).getMessage());
    }

    @Test void storeDocument_shouldNormalizeExtensionAndMimeParameters() {
        var saved = new DocumentoStorageService(temp.toString(), 100).store(1L, 2L, "X.PDF",
                " APPLICATION/PDF; charset=binary", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

        assertEquals("X.PDF", saved.name()); assertEquals("application/pdf", saved.mimeType());
        assertTrue(saved.relativePath().endsWith(".pdf"));
    }

    @ParameterizedTest @CsvSource({"pdf,application/pdf", "png,image/png", "jpg,image/jpeg"})
    void storeDocument_whenBinarySignatureWrong_shouldReject(String ext, String mime) {
        var service = new DocumentoStorageService(temp.toString(), 100);

        assertEquals("El contenido del archivo no coincide con su tipo", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.store(1L, 2L, "x." + ext, mime, new byte[8])).getMessage());
    }

    @Test void storeDocument_whenBytesNullOrEmpty_shouldRejectBeforeNameParsing() {
        var service = new DocumentoStorageService(temp.toString(), 10);

        assertAll(() -> assertEquals("El archivo está vacío", assertThrowsExactly(IllegalArgumentException.class,
                        () -> service.store(1L, 2L, null, null, null)).getMessage()),
                () -> assertEquals("El archivo está vacío", assertThrowsExactly(IllegalArgumentException.class,
                        () -> service.store(1L, 2L, null, null, new byte[0])).getMessage()));
    }

    @Test void storeDocument_shouldAcceptExactLimitAndRejectOneByteOver() {
        var service = new DocumentoStorageService(temp.toString(), 4);

        assertEquals(4, service.store(1L, 2L, "x.txt", "text/plain", new byte[4]).size());
        assertEquals("El archivo supera el tamaño máximo permitido", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.store(1L, 2L, "x.txt", "text/plain", new byte[5])).getMessage());
    }

    @ParameterizedTest @ValueSource(strings = {"../../factura.txt", "..\\..\\factura.txt"})
    void storeDocument_whenOriginalNameContainsTraversal_shouldKeepOnlyBasename(String name) {
        var saved = new DocumentoStorageService(temp.toString(), 10).store(1L, 2L, name, "text/plain", new byte[]{1});

        assertEquals("factura.txt", saved.name()); assertTrue(saved.physicalPath().startsWith(temp));
        assertFalse(saved.relativePath().contains(".."));
    }

    @Test void storeDocument_whenOriginalNameLong_shouldBoundMetadataWithoutUsingItAsPath() {
        var saved = new DocumentoStorageService(temp.toString(), 10).store(1L, 2L, "a".repeat(205) + ".txt", "text/plain", new byte[]{1});

        assertEquals(200, saved.name().length()); assertTrue(saved.name().endsWith(".txt"));
        assertFalse(saved.relativePath().contains("a".repeat(100)));
    }

    @Test void storeDocument_whenFilesystemFails_shouldExposeStorageErrorAndCause() throws Exception {
        Path notDirectory = Files.createFile(temp.resolve("root-file"));
        var service = new DocumentoStorageService(notDirectory.toString(), 10);

        var error = assertThrowsExactly(IllegalStateException.class,
                () -> service.store(1L, 2L, "x.txt", "text/plain", new byte[]{1}));

        assertEquals("No se pudo almacenar el archivo", error.getMessage()); assertNotNull(error.getCause());
    }

    @Test void storeDocument_whenSameNameUploadedTwice_shouldKeepBothPhysicalFiles() {
        var service = new DocumentoStorageService(temp.toString(), 10);
        var first = service.store(1L, 2L, "x.txt", "text/plain", new byte[]{1});
        var second = service.store(1L, 2L, "x.txt", "text/plain", new byte[]{2});

        assertNotEquals(first.relativePath(), second.relativePath());
        assertTrue(Files.exists(first.physicalPath())); assertTrue(Files.exists(second.physicalPath()));
    }

    @Test void deleteDocument_whenInsideStorage_shouldRemoveFileIdempotently() {
        var service = new DocumentoStorageService(temp.toString(), 10);
        var stored = service.store(1L, 2L, "x.txt", "text/plain", new byte[]{1});

        service.delete(stored.physicalPath()); service.delete(stored.physicalPath());

        assertFalse(Files.exists(stored.physicalPath()));
    }

    @Test void deleteDocument_whenOutsideStorageOrNull_shouldLeaveExternalFileUntouched() throws Exception {
        Path root = Files.createDirectory(temp.resolve("root")); Path outside = Files.write(temp.resolve("outside.txt"), new byte[]{7});
        var service = new DocumentoStorageService(root.toString(), 10);

        service.delete(root.resolve("../outside.txt")); service.delete(null);

        assertArrayEquals(new byte[]{7}, Files.readAllBytes(outside));
    }
}
