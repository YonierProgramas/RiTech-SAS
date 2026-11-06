package co.edu.ucc.ritech.repository;

import co.edu.ucc.ritech.domain.ExportacionRequisito;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExportacionRequisitoRepository extends JpaRepository<ExportacionRequisito, Long> {
    List<ExportacionRequisito> findByExportacionIdOrderByIdAsc(Long exportacionId);
}
