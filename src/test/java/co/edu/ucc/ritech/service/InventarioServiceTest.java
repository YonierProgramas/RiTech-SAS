package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.domain.*;
import co.edu.ucc.ritech.repository.*;
import co.edu.ucc.ritech.web.ApiDtos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static co.edu.ucc.ritech.support.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Inventario: productos, lotes y disponibilidad")
class InventarioServiceTest {
    @Mock ProductoRepository productos;
    @Mock LoteRepository lotes;
    @Mock CurrentUserService currentUser;
    @InjectMocks InventarioService service;
    @Captor ArgumentCaptor<Lote> loteCaptor;

    private ProductoRequest productRequest(String code) {
        return new ProductoRequest(code, " Cafe especial ", "cafe", "Descripcion");
    }
    private LoteRequest lotRequest() {
        return new LoteRequest(" lote-nuevo ", 2L, " Huila ", "Proveedor", DATE, new BigDecimal("25.500"));
    }
    private LoteUpdateRequest updateRequest() { return new LoteUpdateRequest(" Tolima ", "Nuevo", DATE.plusDays(1)); }

    @Test void listProducts_shouldUseNameOrdering() {
        var rows = List.of(producto(2L)); when(productos.findAllByOrderByNombreAsc()).thenReturn(rows);

        assertSame(rows, service.listarProductos()); verify(productos).findAllByOrderByNombreAsc();
    }

    @Test void listLots_shouldUseGeneratedAvailabilityOrCalculateItsFallback() {
        Lote calculated = lote(1L), generated = lote(2L);
        generated.setCantidadDisponibleKg(new BigDecimal("81.750"));
        when(lotes.findAllByOrderByCreadoEnDesc()).thenReturn(List.of(calculated, generated));

        var result = service.listarLotes();

        assertAll(() -> assertEquals(new BigDecimal("85.000"), result.getFirst().cantidadDisponibleKg()),
                () -> assertEquals(new BigDecimal("81.750"), result.getLast().cantidadDisponibleKg()),
                () -> assertEquals("Cafe 2", result.getFirst().producto()),
                () -> assertEquals("CAFE", result.getFirst().tipoProducto()));
    }

    @Test void createProduct_whenValid_shouldNormalizeAndSetDefaults() {
        when(productos.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var p = service.crearProducto(productRequest(" prod-nuevo "));

        assertAll(() -> assertEquals("PROD-NUEVO", p.getCodigo()), () -> assertEquals("Cafe especial", p.getNombre()),
                () -> assertEquals("CAFE", p.getTipo()), () -> assertEquals("KG", p.getUnidadMedida()),
                () -> assertTrue(p.getActivo()), () -> assertEquals("Descripcion", p.getDescripcion()));
        verify(productos).save(p);
    }

    @Test void createProduct_whenCodeExists_shouldNotSave() {
        when(productos.existsByCodigoIgnoreCase("PROD-2")).thenReturn(true);

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.crearProducto(productRequest("PROD-2")));

        assertEquals("Código de producto duplicado", error.getMessage()); verify(productos, never()).save(any());
    }

    @Test void updateProduct_whenCodeOnlyChangesCase_shouldNotQueryDuplicates() {
        Producto p = producto(2L); when(productos.findById(2L)).thenReturn(Optional.of(p)); when(productos.save(p)).thenReturn(p);

        assertEquals("Cafe especial", service.actualizarProducto(2L, productRequest("prod-2")).getNombre());

        verify(productos, never()).existsByCodigoIgnoreCase(anyString()); assertTrue(p.getActivo());
    }

    @Test void updateProduct_whenCodeChanges_shouldCheckUniquenessAndNormalize() {
        Producto p = producto(2L); when(productos.findById(2L)).thenReturn(Optional.of(p)); when(productos.save(p)).thenReturn(p);

        assertEquals("NUEVO", service.actualizarProducto(2L, productRequest("nuevo")).getCodigo());

        verify(productos).existsByCodigoIgnoreCase("nuevo"); verify(productos).save(p);
    }

    @Test void updateProduct_whenCodeIsDuplicated_shouldKeepOriginalCode() {
        Producto p = producto(2L); when(productos.findById(2L)).thenReturn(Optional.of(p));
        when(productos.existsByCodigoIgnoreCase("OTRO")).thenReturn(true);

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.actualizarProducto(2L, productRequest("OTRO")));

        assertEquals("Código de producto duplicado", error.getMessage());
        assertEquals("PROD-2", p.getCodigo()); verify(productos, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"actualizar", "cambiarEstado"})
    void mutateProduct_whenMissing_shouldNotSave(String operation) {
        when(productos.findById(99L)).thenReturn(Optional.empty());

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> {
            if (operation.equals("actualizar")) service.actualizarProducto(99L, productRequest("X"));
            else service.cambiarEstadoProducto(99L);
        });

        assertEquals("Producto no encontrado", error.getMessage()); verify(productos, never()).save(any());
    }

    @ParameterizedTest @NullSource @ValueSource(booleans = {true, false})
    void toggleProductStatus_shouldInvertTrueAndTreatNullAsInactive(Boolean previous) {
        Producto p = producto(2L); p.setActivo(previous);
        when(productos.findById(2L)).thenReturn(Optional.of(p)); when(productos.save(p)).thenReturn(p);

        assertEquals(!Boolean.TRUE.equals(previous), service.cambiarEstadoProducto(2L).getActivo());

        verify(productos).save(p);
    }

    @Test void createLot_whenValid_shouldAssociateProductActorAndZeroCounters() {
        Producto p = producto(2L); Usuario actor = usuario();
        when(productos.findById(2L)).thenReturn(Optional.of(p)); when(currentUser.get()).thenReturn(actor);
        when(lotes.saveAndFlush(any())).thenAnswer(inv -> { Lote l = inv.getArgument(0); l.setId(8L); return l; });

        var result = service.crearLote(lotRequest());

        verify(lotes).saveAndFlush(loteCaptor.capture()); Lote saved = loteCaptor.getValue();
        assertAll(() -> assertEquals("LOTE-NUEVO", result.codigo()), () -> assertSame(p, saved.getProducto()),
                () -> assertSame(actor, saved.getCreadoPor()), () -> assertNotNull(saved.getCreadoEn()),
                () -> assertEquals("Huila", saved.getOrigen()), () -> assertEquals("ACTIVO", result.estado()),
                () -> assertEquals(BigDecimal.ZERO, result.cantidadReservadaKg()),
                () -> assertEquals(BigDecimal.ZERO, result.cantidadDespachadaKg()),
                () -> assertEquals(new BigDecimal("25.500"), result.cantidadDisponibleKg()));
    }

    @Test void createLot_whenCodeExists_shouldStopBeforeProductLookup() {
        when(lotes.existsByCodigoIgnoreCase(" lote-nuevo ")).thenReturn(true);

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.crearLote(lotRequest()));

        assertEquals("Código de lote duplicado", error.getMessage());
        verifyNoInteractions(productos, currentUser); verify(lotes, never()).saveAndFlush(any());
    }

    @Test void createLot_whenProductIsMissing_shouldNotResolveActorOrSave() {
        when(productos.findById(2L)).thenReturn(Optional.empty());

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.crearLote(lotRequest()));

        assertEquals("Producto no encontrado", error.getMessage());
        verifyNoInteractions(currentUser); verify(lotes, never()).saveAndFlush(any());
    }

    @Test void updateLot_whenActiveWithoutMovements_shouldEditOnlyAllowedFields() {
        Lote l = lote(1L); l.setCantidadReservadaKg(BigDecimal.ZERO); l.setCantidadDespachadaKg(BigDecimal.ZERO);
        when(lotes.findById(1L)).thenReturn(Optional.of(l)); when(lotes.save(l)).thenReturn(l);

        var result = service.actualizarLote(1L, updateRequest());

        assertAll(() -> assertEquals("Tolima", result.origen()), () -> assertEquals(DATE.plusDays(1), result.fechaRecepcion()),
                () -> assertEquals(new BigDecimal("100.000"), result.cantidadInicialKg()),
                () -> assertEquals("Nuevo", l.getProveedorReferencia())); verify(lotes).save(l);
    }

    @ParameterizedTest @CsvSource({"BLOQUEADO,0,0", "ACTIVO,1,0", "ACTIVO,0,1"})
    void updateLot_whenBlockedReservedOrDispatched_shouldNotModify(String state, String reserved, String dispatched) {
        Lote l = lote(1L); l.setEstado(state); l.setCantidadReservadaKg(new BigDecimal(reserved));
        l.setCantidadDespachadaKg(new BigDecimal(dispatched)); when(lotes.findById(1L)).thenReturn(Optional.of(l));

        var error = assertThrowsExactly(IllegalArgumentException.class, () -> service.actualizarLote(1L, updateRequest()));

        assertEquals("Solo se puede editar un lote activo sin reservas ni despachos", error.getMessage());
        assertEquals("Huila", l.getOrigen()); verify(lotes, never()).save(any());
    }

    @Test void updateLot_whenMissing_shouldNotSave() {
        when(lotes.findById(99L)).thenReturn(Optional.empty());

        assertEquals("Lote no encontrado", assertThrowsExactly(IllegalArgumentException.class,
                () -> service.actualizarLote(99L, updateRequest())).getMessage());

        verify(lotes, never()).save(any());
    }
}
