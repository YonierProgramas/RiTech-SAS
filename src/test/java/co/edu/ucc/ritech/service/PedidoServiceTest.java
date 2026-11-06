package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.domain.*;
import co.edu.ucc.ritech.repository.*;
import co.edu.ucc.ritech.web.ApiDtos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import java.math.BigDecimal;
import java.util.*;
import static co.edu.ucc.ritech.support.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pedidos: reserva de stock y validación completa antes de guardar")
class PedidoServiceTest {
    @Mock PedidoRepository pedidos;
    @Mock DetallePedidoRepository detalles;
    @Mock ClienteRepository clientes;
    @Mock LoteRepository lotes;
    @Mock CurrentUserService currentUser;
    @InjectMocks PedidoService service;
    @Captor ArgumentCaptor<Pedido> pedidoCaptor;
    @Captor ArgumentCaptor<DetallePedido> detalleCaptor;

    private PedidoRequest request(String quantity) {
        return new PedidoRequest(1L, " es ", 2L, new BigDecimal(quantity), "Observacion");
    }
    private PedidoMultipleRequest multiple(long first, long second, String secondQuantity) {
        return new PedidoMultipleRequest(1L, "ES", List.of(
                new PedidoDetalleRequest(first, new BigDecimal("20.000")),
                new PedidoDetalleRequest(second, new BigDecimal(secondQuantity))), "Multiple");
    }
    private void givenClient() { when(clientes.findById(1L)).thenReturn(Optional.of(cliente(1L))); }
    private void givenPersistence() {
        when(currentUser.get()).thenReturn(usuario());
        when(pedidos.save(any())).thenAnswer(inv -> { Pedido p = inv.getArgument(0); p.setId(10L); return p; });
    }
    private DetallePedido detail(long id, Pedido p, Lote l, String quantity) {
        DetallePedido d = new DetallePedido(); d.setId(id); d.setPedido(p); d.setLote(l);
        d.setCantidadKg(new BigDecimal(quantity)); return d;
    }

    @Test void listOrders_shouldMapFirstDetailAndAllowOrdersWithoutDetails() {
        Pedido first = pedido(1L), empty = pedido(2L);
        when(pedidos.findAllByOrderByFechaPedidoDesc()).thenReturn(List.of(first, empty));
        when(detalles.findByPedidoId(1L)).thenReturn(List.of(detail(8L, first, lote(2L), "12.000")));

        var result = service.listar();

        assertAll(() -> assertEquals(2L, result.getFirst().loteId()),
                () -> assertEquals(new BigDecimal("12.000"), result.getFirst().cantidadKg()),
                () -> assertEquals("Cliente 1", result.getFirst().cliente()),
                () -> assertNull(result.getLast().loteId()), () -> assertNull(result.getLast().cantidadKg()));
        verify(detalles).findByPedidoId(2L);
    }

    @Test void orderDetails_whenExisting_shouldExposeLotProductAndQuantity() {
        when(pedidos.existsById(1L)).thenReturn(true);
        when(detalles.findByPedidoId(1L)).thenReturn(List.of(detail(8L, pedido(1L), lote(2L), "12.000")));

        var d = service.detalle(1L).getFirst();

        assertAll(() -> assertEquals(8L, d.id()), () -> assertEquals(2L, d.loteId()),
                () -> assertEquals("Cafe 2", d.producto()), () -> assertEquals(new BigDecimal("12.000"), d.cantidadKg()));
    }

    @Test void orderDetails_whenMissing_shouldNotReadDetails() {
        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.detalle(99L));

        assertEquals("Pedido no encontrado", error.getMessage()); verifyNoInteractions(detalles);
    }

    @Test void createOrder_whenQuantityEqualsAvailability_shouldReserveAllRemainingStock() {
        givenClient(); givenPersistence(); Lote l = lote(2L);
        when(lotes.findByIdForUpdate(2L)).thenReturn(Optional.of(l));

        var result = service.crear(request("85.000"));

        verify(pedidos).save(pedidoCaptor.capture()); verify(detalles).save(detalleCaptor.capture());
        Pedido saved = pedidoCaptor.getValue(); DetallePedido d = detalleCaptor.getValue();
        assertAll(() -> assertEquals("CONFIRMADO", result.estado()), () -> assertEquals("ES", result.paisDestino()),
                () -> assertTrue(result.codigo().matches("PED-[A-F0-9]{8}")),
                () -> assertEquals(new BigDecimal("95.000"), l.getCantidadReservadaKg()),
                () -> assertSame(saved, d.getPedido()), () -> assertSame(l, d.getLote()),
                () -> assertEquals(new BigDecimal("85.000"), d.getCantidadKg()),
                () -> assertEquals(7L, saved.getCreadoPor().getId()), () -> assertNotNull(saved.getFechaPedido()));
        verify(lotes, never()).findById(anyLong()); verify(lotes).save(l);
    }

    @Test void createOrder_whenClientMissing_shouldNotLockOrPersist() {
        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.crear(request("1")));

        assertEquals("Cliente no encontrado", error.getMessage());
        verifyNoInteractions(lotes, pedidos, detalles, currentUser);
    }

    @Test void createMultipleOrder_whenClientInactive_shouldNotLockAnyLot() {
        Cliente c = cliente(1L); c.setActivo(false); when(clientes.findById(1L)).thenReturn(Optional.of(c));

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.crear(multiple(2L, 3L, "10")));

        assertEquals("El cliente está inactivo", error.getMessage());
        verifyNoInteractions(lotes, pedidos, detalles, currentUser);
    }

    @Test void createMultipleOrder_whenClientMissing_shouldRejectBeforeLockingAnyLot() {
        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.crear(multiple(2L, 3L, "10")));

        assertEquals("Cliente no encontrado", error.getMessage());
        verifyNoInteractions(lotes, pedidos, detalles, currentUser);
    }

    @Test void createOrder_whenClientInactive_shouldNotPersist() {
        Cliente c = cliente(1L); c.setActivo(false); when(clientes.findById(1L)).thenReturn(Optional.of(c));

        assertEquals("El cliente está inactivo", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.crear(request("1"))).getMessage());
        verifyNoInteractions(lotes, pedidos, detalles, currentUser);
    }

    @Test void createOrder_whenLotMissing_shouldTryFallbackThenStop() {
        givenClient();

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.crear(request("1")));

        assertEquals("Lote no encontrado", error.getMessage());
        InOrder order = inOrder(lotes); order.verify(lotes).findByIdForUpdate(2L); order.verify(lotes).findById(2L);
        verifyNoInteractions(pedidos, detalles, currentUser);
    }

    @Test void createOrder_whenLockLookupEmptyButFallbackFindsLot_shouldReserve() {
        givenClient(); givenPersistence(); Lote l = lote(2L);
        when(lotes.findById(2L)).thenReturn(Optional.of(l));

        assertEquals("CONFIRMADO", service.crear(request("1.000")).estado());

        assertEquals(new BigDecimal("11.000"), l.getCantidadReservadaKg()); verify(lotes).save(l);
    }

    @Test void createOrder_whenQuantityExceedsUnreservedUndispatchedStock_shouldNotSave() {
        givenClient(); Lote l = lote(2L); when(lotes.findByIdForUpdate(2L)).thenReturn(Optional.of(l));

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.crear(request("85.001")));

        assertEquals("La cantidad solicitada supera la disponibilidad del lote", error.getMessage());
        assertEquals(new BigDecimal("10.000"), l.getCantidadReservadaKg());
        verifyNoInteractions(pedidos, detalles, currentUser); verify(lotes, never()).save(any());
    }

    @Test void createOrder_whenLotBlocked_shouldNotReserveDespiteAvailableStock() {
        givenClient(); Lote l = lote(2L); l.setEstado("BLOQUEADO");
        when(lotes.findByIdForUpdate(2L)).thenReturn(Optional.of(l));

        assertEquals("El lote no está activo", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.crear(request("1"))).getMessage());

        verifyNoInteractions(pedidos, detalles, currentUser); verify(lotes, never()).save(any());
    }

    @Test void createMultipleOrder_whenValid_shouldKeepBothRelationsAndReserveEachLot() {
        givenClient(); givenPersistence(); Lote first = lote(2L), second = lote(3L);
        when(lotes.findByIdForUpdate(2L)).thenReturn(Optional.of(first));
        when(lotes.findByIdForUpdate(3L)).thenReturn(Optional.of(second));

        var result = service.crear(multiple(2L, 3L, "30.000"));

        verify(detalles, times(2)).save(detalleCaptor.capture());
        var rows = detalleCaptor.getAllValues();
        assertAll(() -> assertEquals(2L, result.loteId()),
                () -> assertSame(rows.getFirst().getPedido(), rows.getLast().getPedido()),
                () -> assertSame(first, rows.getFirst().getLote()), () -> assertSame(second, rows.getLast().getLote()),
                () -> assertEquals(new BigDecimal("30.000"), first.getCantidadReservadaKg()),
                () -> assertEquals(new BigDecimal("40.000"), second.getCantidadReservadaKg()));
        verify(lotes).save(first); verify(lotes).save(second);
    }

    @Test void createMultipleOrder_whenLotRepeated_shouldNotWriteAnything() {
        givenClient(); when(lotes.findByIdForUpdate(2L)).thenReturn(Optional.of(lote(2L)));

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.crear(multiple(2L, 2L, "10")));

        assertEquals("Un pedido no puede repetir el mismo lote", error.getMessage());
        verifyNoInteractions(pedidos, detalles, currentUser); verify(lotes, never()).save(any());
    }

    @Test void createMultipleOrder_whenSecondLotInsufficient_shouldValidateAllBeforeReservingFirst() {
        givenClient(); Lote first = lote(2L), second = lote(3L);
        when(lotes.findByIdForUpdate(2L)).thenReturn(Optional.of(first));
        when(lotes.findByIdForUpdate(3L)).thenReturn(Optional.of(second));

        assertThrowsExactly(IllegalArgumentException.class, () -> service.crear(multiple(2L, 3L, "86")));

        assertEquals(new BigDecimal("10.000"), first.getCantidadReservadaKg());
        verifyNoInteractions(pedidos, detalles, currentUser); verify(lotes, never()).save(any());
    }

    @Test void createOrder_whenOrderSaveFails_shouldNotCreateDetailsOrMutateReservation() {
        givenClient(); Lote l = lote(2L); when(lotes.findByIdForUpdate(2L)).thenReturn(Optional.of(l));
        when(currentUser.get()).thenReturn(usuario());
        var cause = new DataIntegrityViolationException("Unique constraint");
        when(pedidos.save(any())).thenThrow(cause);

        assertSame(cause, assertThrowsExactly(DataIntegrityViolationException.class, () -> service.crear(request("1"))));

        verifyNoInteractions(detalles); verify(lotes, never()).save(any());
        assertEquals(new BigDecimal("10.000"), l.getCantidadReservadaKg());
    }
}
