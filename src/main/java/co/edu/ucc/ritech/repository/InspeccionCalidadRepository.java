package co.edu.ucc.ritech.repository;

import co.edu.ucc.ritech.domain.InspeccionCalidad;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InspeccionCalidadRepository extends JpaRepository<InspeccionCalidad, Long> {
    List<InspeccionCalidad> findAllByOrderByFechaInspeccionDesc();
    List<InspeccionCalidad> findByLoteIdOrderByFechaInspeccionDesc(Long loteId);
}
