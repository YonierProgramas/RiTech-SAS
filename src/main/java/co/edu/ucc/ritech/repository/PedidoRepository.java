package co.edu.ucc.ritech.repository;

import co.edu.ucc.ritech.domain.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    boolean existsByCodigoIgnoreCase(String codigo);
    List<Pedido> findAllByOrderByFechaPedidoDesc();
}
