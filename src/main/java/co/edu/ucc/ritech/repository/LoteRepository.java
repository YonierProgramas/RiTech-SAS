package co.edu.ucc.ritech.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import co.edu.ucc.ritech.domain.Lote;
import jakarta.persistence.LockModeType;

public interface LoteRepository extends JpaRepository<Lote, Long> {
    boolean existsByCodigoIgnoreCase(String codigo);
    Optional<Lote> findByCodigoIgnoreCase(String codigo);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lote l where l.id = :id")
    Optional<Lote> findByIdForUpdate(Long id);
    List<Lote> findAllByOrderByCreadoEnDesc();
}
