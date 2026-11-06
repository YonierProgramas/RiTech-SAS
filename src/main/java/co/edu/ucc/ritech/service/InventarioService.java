package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.domain.Lote;
import co.edu.ucc.ritech.domain.Producto;
import co.edu.ucc.ritech.repository.LoteRepository;
import co.edu.ucc.ritech.repository.ProductoRepository;
import co.edu.ucc.ritech.web.ApiDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InventarioService {
    private final ProductoRepository productos;
    private final LoteRepository lotes;
    private final CurrentUserService currentUser;

    public InventarioService(ProductoRepository productos, LoteRepository lotes, CurrentUserService currentUser) {
        this.productos = productos;
        this.lotes = lotes;
        this.currentUser = currentUser;
    }

    public List<Producto> listarProductos() { return productos.findAllByOrderByNombreAsc(); }

    public List<LoteView> listarLotes() {
        return lotes.findAllByOrderByCreadoEnDesc().stream().map(this::toView).toList();
    }

    @Transactional
    public Producto crearProducto(ProductoRequest req) {
        if (productos.existsByCodigoIgnoreCase(req.codigo())) throw new IllegalArgumentException("Código de producto duplicado");
        Producto p = new Producto();
        p.setCodigo(req.codigo().trim().toUpperCase());
        p.setNombre(req.nombre().trim());
        p.setTipo(req.tipo().trim().toUpperCase());
        p.setDescripcion(req.descripcion());
        p.setUnidadMedida("KG");
        p.setActivo(true);
        return productos.save(p);
    }

    @Transactional
    public LoteView crearLote(LoteRequest req) {
        if (lotes.existsByCodigoIgnoreCase(req.codigo())) throw new IllegalArgumentException("Código de lote duplicado");
        Producto producto = productos.findById(req.productoId()).orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
        Lote l = new Lote();
        l.setCodigo(req.codigo().trim().toUpperCase());
        l.setProducto(producto);
        l.setOrigen(req.origen().trim());
        l.setProveedorReferencia(req.proveedorReferencia());
        l.setFechaRecepcion(req.fechaRecepcion());
        l.setCantidadInicialKg(req.cantidadInicialKg());
        l.setCantidadReservadaKg(BigDecimal.ZERO);
        l.setCantidadDespachadaKg(BigDecimal.ZERO);
        l.setEstado("ACTIVO");
        l.setCreadoPor(currentUser.get());
        l.setCreadoEn(LocalDateTime.now());
        return toView(lotes.saveAndFlush(l));
    }

    @Transactional
    public Producto cambiarEstadoProducto(Long id) {
        Producto producto = productos.findById(id).orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
        producto.setActivo(!Boolean.TRUE.equals(producto.getActivo()));
        return productos.save(producto);
    }

    @Transactional
    public Producto actualizarProducto(Long id, ProductoRequest req) {
        Producto producto = productos.findById(id).orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
        if (!producto.getCodigo().equalsIgnoreCase(req.codigo()) && productos.existsByCodigoIgnoreCase(req.codigo())) {
            throw new IllegalArgumentException("Código de producto duplicado");
        }
        producto.setCodigo(req.codigo().trim().toUpperCase());
        producto.setNombre(req.nombre().trim());
        producto.setTipo(req.tipo().trim().toUpperCase());
        producto.setDescripcion(req.descripcion());
        return productos.save(producto);
    }

    @Transactional
    public LoteView actualizarLote(Long id, LoteUpdateRequest req) {
        Lote lote = lotes.findById(id).orElseThrow(() -> new IllegalArgumentException("Lote no encontrado"));
        if (!"ACTIVO".equals(lote.getEstado()) || lote.getCantidadReservadaKg().signum() > 0 || lote.getCantidadDespachadaKg().signum() > 0) {
            throw new IllegalArgumentException("Solo se puede editar un lote activo sin reservas ni despachos");
        }
        lote.setOrigen(req.origen().trim());
        lote.setProveedorReferencia(req.proveedorReferencia());
        lote.setFechaRecepcion(req.fechaRecepcion());
        return toView(lotes.save(lote));
    }

    public LoteView toView(Lote l) {
        BigDecimal disponible = l.getCantidadDisponibleKg();
        if (disponible == null && l.getCantidadInicialKg() != null) {
            disponible = l.getCantidadInicialKg().subtract(l.getCantidadReservadaKg()).subtract(l.getCantidadDespachadaKg());
        }
        return new LoteView(l.getId(), l.getCodigo(), l.getProducto().getId(), l.getProducto().getNombre(), l.getProducto().getTipo(),
                l.getOrigen(), l.getFechaRecepcion(), l.getCantidadInicialKg(), l.getCantidadReservadaKg(), l.getCantidadDespachadaKg(),
                disponible, l.getEstado());
    }
}
