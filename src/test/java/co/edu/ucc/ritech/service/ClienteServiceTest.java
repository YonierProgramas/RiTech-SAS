package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.domain.Cliente;
import co.edu.ucc.ritech.repository.ClienteRepository;
import co.edu.ucc.ritech.web.ApiDtos.ClienteRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static co.edu.ucc.ritech.support.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Clientes: normalización, duplicados y estado")
class ClienteServiceTest {
    @Mock ClienteRepository repo;
    @InjectMocks ClienteService service;
    @Captor ArgumentCaptor<Cliente> clienteCaptor;

    private ClienteRequest request(String fiscal) {
        return new ClienteRequest("  Empresa Uno  ", "es", fiscal,
                " CONTACTO@EXAMPLE.TEST ", " 123 ", " Calle 1 ", " Ana ");
    }

    @Test void listClients_shouldUseBusinessNameOrdering() {
        var rows = List.of(cliente(1L), cliente(2L));
        when(repo.findAllByOrderByRazonSocialAsc()).thenReturn(rows);

        assertSame(rows, service.listar());

        verify(repo).findAllByOrderByRazonSocialAsc(); verifyNoMoreInteractions(repo);
    }

    @Test void createClient_whenValid_shouldNormalizeAndInitializeMetadata() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Cliente result = service.crear(request(" FISCAL-1 "));

        verify(repo).save(clienteCaptor.capture()); assertSame(result, clienteCaptor.getValue());
        assertAll(() -> assertEquals("Empresa Uno", result.getRazonSocial()),
                () -> assertEquals("ES", result.getPais()),
                () -> assertEquals("FISCAL-1", result.getIdentificacionFiscal()),
                () -> assertEquals("contacto@example.test", result.getEmail()),
                () -> assertEquals("123", result.getTelefono()), () -> assertEquals("Calle 1", result.getDireccion()),
                () -> assertEquals("Ana", result.getContactoPrincipal()),
                () -> assertTrue(result.getActivo()), () -> assertNotNull(result.getCreadoEn()));
    }

    @Test void createClient_whenFiscalIdExists_shouldNotSave() {
        when(repo.existsByIdentificacionFiscalIgnoreCase("FISCAL-1")).thenReturn(true);

        var error = assertThrowsExactly(IllegalArgumentException.class,
                () -> service.crear(request("FISCAL-1")));

        assertEquals("Ya existe un cliente con esa identificación fiscal", error.getMessage());
        verify(repo, never()).save(any());
    }

    @Test void updateClient_whenFiscalIdOnlyChangesCase_shouldSkipDuplicateLookup() {
        Cliente existing = cliente(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(existing)); when(repo.save(existing)).thenReturn(existing);

        var result = service.actualizar(1L, request("fiscal-1"));

        assertAll(() -> assertSame(existing, result), () -> assertEquals(TIME, result.getCreadoEn()),
                () -> assertTrue(result.getActivo()), () -> assertEquals("Empresa Uno", result.getRazonSocial()));
        verify(repo, never()).existsByIdentificacionFiscalIgnoreCase(anyString());
    }

    @Test void updateClient_whenNewFiscalIdIsUnique_shouldApplyChanges() {
        Cliente existing = cliente(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(existing)); when(repo.save(existing)).thenReturn(existing);

        assertEquals("OTRO", service.actualizar(1L, request("OTRO")).getIdentificacionFiscal());

        verify(repo).existsByIdentificacionFiscalIgnoreCase("OTRO"); verify(repo).save(existing);
    }

    @Test void updateClient_whenFiscalIdBelongsToAnotherClient_shouldKeepOriginalData() {
        Cliente existing = cliente(1L); when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.existsByIdentificacionFiscalIgnoreCase("OTRO")).thenReturn(true);

        var error = assertThrowsExactly(IllegalArgumentException.class,
                () -> service.actualizar(1L, request("OTRO")));

        assertEquals("Ya existe un cliente con esa identificación fiscal", error.getMessage());
        assertEquals("FISCAL-1", existing.getIdentificacionFiscal()); verify(repo, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"actualizar", "desactivar", "cambiarEstado"})
    void mutateClient_whenMissing_shouldRejectBeforeSaving(String operation) {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> {
            switch (operation) {
                case "actualizar" -> service.actualizar(99L, request("OTRO"));
                case "desactivar" -> service.desactivar(99L);
                default -> service.cambiarEstado(99L, true);
            }
        });

        assertEquals("Cliente no encontrado", error.getMessage()); verify(repo, never()).save(any());
        verify(repo, never()).existsByIdentificacionFiscalIgnoreCase(anyString());
    }

    @Test void deactivateClient_shouldPersistInactiveStatus() {
        Cliente existing = cliente(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(existing)); when(repo.save(existing)).thenReturn(existing);

        assertFalse(service.desactivar(1L).getActivo());

        verify(repo).save(existing);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void changeClientStatus_shouldPersistRequestedValue(boolean active) {
        Cliente existing = cliente(1L); existing.setActivo(!active);
        when(repo.findById(1L)).thenReturn(Optional.of(existing)); when(repo.save(existing)).thenReturn(existing);

        assertEquals(active, service.cambiarEstado(1L, active).getActivo());

        verify(repo).save(existing);
    }
}
