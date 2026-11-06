package co.edu.ucc.ritech.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "exportaciones")
public class Exportacion {

    @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    private Pedido pedido;

    @Column(name = "pais_destino", nullable = false, columnDefinition = "CHAR(2)")
    private String paisDestino;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "requisitos_revisados", nullable = false)
    private Boolean requisitosRevisados;

    @Column(name = "fecha_salida_prevista")
    private LocalDate fechaSalidaPrevista;

    @Column(name = "fecha_salida_real")
    private LocalDateTime fechaSalidaReal;

    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "responsable_id", nullable = false)
    private Usuario responsable;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Version
        @Column(nullable = false)
    private Long version;

    public Exportacion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }
    public String getPaisDestino() { return paisDestino; }
    public void setPaisDestino(String paisDestino) { this.paisDestino = paisDestino; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Boolean getRequisitosRevisados() { return requisitosRevisados; }
    public void setRequisitosRevisados(Boolean requisitosRevisados) { this.requisitosRevisados = requisitosRevisados; }
    public LocalDate getFechaSalidaPrevista() { return fechaSalidaPrevista; }
    public void setFechaSalidaPrevista(LocalDate fechaSalidaPrevista) { this.fechaSalidaPrevista = fechaSalidaPrevista; }
    public LocalDateTime getFechaSalidaReal() { return fechaSalidaReal; }
    public void setFechaSalidaReal(LocalDateTime fechaSalidaReal) { this.fechaSalidaReal = fechaSalidaReal; }
    public LocalDateTime getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDateTime fechaEntrega) { this.fechaEntrega = fechaEntrega; }
    public Usuario getResponsable() { return responsable; }
    public void setResponsable(Usuario responsable) { this.responsable = responsable; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
