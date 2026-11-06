package co.edu.ucc.ritech.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.ucc.ritech.service.PedidoService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
    private final PedidoService service;
    public PedidoController(PedidoService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR_VENTAS','ESPECIALISTA_EXPORTACION','AUDITOR_PROCESO')")
    public List<ApiDtos.PedidoView> listar() { return service.listar(); }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR_VENTAS')")
    public ApiDtos.PedidoView crear(@Valid @RequestBody ApiDtos.PedidoRequest req) { return service.crear(req); }

    @PostMapping("/multiple")
    @PreAuthorize("hasRole('ADMINISTRADOR_VENTAS')")
    public ApiDtos.PedidoView crearMultiple(@Valid @RequestBody ApiDtos.PedidoMultipleRequest req) {
        return service.crear(req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR_VENTAS','ESPECIALISTA_EXPORTACION','AUDITOR_PROCESO')")
    public List<ApiDtos.PedidoDetalleView> detalle(@PathVariable Long id) { return service.detalle(id); }
}
