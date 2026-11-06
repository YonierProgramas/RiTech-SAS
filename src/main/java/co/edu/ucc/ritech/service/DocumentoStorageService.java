package co.edu.ucc.ritech.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentoStorageService {
    private static final Map<String, String> TYPES = Map.of(
            "pdf", "application/pdf", "png", "image/png", "jpg", "image/jpeg",
            "jpeg", "image/jpeg", "txt", "text/plain", "csv", "text/csv");
    private final Path root;
    private final long maxBytes;

    public DocumentoStorageService(@Value("${ritech.documentos.storage-dir:./uploads/documentos}") String directory,
                                   @Value("${ritech.documentos.max-bytes:10485760}") long maxBytes) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public StoredFile store(Long exportacionId, Long requisitoId, String originalName, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("El archivo está vacío");
        if (bytes.length > maxBytes) throw new IllegalArgumentException("El archivo supera el tamaño máximo permitido");
        String safeName = Paths.get(originalName == null ? "" : originalName.replace('\\', '/')).getFileName().toString();
        int dot = safeName.lastIndexOf('.');
        if (dot <= 0 || dot == safeName.length() - 1) throw new IllegalArgumentException("Extensión de archivo no permitida");
        String extension = safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expectedType = TYPES.get(extension);
        if (expectedType == null) throw new IllegalArgumentException("Extensión de archivo no permitida");
        String suppliedType = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!expectedType.equals(suppliedType)) throw new IllegalArgumentException("El MIME type no corresponde con la extensión");
        validateSignature(extension, bytes);
        String sha = sha256(bytes);
        String relative = exportacionId + "/" + requisitoId + "/" + UUID.randomUUID() + "." + extension;
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Ruta de archivo inválida");
        try {
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo almacenar el archivo", e);
        }
        return new StoredFile(safeName.length() > 200 ? safeName.substring(safeName.length() - 200) : safeName,
                relative.replace('\\', '/'), expectedType, bytes.length, sha, target);
    }

    public void delete(Path path) {
        if (path != null && path.normalize().startsWith(root)) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { }
        }
    }

    private static void validateSignature(String ext, byte[] b) {
        boolean valid = switch (ext) {
            case "pdf" -> b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
            case "png" -> b.length >= 8
                    && (b[0] & 255) == 137 && b[1] == 80 && b[2] == 78 && b[3] == 71
                    && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10;
            case "jpg", "jpeg" -> b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255;
            case "txt", "csv" -> true;
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("El contenido del archivo no coincide con su tipo");
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public record StoredFile(String name, String relativePath, String mimeType, long size, String sha256, Path physicalPath) {}
}
