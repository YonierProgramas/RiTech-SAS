package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.domain.*;
import co.edu.ucc.ritech.repository.*;
import co.edu.ucc.ritech.web.ApiDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ExportacionService {
    private final ExportacionRepository exportaciones;
    private final PedidoRepository pedidos;
    private final RequisitoDocumentalRepository requisitos;
    private final ExportacionRequisitoRepository exportacionRequisitos;
    private final HistorialEstadoRepository historial;
    private final CurrentUserService currentUser;
    private final JdbcTemplate jdbc;

    public ExportacionService(ExportacionRepository exportaciones, PedidoRepository pedidos,
                              RequisitoDocumentalRepository requisitos, ExportacionRequisitoRepository exportacionRequisitos,
                              HistorialEstadoRepository historial, CurrentUserService currentUser, JdbcTemplate jdbc) {
        this.exportaciones = exportaciones; this.pedidos = pedidos; this.requisitos = requisitos;
        this.exportacionRequisitos = exportacionRequisitos; this.historial = historial;
        this.currentUser = currentUser; this.jdbc = jdbc;
    }

    public List<ExportacionView> listar() {
        return exportaciones.findAllByOrderByCreadoEnDesc().stream().map(this::toView).toList();
    }

    public ExportacionView detalle(Long id) {
        return toView(exportaciones.findById(id).orElseThrow(() -> new IllegalArgumentException("Exportación no encontrada")));
    }

    public List<RequisitoView> requisitos() {
        return requisitos.findByActivoTrueOrderByIdAsc().stream()
                .map(r -> new RequisitoView(r.getId(), r.getCodigo(), r.getNombre(), r.getObligatorio(), r.getActivo())).toList();
    }

    @Transactional
    public ExportacionView crear(ExportacionRequest req) {
        Pedido p = pedidos.findById(req.pedidoId()).orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
        if (exportaciones.existsByPedidoId(p.getId())) throw new IllegalArgumentException("El pedido ya tiene una exportación asociada");
        if (!"CONFIRMADO".equals(p.getEstado())) throw new IllegalArgumentException("El pedido debe estar CONFIRMADO");

        Exportacion e = new Exportacion();
        e.setCodigo("EXP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        e.setPedido(p);
        e.setPaisDestino(p.getPaisDestino());
        e.setEstado("PREPARACION");
        e.setRequisitosRevisados(false);
        e.setFechaSalidaPrevista(req.fechaSalidaPrevista());
        e.setResponsable(currentUser.get());
        e.setCreadoEn(LocalDateTime.now());
        e = exportaciones.save(e);

        for (RequisitoDocumental r : requisitos.findByActivoTrueOrderByIdAsc()) {
            boolean paisAplica = r.getPaisDestino() == null || r.getPaisDestino().equalsIgnoreCase(e.getPaisDestino());
            if (!paisAplica) continue;
            ExportacionRequisito er = new ExportacionRequisito();
            er.setExportacion(e); er.setRequisito(r); er.setNombreRequisito(r.getNombre());
            er.setObligatorio(r.getObligatorio()); er.setCreadoEn(LocalDateTime.now());
            exportacionRequisitos.save(er);
        }

        HistorialEstado h = new HistorialEstado();
        h.setExportacion(e); h.setEstadoAnterior(null); h.setEstadoNuevo("PREPARACION"); h.setFechaEvento(LocalDateTime.now());
        h.setUsuario(currentUser.get()); h.setObservacion("Creación del expediente de exportación");
        historial.save(h);
        return toView(e);
    }

    public List<Map<String,Object>> checklist(Long exportacionId) {
        if (!exportaciones.existsById(exportacionId)) throw new IllegalArgumentException("Exportación no encontrada");
        return jdbc.queryForList("SELECT c.*, d.nombre_original FROM v_checklist_exportacion c " +
                "LEFT JOIN documentos_exportacion d ON d.id=c.documento_id " +
                "WHERE c.exportacion_id=? ORDER BY c.exportacion_requisito_id", exportacionId);
    }

    @Transactional
    public Map<String, Object> cambiarDocumento(Long exportacionId, Long documentoId, DocumentoEstadoRequest req) {
        if (!exportaciones.existsById(exportacionId)) throw new IllegalArgumentException("Exportación no encontrada");
        Integer pertenece = jdbc.query("SELECT COUNT(*) FROM documentos_exportacion d " +
                "JOIN exportacion_requisitos er ON er.id=d.exportacion_requisito_id " +
                "WHERE d.id=? AND er.exportacion_id=?", rs -> rs.next() ? rs.getInt(1) : 0, documentoId, exportacionId);
        if (pertenece == 0) throw new IllegalArgumentException("Documento no encontrado en la exportación");
        String estado = req.estado().trim().toUpperCase();
        String observacion = req.observacion() == null ? null : req.observacion().trim();
        int actualizadas;
        if (Set.of("APROBADO", "RECHAZADO").contains(estado)) {
            actualizadas = jdbc.update("UPDATE documentos_exportacion SET estado=?, revisado_por=?, revisado_en=UTC_TIMESTAMP(6), observacion_revision=? WHERE id=?",
                    estado, currentUser.get().getId(), observacion, documentoId);
        } else {
            actualizadas = jdbc.update("UPDATE documentos_exportacion SET estado=?, observacion_revision=? WHERE id=?",
                    estado, observacion, documentoId);
        }
        if (actualizadas != 1) throw new IllegalArgumentException("No se pudo actualizar el documento");
        return jdbc.queryForMap("SELECT * FROM v_checklist_exportacion WHERE documento_id=?", documentoId);
    }

    @Transactional
    public TramiteView crearTramite(Long exportacionId, TramiteRequest req) {
        if (!exportaciones.existsById(exportacionId)) throw new IllegalArgumentException("Exportación no encontrada");
        int inserted = jdbc.update("INSERT INTO tramites_externos (exportacion_id, entidad, tipo_tramite, referencia, estado_reportado, observaciones, registrado_por, registrado_en) VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",
                exportacionId, req.entidad().trim(), req.tipoTramite().trim(), req.referencia().trim(), req.estadoReportado().trim(), req.observaciones(), currentUser.get().getId());
        if (inserted != 1) throw new IllegalArgumentException("No se pudo registrar el trámite externo");
        return tramites(exportacionId).getLast();
    }

    public List<TramiteView> tramites(Long exportacionId) {
        if (!exportaciones.existsById(exportacionId)) throw new IllegalArgumentException("Exportación no encontrada");
        return jdbc.query("SELECT t.id, t.entidad, t.tipo_tramite, t.referencia, t.estado_reportado, t.observaciones, t.registrado_en, u.nombre " +
                        "FROM tramites_externos t JOIN usuarios u ON u.id=t.registrado_por WHERE t.exportacion_id=? ORDER BY t.registrado_en, t.id",
                (rs, rowNum) -> new TramiteView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getTimestamp(7).toLocalDateTime(), rs.getString(8)), exportacionId);
    }

    public List<HistorialView> historial(Long exportacionId) {
        return historial.findByExportacionIdOrderByFechaEventoAscIdAsc(exportacionId).stream()
                .map(h -> new HistorialView(h.getId(), h.getEstadoAnterior(), h.getEstadoNuevo(), h.getFechaEvento(), h.getUsuario().getNombre(), h.getObservacion()))
                .toList();
    }

    @Transactional
    public ExportacionView cambiarEstado(Long id, CambioEstadoRequest req) {
        Exportacion e = exportaciones.findById(id).orElseThrow(() -> new IllegalArgumentException("Exportación no encontrada"));
        String nuevo = req.estado().trim().toUpperCase();
        if (nuevo.equals(e.getEstado())) throw new IllegalArgumentException("El nuevo estado debe ser diferente al actual");
        if (Set.of("LISTA_DESPACHO","ADUANA","EN_TRANSITO","ENTREGADA").contains(nuevo)) {
            Boolean completo = jdbc.query("SELECT checklist_completo FROM v_control_documental WHERE exportacion_id=?",
                    rs -> rs.next() && rs.getBoolean(1), id);
            if (!Boolean.TRUE.equals(completo)) throw new IllegalArgumentException("Existen requisitos documentales obligatorios pendientes");
        }
        String anterior = e.getEstado();
        e.setEstado(nuevo);
        if ("EN_TRANSITO".equals(nuevo) && e.getFechaSalidaReal() == null) e.setFechaSalidaReal(LocalDateTime.now());
        if ("ENTREGADA".equals(nuevo)) {
            if (e.getFechaSalidaReal() == null) e.setFechaSalidaReal(LocalDateTime.now());
            e.setFechaEntrega(LocalDateTime.now());
        }
        e = exportaciones.save(e);
        HistorialEstado h = new HistorialEstado();
        h.setExportacion(e); h.setEstadoAnterior(anterior); h.setEstadoNuevo(nuevo); h.setFechaEvento(LocalDateTime.now());
        h.setUsuario(currentUser.get()); h.setObservacion(req.observacion().trim());
        historial.save(h);
        return toView(e);
    }

    private ExportacionView toView(Exportacion e) {
        return new ExportacionView(e.getId(), e.getCodigo(), e.getPedido().getCodigo(), e.getPedido().getCliente().getRazonSocial(),
                e.getPaisDestino(), e.getEstado(), e.getRequisitosRevisados(), e.getFechaSalidaPrevista(), e.getFechaSalidaReal(),
                e.getFechaEntrega(), e.getResponsable().getNombre());
    }
}
