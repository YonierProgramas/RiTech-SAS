package co.edu.ucc.ritech.web;

import co.edu.ucc.ritech.service.CalidadService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/calidad")
public class CalidadController {
    private final CalidadService service;
    public CalidadController(CalidadService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('INSPECTOR_CALIDAD','AUDITOR_PROCESO')")
    public List<ApiDtos.InspeccionView> listar() { return service.listar(); }

    @PostMapping
    @PreAuthorize("hasRole('INSPECTOR_CALIDAD')")
    public ApiDtos.InspeccionView crear(@Valid @RequestBody ApiDtos.InspeccionRequest req) { return service.crear(req); }

    @GetMapping("/lotes/{loteId}")
    @PreAuthorize("hasAnyRole('INSPECTOR_CALIDAD','AUDITOR_PROCESO')")
    public List<ApiDtos.InspeccionView> historialPorLote(@PathVariable Long loteId) {
        return service.historialPorLote(loteId);
    }
}
