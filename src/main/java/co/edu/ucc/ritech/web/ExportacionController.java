package co.edu.ucc.ritech.web;

import co.edu.ucc.ritech.service.ExportacionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exportaciones")
public class ExportacionController {
    private final ExportacionService service;
    private final co.edu.ucc.ritech.service.CargaDocumentoService cargaDocumento;
    public ExportacionController(ExportacionService service, co.edu.ucc.ritech.service.CargaDocumentoService cargaDocumento) { this.service = service; this.cargaDocumento = cargaDocumento; }

    @GetMapping
    @PreAuthorize("hasAnyRole('ESPECIALISTA_EXPORTACION','COORDINADOR_LOGISTICO','AUDITOR_PROCESO')")
    public List<ApiDtos.ExportacionView> listar() { return service.listar(); }

    @PostMapping
    @PreAuthorize("hasRole('ESPECIALISTA_EXPORTACION')")
    public ApiDtos.ExportacionView crear(@Valid @RequestBody ApiDtos.ExportacionRequest req) { return service.crear(req); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ESPECIALISTA_EXPORTACION','COORDINADOR_LOGISTICO','AUDITOR_PROCESO')")
    public ApiDtos.ExportacionView detalle(@PathVariable Long id) { return service.detalle(id); }

    @GetMapping("/requisitos")
    @PreAuthorize("hasAnyRole('ESPECIALISTA_EXPORTACION','AUDITOR_PROCESO')")
    public List<ApiDtos.RequisitoView> requisitos() { return service.requisitos(); }

    @GetMapping("/{id}/checklist")
    @PreAuthorize("hasAnyRole('ESPECIALISTA_EXPORTACION','AUDITOR_PROCESO')")
    public List<Map<String,Object>> checklist(@PathVariable Long id) { return service.checklist(id); }

    @PostMapping(value = "/{id}/requisitos/{requisitoId}/documentos", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ESPECIALISTA_EXPORTACION')")
    public Map<String,Object> cargarDocumento(@PathVariable Long id, @PathVariable Long requisitoId,
                                               @RequestParam("file") MultipartFile file) {
        return cargaDocumento.cargar(id, requisitoId, file);
    }

    @PatchMapping("/{id}/documentos/{documentoId}/estado")
    @PreAuthorize("hasRole('ESPECIALISTA_EXPORTACION')")
    public Map<String,Object> cambiarDocumento(@PathVariable Long id, @PathVariable Long documentoId,
                                                @Valid @RequestBody ApiDtos.DocumentoEstadoRequest req) {
        return service.cambiarDocumento(id, documentoId, req);
    }

    @GetMapping("/{id}/tramites")
    @PreAuthorize("hasAnyRole('ESPECIALISTA_EXPORTACION','AUDITOR_PROCESO')")
    public List<ApiDtos.TramiteView> tramites(@PathVariable Long id) { return service.tramites(id); }

    @PostMapping("/{id}/tramites")
    @PreAuthorize("hasRole('ESPECIALISTA_EXPORTACION')")
    public ApiDtos.TramiteView crearTramite(@PathVariable Long id, @Valid @RequestBody ApiDtos.TramiteRequest req) {
        return service.crearTramite(id, req);
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAnyRole('COORDINADOR_LOGISTICO','AUDITOR_PROCESO')")
    public List<ApiDtos.HistorialView> historial(@PathVariable Long id) { return service.historial(id); }

    @PostMapping("/{id}/estado")
    @PreAuthorize("hasRole('COORDINADOR_LOGISTICO')")
    public ApiDtos.ExportacionView cambiarEstado(@PathVariable Long id, @Valid @RequestBody ApiDtos.CambioEstadoRequest req) {
        return service.cambiarEstado(id, req);
    }
}
