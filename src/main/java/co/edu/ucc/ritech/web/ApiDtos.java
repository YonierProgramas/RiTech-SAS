package co.edu.ucc.ritech.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ApiDtos {
    private ApiDtos() {}

    public record SessionView(Long id, String nombre, String email, String rol) {}
    public record DashboardView(long clientes, long productos, long lotes, long pedidos, long exportaciones, long inspecciones) {}

    public record ClienteRequest(
            @NotBlank @Size(max = 150) String razonSocial,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String pais,
            @NotBlank @Size(max = 50) String identificacionFiscal,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(max = 30) String telefono,
            @NotBlank @Size(max = 255) String direccion,
            @NotBlank @Size(max = 120) String contactoPrincipal) {}

        public record EstadoRequest(@NotNull Boolean activo) {}

    public record ProductoRequest(
            @NotBlank @Size(max = 30) String codigo,
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Pattern(regexp = "CAFE|CACAO") String tipo,
            @Size(max = 255) String descripcion) {}

    public record TramiteRequest(
            @NotBlank @Size(max = 60) String entidad,
            @NotBlank @Size(max = 100) String tipoTramite,
            @NotBlank @Size(max = 120) String referencia,
            @NotBlank @Size(max = 60) String estadoReportado,
            @Size(max = 2000) String observaciones) {}

    public record RequisitoView(Long id, String codigo, String nombre, Boolean obligatorio, Boolean activo) {}

    public record TramiteView(Long id, String entidad, String tipoTramite, String referencia,
                              String estadoReportado, String observaciones, LocalDateTime registradoEn,
                              String registradoPor) {}

        public record LoteUpdateRequest(
                        @NotBlank @Size(max = 255) String origen,
                        @Size(max = 150) String proveedorReferencia,
                        @NotNull LocalDate fechaRecepcion) {}

    public record LoteRequest(
            @NotBlank @Size(max = 40) String codigo,
            @NotNull Long productoId,
            @NotBlank @Size(max = 255) String origen,
            @Size(max = 150) String proveedorReferencia,
            @NotNull LocalDate fechaRecepcion,
            @NotNull @DecimalMin(value = "0.001") BigDecimal cantidadInicialKg) {}

    public record LoteView(Long id, String codigo, Long productoId, String producto, String tipoProducto,
                           String origen, LocalDate fechaRecepcion, BigDecimal cantidadInicialKg,
                           BigDecimal cantidadReservadaKg, BigDecimal cantidadDespachadaKg,
                           BigDecimal cantidadDisponibleKg, String estado) {}

    public record PedidoRequest(
            @NotNull Long clienteId,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String paisDestino,
            @NotNull Long loteId,
            @NotNull @DecimalMin(value = "0.001") BigDecimal cantidadKg,
            @Size(max = 2000) String observaciones) {}

    public record PedidoDetalleRequest(
            @NotNull Long loteId,
            @NotNull @DecimalMin(value = "0.001") BigDecimal cantidadKg) {}

    public record PedidoMultipleRequest(
            @NotNull Long clienteId,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String paisDestino,
            @NotEmpty @Size(max = 50) List<@Valid PedidoDetalleRequest> detalles,
            @Size(max = 2000) String observaciones) {}

    public record PedidoView(Long id, String codigo, Long clienteId, String cliente, String paisDestino,
                             String estado, LocalDateTime fechaPedido, Long loteId, String lote,
                             BigDecimal cantidadKg) {}

        public record PedidoDetalleView(Long id, Long loteId, String lote, String producto, BigDecimal cantidadKg) {}

    public record ExportacionRequest(
            @NotNull Long pedidoId,
            LocalDate fechaSalidaPrevista) {}

    public record ExportacionView(Long id, String codigo, String pedido, String cliente, String paisDestino,
                                  String estado, Boolean requisitosRevisados, LocalDate fechaSalidaPrevista,
                                  LocalDateTime fechaSalidaReal, LocalDateTime fechaEntrega, String responsable) {}

    public record CambioEstadoRequest(
            @NotBlank @Pattern(regexp = "PREPARACION|LISTA_DESPACHO|ADUANA|EN_TRANSITO|ENTREGADA|CANCELADA") String estado,
            @NotBlank @Size(max = 500) String observacion) {}

    public record DocumentoEstadoRequest(
            @NotBlank @Pattern(regexp = "CARGADO|EN_REVISION|APROBADO|RECHAZADO") String estado,
            @Size(max = 500) String observacion) {}

    public record HistorialView(Long id, String estadoAnterior, String estadoNuevo, LocalDateTime fechaEvento,
                                String usuario, String observacion) {}

    public record InspeccionRequest(
            @NotNull Long loteId,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal humedadPorcentaje,
            @Size(max = 150) String evaluacionTaza,
            @Size(max = 150) String aroma,
            String defectos,
            String observaciones,
            @NotBlank @Pattern(regexp = "PENDIENTE|APROBADO|RECHAZADO") String resultado) {}

    public record InspeccionView(Long id, Long loteId, String lote, String inspector, LocalDateTime fechaInspeccion,
                                 BigDecimal humedadPorcentaje, String evaluacionTaza, String aroma,
                                 String defectos, String observaciones, String resultado) {}

    public record TrazabilidadView(String codigoLote, List<java.util.Map<String,Object>> eventos) {}
}
