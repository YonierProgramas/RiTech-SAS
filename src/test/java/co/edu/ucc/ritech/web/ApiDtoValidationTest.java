package co.edu.ucc.ritech.web;

import co.edu.ucc.ritech.web.ApiDtos.*;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the actual Bean Validation constraints without starting Spring or a database. */
@DisplayName("Contratos de entrada: límites, formatos, nulos y validación anidada")
class ApiDtoValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);
    @BeforeAll static void createValidator() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll static void closeValidator() { factory.close(); }

    private static ClienteRequest client(String name, String country, String fiscal, String email) {
        return new ClienteRequest(name, country, fiscal, email, "123", "Calle 1", "Ana");
    }
    private static InspeccionRequest inspection(String humidity, String result) {
        return new InspeccionRequest(2L, new BigDecimal(humidity), null, null, null, null, result);
    }
    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("cliente sin razón social", client(" ", "ES", "F1", "a@example.test"), "razonSocial"),
                Arguments.of("país de tres letras", client("A", "COL", "F1", "a@example.test"), "pais"),
                Arguments.of("identificación ausente", client("A", "ES", null, "a@example.test"), "identificacionFiscal"),
                Arguments.of("correo inválido", client("A", "ES", "F1", "sin-arroba"), "email"),
                Arguments.of("tipo de producto desconocido", new ProductoRequest("P1", "Cafe", "OTRO", null), "tipo"),
                Arguments.of("lote sin producto", new LoteRequest("L1", null, "Huila", null, DATE, BigDecimal.ONE), "productoId"),
                Arguments.of("lote con cantidad cero", new LoteRequest("L1", 2L, "Huila", null, DATE, BigDecimal.ZERO), "cantidadInicialKg"),
                Arguments.of("pedido negativo", new PedidoRequest(1L, "ES", 2L, new BigDecimal("-0.001"), null), "cantidadKg"),
                Arguments.of("pedido sin cantidad", new PedidoRequest(1L, "ES", 2L, null, null), "cantidadKg"),
                Arguments.of("pedido múltiple vacío", new PedidoMultipleRequest(1L, "ES", List.of(), null), "detalles"),
                Arguments.of("validación anidada de cantidad", new PedidoMultipleRequest(1L, "ES", List.of(new PedidoDetalleRequest(2L, BigDecimal.ZERO)), null), "detalles[0].cantidadKg"),
                Arguments.of("más de cincuenta líneas", new PedidoMultipleRequest(1L, "ES", Collections.nCopies(51, new PedidoDetalleRequest(2L, BigDecimal.ONE)), null), "detalles"),
                Arguments.of("estado de cliente ausente", new EstadoRequest(null), "activo"),
                Arguments.of("estado de exportación inválido", new CambioEstadoRequest("DESCONOCIDO", "Nota"), "estado"),
                Arguments.of("estado documental inválido", new DocumentoEstadoRequest("DESCONOCIDO", null), "estado"),
                Arguments.of("resultado de calidad inválido", inspection("10", "DESCONOCIDO"), "resultado"),
                Arguments.of("humedad mayor a cien", inspection("100.001", "APROBADO"), "humedadPorcentaje"),
                Arguments.of("humedad negativa", inspection("-0.001", "APROBADO"), "humedadPorcentaje"));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("invalidRequests")
    void validateRequest_whenConstraintViolated_shouldIdentifyTheExactField(String reason, Object request, String field) {
        var violations = validator.validate(request);

        assertFalse(violations.isEmpty(), reason);
        assertEquals(Set.of(field), violations.stream().map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void validateRequests_whenAtValidBoundaries_shouldAcceptOptionalFieldsAndMinimumQuantities() {
        var requests = List.of(
                client("A", "ES", "F1", "a@example.test"),
                new ProductoRequest("P1", "Cafe", "CAFE", null),
                new LoteRequest("L1", 2L, "Huila", null, DATE, new BigDecimal("0.001")),
                new PedidoRequest(1L, "ES", 2L, new BigDecimal("0.001"), null),
                new PedidoMultipleRequest(1L, "ES", List.of(new PedidoDetalleRequest(2L, new BigDecimal("0.001"))), null),
                new CambioEstadoRequest("ENTREGADA", "Nota"), new DocumentoEstadoRequest("APROBADO", null),
                inspection("0", "PENDIENTE"), inspection("100", "APROBADO"), inspection("50", "RECHAZADO"));

        assertAll(requests.stream().map(r -> () -> assertTrue(validator.validate(r).isEmpty(), r.getClass().getSimpleName())));
    }
}
