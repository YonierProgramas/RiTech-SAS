package co.edu.ucc.ritech.repository;

import co.edu.ucc.ritech.domain.RequisitoDocumental;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RequisitoDocumentalRepository extends JpaRepository<RequisitoDocumental, Long> {
    List<RequisitoDocumental> findByActivoTrueOrderByIdAsc();
}
