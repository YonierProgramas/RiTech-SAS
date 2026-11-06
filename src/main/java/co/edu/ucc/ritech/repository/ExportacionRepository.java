package co.edu.ucc.ritech.repository;

import co.edu.ucc.ritech.domain.Exportacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExportacionRepository extends JpaRepository<Exportacion, Long> {
    boolean existsByPedidoId(Long pedidoId);
    List<Exportacion> findAllByOrderByCreadoEnDesc();
}
