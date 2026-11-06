package co.edu.ucc.ritech.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "requisitos_documentales")
public class RequisitoDocumental {

    @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String codigo;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Lob
        @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "pais_destino", columnDefinition = "CHAR(2)")
    private String paisDestino;

    @Column(name = "tipo_producto", length = 10)
    private String tipoProducto;

    @Column(nullable = false)
    private Boolean obligatorio;

    @Column(nullable = false)
    private Boolean activo;

    @Column(name = "fuente_referencia", length = 500)
    private String fuenteReferencia;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    public RequisitoDocumental() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getPaisDestino() { return paisDestino; }
    public void setPaisDestino(String paisDestino) { this.paisDestino = paisDestino; }
    public String getTipoProducto() { return tipoProducto; }
    public void setTipoProducto(String tipoProducto) { this.tipoProducto = tipoProducto; }
    public Boolean getObligatorio() { return obligatorio; }
    public void setObligatorio(Boolean obligatorio) { this.obligatorio = obligatorio; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public String getFuenteReferencia() { return fuenteReferencia; }
    public void setFuenteReferencia(String fuenteReferencia) { this.fuenteReferencia = fuenteReferencia; }
    public LocalDate getVigenteDesde() { return vigenteDesde; }
    public void setVigenteDesde(LocalDate vigenteDesde) { this.vigenteDesde = vigenteDesde; }
    public LocalDate getVigenteHasta() { return vigenteHasta; }
    public void setVigenteHasta(LocalDate vigenteHasta) { this.vigenteHasta = vigenteHasta; }
}
