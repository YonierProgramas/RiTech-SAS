package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.domain.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class CargaDocumentoService {
    private final JdbcTemplate jdbc;
    private final CurrentUserService currentUser;
    private final DocumentoStorageService storage;

    public CargaDocumentoService(JdbcTemplate jdbc, CurrentUserService currentUser, DocumentoStorageService storage) {
        this.jdbc = jdbc; this.currentUser = currentUser; this.storage = storage;
    }

    @Transactional
    public Map<String, Object> cargar(Long exportacionId, Long requisitoId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Debe seleccionar un archivo");
        long pertenece = jdbc.queryForObject("SELECT COUNT(*) FROM exportacion_requisitos WHERE id=? AND exportacion_id=?", Long.class, requisitoId, exportacionId);
        if (pertenece == 0) throw new IllegalArgumentException("Requisito no encontrado en la exportación");
        jdbc.queryForObject("SELECT id FROM exportacion_requisitos WHERE id=? FOR UPDATE", Long.class, requisitoId);
        Usuario usuario = currentUser.get();
        DocumentoStorageService.StoredFile saved = null;
        try {
            byte[] bytes = file.getBytes();
            saved = storage.store(exportacionId, requisitoId, file.getOriginalFilename(), file.getContentType(), bytes);
            Integer version = jdbc.queryForObject("SELECT COALESCE(MAX(numero_version),0)+1 FROM documentos_exportacion WHERE exportacion_requisito_id=?", Integer.class, requisitoId);
            jdbc.update("UPDATE documentos_exportacion SET vigente=FALSE WHERE exportacion_requisito_id=? AND vigente=TRUE", requisitoId);
            jdbc.update("INSERT INTO documentos_exportacion (exportacion_requisito_id,numero_version,vigente,nombre_original,ruta_archivo,mime_type,tamano_bytes,sha256,estado,cargado_por,cargado_en) VALUES (?,?,TRUE,?,?,?,?,?,'CARGADO',?,UTC_TIMESTAMP(6))",
                    requisitoId, version, saved.name(), saved.relativePath(), saved.mimeType(), saved.size(), saved.sha256(), usuario.getId());
            return jdbc.queryForMap("SELECT id, exportacion_requisito_id, numero_version, vigente, nombre_original, mime_type, tamano_bytes, sha256, estado, cargado_en FROM documentos_exportacion WHERE exportacion_requisito_id=? AND numero_version=?", requisitoId, version);
        } catch (Exception e) {
            if (saved != null) storage.delete(saved.physicalPath());
            if (e instanceof IllegalArgumentException iae) throw iae;
            throw new IllegalArgumentException("No se pudo cargar el documento", e);
        }
    }
}
