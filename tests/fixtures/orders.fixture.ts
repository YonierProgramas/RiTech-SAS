import { test as base, expect } from './ritech.fixture';
import type { PedidosPage, PedidoFormData } from '../pages/PedidosPage';
import { jsonBody } from '../helpers/authorization';
import {
  businessSnapshot, ensureOrderResources, expectKnownOrderState, expectPersistedOrder, findReservedOrder,
  protectedBusiness, ORDER_NOTES, ORDER_QUANTITY_KG, type OrderRecord, type OrderResources,
} from '../helpers/orders-data';

type DedicatedOrder = OrderResources & { order: OrderRecord; form: PedidoFormData; createdViaUi: boolean };
type OrdersFixtures = { orderModule: PedidosPage; dedicatedOrder: DedicatedOrder };

export const test = base.extend<OrdersFixtures>({
  orderModule: async ({ signInAs, pedidosPage, context }, use, testInfo) => {
    await signInAs('ventas');
    const before = await businessSnapshot(context.request);
    try {
      await pedidosPage.open();
      await use(pedidosPage);
    } finally {
      const after = await businessSnapshot(context.request);
      expect(protectedBusiness(after), 'Clientes, productos, lotes y pedidos fuera de RIT-47 permanecen intactos')
        .toEqual(protectedBusiness(before));
      await testInfo.attach('pedidos-integridad-protegida', {
        contentType: 'application/json', body: JSON.stringify({ unchanged: true,
          recordsCompared: Object.fromEntries(Object.entries(protectedBusiness(before)).map(([key, rows]) => [key, rows.length])) }),
      });
    }
  },
  dedicatedOrder: async ({ orderModule, context, browser, baseURL }, use, testInfo) => {
    expect(baseURL).toBeTruthy();
    const initial = await businessSnapshot(context.request);
    let resources: OrderResources | undefined;
    let createdViaUi = false;
    let beforeReserved = 0;
    let ready = false;
    try {
      resources = await ensureOrderResources(context.request, browser, baseURL!);
      beforeReserved = resources.lot.cantidadReservadaKg;
      let order = await expectKnownOrderState(context.request, resources);
      const form: PedidoFormData = { clienteId: resources.client.id, loteId: resources.lot.id,
        paisDestino: 'ES', cantidadKg: ORDER_QUANTITY_KG, observaciones: ORDER_NOTES };
      await orderModule.open();
      if (!order) {
        order = await base.step('Crear por UI el único pedido RIT-47 y reservar exactamente 1 kg propio', async () => {
          await orderModule.fillForm(form);
          const response = await orderModule.createAndWaitForResponse();
          expect(response.status()).toBe(200);
          createdViaUi = true;
          const saved = await jsonBody(response) as OrderRecord;
          await orderModule.expectSuccess();
          await orderModule.expectOrderSummary(saved);
          const persisted = await expectKnownOrderState(context.request, resources!);
          expect(persisted).toBeDefined();
          expectPersistedOrder(saved, persisted!);
          expect(persisted!.id).toBe(saved.id);
          return persisted!;
        });
      }
      testInfo.annotations.push({ type: 'pedido-e2e',
        description: `id=${order.id}; codigo=${order.codigo}; ${createdViaUi ? 'creado por UI' : 'reutilizado'}; CONFIRMADO; reserva=1 kg` });
      ready = true;
      await use({ ...resources, order, form, createdViaUi });
    } finally {
      if (resources) {
        const final = await expectKnownOrderState(context.request, resources);
        // A failed setup may leave only the bounded resources, with 0 kg reserved.
        // Verify that safe state without replacing the original setup error.
        if (ready) expect(final, 'La fixture debe terminar con un único pedido confirmado').toBeDefined();
        const after = await businessSnapshot(context.request);
        const finalLot = after.lotes.find(l => l.id === resources!.lot.id)!;
        expect(findReservedOrder(after.pedidos, resources)).toEqual(final);
        await testInfo.attach('pedido-e2e-lifecycle', {
          contentType: 'application/json', body: JSON.stringify({
            orderId: final?.id ?? null, orderCode: final?.codigo ?? null, clientId: resources.client.id,
            productId: resources.product.id, lotId: resources.lot.id, createdViaUi,
            resourcesCreated: { clients: after.clientes.length - initial.clientes.length,
              products: after.productos.length - initial.productos.length, lots: after.lotes.length - initial.lotes.length },
            newOrders: after.pedidos.length - initial.pedidos.length,
            state: final?.estado ?? 'NO_CREADO', initialKg: finalLot.cantidadInicialKg,
            reservedBeforeKg: beforeReserved, reservedAfterKg: finalLot.cantidadReservadaKg,
            dispatchedKg: finalLot.cantidadDespachadaKg, availableKg: finalLot.cantidadDisponibleKg,
          }, null, 2),
        });
      }
    }
  },
});

export { expect } from '@playwright/test';
