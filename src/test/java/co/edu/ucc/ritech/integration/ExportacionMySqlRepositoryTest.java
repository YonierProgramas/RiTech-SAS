package co.edu.ucc.ritech.integration;

import co.edu.ucc.ritech.repository.ExportacionRequisitoRepository;
import co.edu.ucc.ritech.repository.ExportacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(readOnly = true)
@EnabledIfEnvironmentVariable(named = "RITECH_RUN_MYSQL_INTEGRATION", matches = "true")
class ExportacionMySqlRepositoryTest {
    @Autowired ExportacionRepository exportaciones;
    @Autowired ExportacionRequisitoRepository requisitos;

    @Test void consultaExportacionesYRelacionesConElEsquemaMySqlExistente() {
        var expedientes = exportaciones.findAllByOrderByCreadoEnDesc();
        assertFalse(expedientes.isEmpty(), "MySQL debe contener al menos una exportación para validar el esquema Sprint 2");
        expedientes.forEach(e -> {
            assertNotNull(e.getPedido().getCliente().getRazonSocial());
            assertNotNull(e.getResponsable().getNombre());
        });

        var checklist = requisitos.findAll();
        assertFalse(checklist.isEmpty(), "MySQL debe contener requisitos asociados a exportaciones");
        checklist.forEach(r -> {
            assertNotNull(r.getExportacion().getCodigo());
            assertNotNull(r.getRequisito().getCodigo());
        });
    }
}
