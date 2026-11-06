package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.domain.*;
import co.edu.ucc.ritech.repository.*;
import co.edu.ucc.ritech.web.ApiDtos.PedidoRequest;
import co.edu.ucc.ritech.web.ApiDtos.PedidoMultipleRequest;
import co.edu.ucc.ritech.web.ApiDtos.PedidoDetalleView;
import co.edu.ucc.ritech.web.ApiDtos.PedidoView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PedidoService {
    private final PedidoRepository pedidos;
    private final DetallePedidoRepository detalles;
    private final ClienteRepository clientes;
    private final LoteRepository lotes;
    private final CurrentUserService currentUser;

    public PedidoService(PedidoRepository pedidos, DetallePedidoRepository detalles, ClienteRepository clientes,
                         LoteRepository lotes, CurrentUserService currentUser) {
        this.pedidos = pedidos; this.detalles = detalles; this.clientes = clientes; this.lotes = lotes; this.currentUser = currentUser;
    }

    public List<PedidoView> listar() {
        return pedidos.findAllByOrderByFechaPedidoDesc().stream().map(p -> {
            List<DetallePedido> ds = detalles.findByPedidoId(p.getId());
            DetallePedido d = ds.isEmpty() ? null : ds.get(0);
            return new PedidoView(p.getId(), p.getCodigo(), p.getCliente().getId(), p.getCliente().getRazonSocial(), p.getPaisDestino(),
                    p.getEstado(), p.getFechaPedido(), d == null ? null : d.getLote().getId(), d == null ? null : d.getLote().getCodigo(),
                    d == null ? null : d.getCantidadKg());
        }).toList();
    }

    public List<PedidoDetalleView> detalle(Long pedidoId) {
        if (!pedidos.existsById(pedidoId)) throw new IllegalArgumentException("Pedido no encontrado");
        return detalles.findByPedidoId(pedidoId).stream()
                .map(d -> new PedidoDetalleView(d.getId(), d.getLote().getId(), d.getLote().getCodigo(),
                        d.getLote().getProducto().getNombre(), d.getCantidadKg())).toList();
    }

    @Transactional
    public PedidoView crear(PedidoRequest req) {
        Cliente cliente = clientes.findById(req.clienteId()).orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        if (!Boolean.TRUE.equals(cliente.getActivo())) throw new IllegalArgumentException("El cliente está inactivo");
        Lote lote = loteBloqueado(req.loteId());
        return guardarPedido(cliente, req.paisDestino(), req.observaciones(), List.of(new Linea(lote, req.cantidadKg())));
    }

    @Transactional
    public PedidoView crear(PedidoMultipleRequest req) {
        Cliente cliente = clientes.findById(req.clienteId()).orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        if (!Boolean.TRUE.equals(cliente.getActivo())) throw new IllegalArgumentException("El cliente está inactivo");
        List<Linea> lineas = req.detalles().stream()
                .map(d -> new Linea(loteBloqueado(d.loteId()), d.cantidadKg()))
                .toList();
        if (lineas.stream().map(Linea::lote).map(Lote::getId).distinct().count() != lineas.size()) {
            throw new IllegalArgumentException("Un pedido no puede repetir el mismo lote");
        }
        return guardarPedido(cliente, req.paisDestino(), req.observaciones(), lineas);
    }

    private Lote loteBloqueado(Long id) {
        return lotes.findByIdForUpdate(id).or(() -> lotes.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado"));
    }

    private PedidoView guardarPedido(Cliente cliente, String paisDestino, String observaciones, List<Linea> lineas) {
        for (Linea linea : lineas) validarDisponibilidad(linea.lote(), linea.cantidad());
        Lote primerLote = lineas.get(0).lote();

        Pedido p = new Pedido();
        p.setCodigo("PED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        p.setCliente(cliente);
        p.setPaisDestino(paisDestino.trim().toUpperCase());
        p.setEstado("CONFIRMADO");
        p.setFechaPedido(LocalDateTime.now());
        p.setObservaciones(observaciones);
        p.setCreadoPor(currentUser.get());
        p = pedidos.save(p);

        for (Linea linea : lineas) {
            DetallePedido d = new DetallePedido();
            d.setPedido(p); d.setLote(linea.lote()); d.setCantidadKg(linea.cantidad());
            detalles.save(d);
            linea.lote().setCantidadReservadaKg(linea.lote().getCantidadReservadaKg().add(linea.cantidad()));
            lotes.save(linea.lote());
        }

        return new PedidoView(p.getId(), p.getCodigo(), cliente.getId(), cliente.getRazonSocial(), p.getPaisDestino(), p.getEstado(),
                p.getFechaPedido(), primerLote.getId(), primerLote.getCodigo(), lineas.get(0).cantidad());
    }

    private void validarDisponibilidad(Lote lote, BigDecimal cantidad) {
        BigDecimal disponible = lote.getCantidadInicialKg().subtract(lote.getCantidadReservadaKg()).subtract(lote.getCantidadDespachadaKg());
        if (cantidad.compareTo(disponible) > 0) throw new IllegalArgumentException("La cantidad solicitada supera la disponibilidad del lote");
        if (!"ACTIVO".equals(lote.getEstado())) throw new IllegalArgumentException("El lote no está activo");
    }

    private record Linea(Lote lote, BigDecimal cantidad) {}
}
