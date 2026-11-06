package co.edu.ucc.ritech.support;

import co.edu.ucc.ritech.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Deterministic entity graphs; no persistence, Spring context or DEMO records. */
public final class TestFixtures {
    public static final LocalDate DATE = LocalDate.of(2026, 10, 1);
    public static final LocalDateTime TIME = DATE.atTime(12, 0);
    private TestFixtures() {}

    public static Usuario usuario() {
        Usuario u = new Usuario(); u.setId(7L); u.setNombre("Ana Inspectora");
        u.setEmail("ana@example.test"); u.setActivo(true); return u;
    }
    public static Cliente cliente(long id) {
        Cliente c = new Cliente(); c.setId(id); c.setRazonSocial("Cliente " + id);
        c.setIdentificacionFiscal("FISCAL-" + id); c.setPais("ES");
        c.setActivo(true); c.setCreadoEn(TIME); return c;
    }
    public static Producto producto(long id) {
        Producto p = new Producto(); p.setId(id); p.setCodigo("PROD-" + id);
        p.setNombre("Cafe " + id); p.setTipo("CAFE"); p.setUnidadMedida("KG");
        p.setActivo(true); return p;
    }
    public static Lote lote(long id) {
        Lote l = new Lote(); l.setId(id); l.setCodigo("LOTE-" + id);
        l.setProducto(producto(2L)); l.setOrigen("Huila"); l.setFechaRecepcion(DATE);
        l.setCantidadInicialKg(new BigDecimal("100.000"));
        l.setCantidadReservadaKg(new BigDecimal("10.000"));
        l.setCantidadDespachadaKg(new BigDecimal("5.000"));
        l.setEstado("ACTIVO"); l.setCreadoPor(usuario()); l.setCreadoEn(TIME); return l;
    }
    public static Pedido pedido(long id) {
        Pedido p = new Pedido(); p.setId(id); p.setCodigo("PED-" + id);
        p.setCliente(cliente(1L)); p.setEstado("CONFIRMADO"); p.setPaisDestino("ES");
        p.setFechaPedido(TIME); p.setCreadoPor(usuario()); return p;
    }
    public static Exportacion exportacion(long id) {
        Exportacion e = new Exportacion(); e.setId(id); e.setCodigo("EXP-" + id);
        e.setPedido(pedido(3L)); e.setPaisDestino("ES"); e.setEstado("PREPARACION");
        e.setResponsable(usuario()); e.setRequisitosRevisados(false); e.setCreadoEn(TIME); return e;
    }
    public static RequisitoDocumental requisito(long id, String pais) {
        RequisitoDocumental r = new RequisitoDocumental(); r.setId(id);
        r.setCodigo("REQ-" + id); r.setNombre("Documento " + id); r.setPaisDestino(pais);
        r.setActivo(true); r.setObligatorio(true); return r;
    }
}
