package co.edu.ucc.ritech.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "exportacion_requisitos")
public class ExportacionRequisito {

    @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "exportacion_id", nullable = false)
    private Exportacion exportacion;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "requisito_id", nullable = false)
    private RequisitoDocumental requisito;

    @Column(name = "nombre_requisito", nullable = false, length = 150)
    private String nombreRequisito;

    @Column(nullable = false)
    private Boolean obligatorio;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    public ExportacionRequisito() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Exportacion getExportacion() { return exportacion; }
    public void setExportacion(Exportacion exportacion) { this.exportacion = exportacion; }
    public RequisitoDocumental getRequisito() { return requisito; }
    public void setRequisito(RequisitoDocumental requisito) { this.requisito = requisito; }
    public String getNombreRequisito() { return nombreRequisito; }
    public void setNombreRequisito(String nombreRequisito) { this.nombreRequisito = nombreRequisito; }
    public Boolean getObligatorio() { return obligatorio; }
    public void setObligatorio(Boolean obligatorio) { this.obligatorio = obligatorio; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}