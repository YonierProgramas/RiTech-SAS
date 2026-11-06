package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.domain.InspeccionCalidad;
import co.edu.ucc.ritech.domain.Lote;
import co.edu.ucc.ritech.repository.InspeccionCalidadRepository;
import co.edu.ucc.ritech.repository.LoteRepository;
import co.edu.ucc.ritech.web.ApiDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CalidadService {
    private final InspeccionCalidadRepository inspecciones;
    private final LoteRepository lotes;
    private final CurrentUserService currentUser;

    public CalidadService(InspeccionCalidadRepository inspecciones, LoteRepository lotes, CurrentUserService currentUser) {
        this.inspecciones = inspecciones; this.lotes = lotes; this.currentUser = currentUser;
    }

    public List<InspeccionView> listar() {
        return inspecciones.findAllByOrderByFechaInspeccionDesc().stream().map(this::toView).toList();
    }

    public List<InspeccionView> historialPorLote(Long loteId) {
        if (!lotes.existsById(loteId)) throw new IllegalArgumentException("Lote no encontrado");
        return inspecciones.findByLoteIdOrderByFechaInspeccionDesc(loteId).stream().map(this::toView).toList();
    }

    @Transactional
    public InspeccionView crear(InspeccionRequest req) {
        Lote lote = lotes.findById(req.loteId()).orElseThrow(() -> new IllegalArgumentException("Lote no encontrado"));
        InspeccionCalidad i = new InspeccionCalidad();
        i.setLote(lote); i.setInspector(currentUser.get()); i.setFechaInspeccion(LocalDateTime.now());
        i.setHumedadPorcentaje(req.humedadPorcentaje()); i.setEvaluacionTaza(req.evaluacionTaza()); i.setAroma(req.aroma());
        i.setDefectos(req.defectos()); i.setObservaciones(req.observaciones()); i.setResultado(req.resultado().toUpperCase());
        return toView(inspecciones.save(i));
    }

    private InspeccionView toView(InspeccionCalidad i) {
        return new InspeccionView(i.getId(), i.getLote().getId(), i.getLote().getCodigo(), i.getInspector().getNombre(), i.getFechaInspeccion(),
                i.getHumedadPorcentaje(), i.getEvaluacionTaza(), i.getAroma(), i.getDefectos(), i.getObservaciones(), i.getResultado());
    }
}
