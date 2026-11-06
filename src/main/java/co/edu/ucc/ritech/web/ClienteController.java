package co.edu.ucc.ritech.web;

import co.edu.ucc.ritech.domain.Cliente;
import co.edu.ucc.ritech.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {
    private final ClienteService service;
    public ClienteController(ClienteService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('GESTOR_COMERCIAL','ADMINISTRADOR_VENTAS','AUDITOR_PROCESO')")
    public List<Cliente> listar() { return service.listar(); }

    @PostMapping
    @PreAuthorize("hasRole('GESTOR_COMERCIAL')")
    public Cliente crear(@Valid @RequestBody ApiDtos.ClienteRequest req) { return service.crear(req); }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('GESTOR_COMERCIAL')")
    public Cliente actualizar(@PathVariable Long id, @Valid @RequestBody ApiDtos.ClienteRequest req) { return service.actualizar(id, req); }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('GESTOR_COMERCIAL')")
    public Cliente desactivar(@PathVariable Long id) { return service.desactivar(id); }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('GESTOR_COMERCIAL')")
    public Cliente cambiarEstado(@PathVariable Long id, @Valid @RequestBody ApiDtos.EstadoRequest req) {
        return service.cambiarEstado(id, req.activo());
    }
}
