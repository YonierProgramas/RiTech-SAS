package co.edu.ucc.ritech.repository;

import co.edu.ucc.ritech.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    boolean existsByIdentificacionFiscalIgnoreCase(String identificacionFiscal);
    List<Cliente> findAllByOrderByRazonSocialAsc();
}
