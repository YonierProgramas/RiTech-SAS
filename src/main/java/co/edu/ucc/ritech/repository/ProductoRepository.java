package co.edu.ucc.ritech.repository;

import co.edu.ucc.ritech.domain.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    boolean existsByCodigoIgnoreCase(String codigo);
    List<Producto> findAllByOrderByNombreAsc();
}
