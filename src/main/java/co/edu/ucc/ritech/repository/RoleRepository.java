package co.edu.ucc.ritech.repository;

import co.edu.ucc.ritech.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCodigo(String codigo);
}
