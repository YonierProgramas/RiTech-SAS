package co.edu.ucc.ritech.web;

import co.edu.ucc.ritech.service.TrazabilidadService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trazabilidad")
public class TrazabilidadController {
    private final TrazabilidadService service;
    public TrazabilidadController(TrazabilidadService service) { this.service = service; }

    @GetMapping("/{codigoLote}")
    @PreAuthorize("hasRole('AUDITOR_PROCESO')")
    public ApiDtos.TrazabilidadView consultar(@PathVariable String codigoLote) { return service.consultar(codigoLote); }
}
