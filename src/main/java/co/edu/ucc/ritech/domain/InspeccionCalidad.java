package co.edu.ucc.ritech.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inspecciones_calidad")
public class InspeccionCalidad {

    @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "inspector_id", nullable = false)
    private Usuario inspector;

    @Column(name = "fecha_inspeccion", nullable = false)
    private LocalDateTime fechaInspeccion;

    @Column(name = "humedad_porcentaje", precision = 5, scale = 2)
    private BigDecimal humedadPorcentaje;

    @Column(name = "evaluacion_taza", length = 150)
    private String evaluacionTaza;

    @Column(length = 150)
    private String aroma;

    @Lob
        @Column(columnDefinition = "TEXT")
    private String defectos;

    @Lob
        @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(nullable = false, length = 12)
    private String resultado;

    public InspeccionCalidad() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Lote getLote() { return lote; }
    public void setLote(Lote lote) { this.lote = lote; }
    public Usuario getInspector() { return inspector; }
    public void setInspector(Usuario inspector) { this.inspector = inspector; }
    public LocalDateTime getFechaInspeccion() { return fechaInspeccion; }
    public void setFechaInspeccion(LocalDateTime fechaInspeccion) { this.fechaInspeccion = fechaInspeccion; }
    public BigDecimal getHumedadPorcentaje() { return humedadPorcentaje; }
    public void setHumedadPorcentaje(BigDecimal humedadPorcentaje) { this.humedadPorcentaje = humedadPorcentaje; }
    public String getEvaluacionTaza() { return evaluacionTaza; }
    public void setEvaluacionTaza(String evaluacionTaza) { this.evaluacionTaza = evaluacionTaza; }
    public String getAroma() { return aroma; }
    public void setAroma(String aroma) { this.aroma = aroma; }
    public String getDefectos() { return defectos; }
    public void setDefectos(String defectos) { this.defectos = defectos; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }
}