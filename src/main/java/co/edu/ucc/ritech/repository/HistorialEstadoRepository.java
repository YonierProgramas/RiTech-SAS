package co.edu.ucc.ritech.repository;

import co.edu.ucc.ritech.domain.HistorialEstado;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HistorialEstadoRepository extends JpaRepository<HistorialEstado, Long> {
    List<HistorialEstado> findByExportacionIdOrderByFechaEventoAscIdAsc(Long exportacionId);
}
