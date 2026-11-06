package co.edu.ucc.ritech.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "lotes")
public class Lote {

    @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false, length = 255)
    private String origen;

    @Column(name = "proveedor_referencia", length = 150)
    private String proveedorReferencia;

    @Column(name = "fecha_recepcion", nullable = false)
    private LocalDate fechaRecepcion;

    @Column(name = "cantidad_inicial_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidadInicialKg;

    @Column(name = "cantidad_reservada_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidadReservadaKg;

    @Column(name = "cantidad_despachada_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidadDespachadaKg;

    @Column(name = "cantidad_disponible_kg", insertable = false, updatable = false, precision = 14, scale = 3)
    private BigDecimal cantidadDisponibleKg;

    @Column(nullable = false, length = 12)
    private String estado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "creado_por", nullable = false)
    private Usuario creadoPor;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Version
        @Column(nullable = false)
    private Long version;

    public Lote() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public String getProveedorReferencia() { return proveedorReferencia; }
    public void setProveedorReferencia(String proveedorReferencia) { this.proveedorReferencia = proveedorReferencia; }
    public LocalDate getFechaRecepcion() { return fechaRecepcion; }
    public void setFechaRecepcion(LocalDate fechaRecepcion) { this.fechaRecepcion = fechaRecepcion; }
    public BigDecimal getCantidadInicialKg() { return cantidadInicialKg; }
    public void setCantidadInicialKg(BigDecimal cantidadInicialKg) { this.cantidadInicialKg = cantidadInicialKg; }
    public BigDecimal getCantidadReservadaKg() { return cantidadReservadaKg; }
    public void setCantidadReservadaKg(BigDecimal cantidadReservadaKg) { this.cantidadReservadaKg = cantidadReservadaKg; }
    public BigDecimal getCantidadDespachadaKg() { return cantidadDespachadaKg; }
    public void setCantidadDespachadaKg(BigDecimal cantidadDespachadaKg) { this.cantidadDespachadaKg = cantidadDespachadaKg; }
    public BigDecimal getCantidadDisponibleKg() { return cantidadDisponibleKg; }
    public void setCantidadDisponibleKg(BigDecimal cantidadDisponibleKg) { this.cantidadDisponibleKg = cantidadDisponibleKg; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Usuario getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Usuario creadoPor) { this.creadoPor = creadoPor; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}