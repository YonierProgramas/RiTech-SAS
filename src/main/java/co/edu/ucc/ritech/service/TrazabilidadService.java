package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.web.ApiDtos.TrazabilidadView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TrazabilidadService {
    private final JdbcTemplate jdbc;
    public TrazabilidadService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public TrazabilidadView consultar(String codigoLote) {
        List<Map<String,Object>> eventos = jdbc.queryForList(
                "SELECT origen_evento, referencia_id, tipo_evento, fecha_evento, usuario_id, descripcion " +
                "FROM v_trazabilidad_lote WHERE codigo_lote=? ORDER BY fecha_evento, origen_evento, referencia_id", codigoLote);
        if (eventos.isEmpty()) throw new IllegalArgumentException("No se encontró trazabilidad para el lote indicado");
        return new TrazabilidadView(codigoLote, eventos);
    }
}
