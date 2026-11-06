package co.edu.ucc.ritech.web;

import co.edu.ucc.ritech.domain.Producto;
import co.edu.ucc.ritech.service.InventarioService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class InventarioController {
    private final InventarioService service;
    public InventarioController(InventarioService service) { this.service = service; }

    @GetMapping("/productos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR_VENTAS','AUDITOR_PROCESO')")
    public List<Producto> productos() { return service.listarProductos(); }

    @PostMapping("/productos")
    @PreAuthorize("hasRole('ADMINISTRADOR_VENTAS')")
    public Producto crearProducto(@Valid @RequestBody ApiDtos.ProductoRequest req) { return service.crearProducto(req); }

    @PutMapping("/productos/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR_VENTAS')")
    public Producto actualizarProducto(@PathVariable Long id, @Valid @RequestBody ApiDtos.ProductoRequest req) {
        return service.actualizarProducto(id, req);
    }

    @PatchMapping("/productos/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR_VENTAS')")
    public Producto cambiarEstadoProducto(@PathVariable Long id) { return service.cambiarEstadoProducto(id); }

    @GetMapping("/lotes")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR_VENTAS','INSPECTOR_CALIDAD','AUDITOR_PROCESO')")
    public List<ApiDtos.LoteView> lotes() { return service.listarLotes(); }

    @PostMapping("/lotes")
    @PreAuthorize("hasRole('ADMINISTRADOR_VENTAS')")
    public ApiDtos.LoteView crearLote(@Valid @RequestBody ApiDtos.LoteRequest req) { return service.crearLote(req); }

    @PutMapping("/lotes/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR_VENTAS')")
    public ApiDtos.LoteView actualizarLote(@PathVariable Long id, @Valid @RequestBody ApiDtos.LoteUpdateRequest req) {
        return service.actualizarLote(id, req);
    }
}
