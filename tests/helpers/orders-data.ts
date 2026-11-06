import { expect, type APIRequestContext, type Browser } from '@playwright/test';
import { ClientesPage, type ClienteFormData } from '../pages/ClientesPage';
import { csrfHeaders, jsonBody } from './authorization';
import { signInByUi } from './ui-session';
import type { ClientRecord } from './clients-data';
import { expectMySqlTimestamp } from './timestamps';

export const ORDER_CLIENT: Readonly<ClienteFormData> = Object.freeze({
  razonSocial: '[E2E RIT-47] Cliente de pedidos', pais: 'ES',
  identificacionFiscal: 'E2E-RIT47-CLIENTE-UNICO', email: 'rit47-orders@ritech.invalid',
  telefono: '0000000047', contactoPrincipal: 'Equipo E2E RIT-47',
  direccion: 'Registro reservado para Pedidos E2E RIT-47',
});
export const ORDER_PRODUCT = Object.freeze({ codigo: 'E2E-RIT47-PRODUCTO',
  nombre: '[E2E RIT-47] Café de pruebas', tipo: 'CAFE', descripcion: 'Uso exclusivo de Pedidos E2E RIT-47' });
export const ORDER_LOT = Object.freeze({ codigo: 'E2E-RIT47-LOTE-UNICO',
  origen: 'Inventario ficticio reservado RIT-47', proveedorReferencia: 'E2E-RIT47',
  fechaRecepcion: '2026-01-01', cantidadInicialKg: 1 });
export const ORDER_QUANTITY_KG = 1;
export const ORDER_NOTES = '[E2E RIT-47] Pedido único reutilizable; no despachar ni exportar';

export type ProductRecord = typeof ORDER_PRODUCT & { id: number; unidadMedida: string; activo: boolean };
export type LotRecord = { id: number; codigo: string; productoId: number; producto: string; tipoProducto: string;
  origen: string; fechaRecepcion: string; cantidadInicialKg: number; cantidadReservadaKg: number;
  cantidadDespachadaKg: number; cantidadDisponibleKg: number; estado: string };
export type OrderRecord = { id: number; codigo: string; clienteId: number; cliente: string; paisDestino: string;
  estado: string; fechaPedido: string; loteId: number; lote: string; cantidadKg: number };
export type OrderDetail = { id: number; loteId: number; lote: string; producto: string; cantidadKg: number };
export type BusinessSnapshot = { clientes: ClientRecord[]; productos: ProductRecord[]; lotes: LotRecord[]; pedidos: OrderRecord[] };
export type OrderResources = { client: ClientRecord; product: ProductRecord; lot: LotRecord };

/** MySQL fecha_pedido is DATETIME(6); the POST's LocalDateTime may contain nanoseconds. */
export function expectPersistedOrder(saved: OrderRecord, persisted: OrderRecord): void {
  const { fechaPedido: responseDate, ...responseFields } = saved;
  const { fechaPedido: storedDate, ...storedFields } = persisted;
  expect(storedFields).toEqual(responseFields);
  expectMySqlTimestamp(responseDate, storedDate);
}

export async function readList<T>(request: APIRequestContext, endpoint: string): Promise<T[]> {
  const response = await request.get(endpoint, { maxRedirects: 0 });
  expect(response.status(), endpoint).toBe(200);
  const body = await jsonBody(response);
  expect(Array.isArray(body), `${endpoint}: contrato array`).toBe(true);
  return body as T[];
}

export async function businessSnapshot(request: APIRequestContext): Promise<BusinessSnapshot> {
  const [clientes, productos, lotes, pedidos] = await Promise.all([
    readList<ClientRecord>(request, '/api/clientes'), readList<ProductRecord>(request, '/api/productos'),
    readList<LotRecord>(request, '/api/lotes'), readList<OrderRecord>(request, '/api/pedidos'),
  ]);
  return { clientes, productos, lotes, pedidos };
}

function one<T>(records: T[], label: string): T | undefined {
  expect(records.length, `${label}: nunca más de un registro reservado`).toBeLessThanOrEqual(1);
  return records[0];
}
function sameCode(a: string, b: string): boolean { return a.toUpperCase() === b; }

function ownedClient(records: ClientRecord[]): ClientRecord | undefined {
  const client = one(records.filter(c => sameCode(c.identificacionFiscal, ORDER_CLIENT.identificacionFiscal)), 'cliente RIT-47');
  if (client) {
    const fields = Object.fromEntries(Object.keys(ORDER_CLIENT).map(key => [key, client[key as keyof ClienteFormData]]));
    expect(fields, 'Colisión de identidad: no sobrescribir clientes ajenos').toEqual(ORDER_CLIENT);
    expect(client.activo, 'El cliente RIT-47 permanece activo para ser seleccionable').toBe(true);
    expect(client.id).toBeGreaterThan(0);
  }
  return client;
}
function ownedProduct(records: ProductRecord[]): ProductRecord | undefined {
  const product = one(records.filter(p => sameCode(p.codigo, ORDER_PRODUCT.codigo)), 'producto RIT-47');
  if (product) expect(product).toMatchObject({ ...ORDER_PRODUCT, activo: true, unidadMedida: 'KG' });
  return product;
}
function ownedLot(records: LotRecord[], product?: ProductRecord): LotRecord | undefined {
  const lot = one(records.filter(l => sameCode(l.codigo, ORDER_LOT.codigo)), 'lote RIT-47');
  if (lot) {
    expect(product, 'El lote reservado debe pertenecer al producto reservado').toBeDefined();
    expect(lot).toMatchObject({ codigo: ORDER_LOT.codigo, productoId: product!.id,
      producto: ORDER_PRODUCT.nombre, tipoProducto: ORDER_PRODUCT.tipo, origen: ORDER_LOT.origen,
      fechaRecepcion: ORDER_LOT.fechaRecepcion, cantidadInicialKg: 1, cantidadDespachadaKg: 0, estado: 'ACTIVO' });
    expect([0, 1]).toContain(lot.cantidadReservadaKg);
    expect(lot.cantidadDisponibleKg).toBe(1 - lot.cantidadReservadaKg);
  }
  return lot;
}

/** Keep every pre-existing record outside RIT-47 byte-for-byte at the API contract level. */
export function protectedBusiness(snapshot: BusinessSnapshot): BusinessSnapshot {
  const sort = <T extends { id: number }>(rows: T[]) => rows.sort((a, b) => a.id - b.id);
  const clients = snapshot.clientes.filter(c => sameCode(c.identificacionFiscal, ORDER_CLIENT.identificacionFiscal));
  const lots = snapshot.lotes.filter(l => sameCode(l.codigo, ORDER_LOT.codigo));
  return {
    clientes: sort(snapshot.clientes.filter(c => !clients.some(own => own.id === c.id))),
    productos: sort(snapshot.productos.filter(p => !sameCode(p.codigo, ORDER_PRODUCT.codigo))),
    lotes: sort(snapshot.lotes.filter(l => !lots.some(own => own.id === l.id))),
    pedidos: sort(snapshot.pedidos.filter(p => !clients.some(c => c.id === p.clienteId) && !lots.some(l => l.id === p.loteId))),
  };
}

export async function ensureOrderResources(request: APIRequestContext, browser: Browser, baseURL: string): Promise<OrderResources> {
  const before = await businessSnapshot(request);
  // Check all existing reserved identities before inserting anything; collisions fail closed.
  let client = ownedClient(before.clientes);
  let product = ownedProduct(before.productos);
  let lot = ownedLot(before.lotes, product);
  if (!client) {
    const setupContext = await browser.newContext({ baseURL });
    try {
      const page = await setupContext.newPage();
      await signInByUi(page, 'comercial');
      const clientsPage = new ClientesPage(page);
      await clientsPage.open();
      await clientsPage.fillForm({ ...ORDER_CLIENT });
      const response = await clientsPage.saveAndWaitForResponse();
      expect(response.status()).toBe(200);
      await clientsPage.expectSuccess('Cliente registrado');
      client = ownedClient(await readList<ClientRecord>(request, '/api/clientes'));
      expect(client).toBeDefined();
      await clientsPage.expectClientRow(client!);
    } finally { await setupContext.close(); }
  }
  if (!product) {
    const response = await request.post('/api/productos', { maxRedirects: 0,
      headers: await csrfHeaders(request), data: ORDER_PRODUCT });
    expect(response.status()).toBe(200);
    await jsonBody(response);
    product = ownedProduct(await readList<ProductRecord>(request, '/api/productos'));
    expect(product).toBeDefined();
  }
  if (!lot) {
    const response = await request.post('/api/lotes', { maxRedirects: 0,
      headers: await csrfHeaders(request), data: { ...ORDER_LOT, productoId: product!.id } });
    expect(response.status()).toBe(200);
    await jsonBody(response);
    lot = ownedLot(await readList<LotRecord>(request, '/api/lotes'), product);
    expect(lot).toBeDefined();
  }
  return { client: client!, product: product!, lot: lot! };
}

export function findReservedOrder(orders: OrderRecord[], resources: OrderResources): OrderRecord | undefined {
  const order = one(orders.filter(p => p.clienteId === resources.client.id || p.loteId === resources.lot.id), 'pedido RIT-47');
  if (order) {
    expect(order).toMatchObject({ clienteId: resources.client.id, cliente: ORDER_CLIENT.razonSocial,
      loteId: resources.lot.id, lote: ORDER_LOT.codigo, cantidadKg: ORDER_QUANTITY_KG, paisDestino: 'ES', estado: 'CONFIRMADO' });
    expect(order.id).toBeGreaterThan(0);
    expect(order.codigo).toMatch(/^PED-[A-F0-9]{8}$/);
    expect(Number.isNaN(Date.parse(order.fechaPedido))).toBe(false);
  }
  return order;
}

export async function expectOrderDetails(request: APIRequestContext, order: OrderRecord, resources: OrderResources): Promise<OrderDetail[]> {
  const details = await readList<OrderDetail>(request, `/api/pedidos/${order.id}`);
  expect(details).toHaveLength(1);
  expect(details[0]).toMatchObject({ id: expect.any(Number), loteId: resources.lot.id,
    lote: ORDER_LOT.codigo, producto: ORDER_PRODUCT.nombre, cantidadKg: ORDER_QUANTITY_KG });
  expect(details[0].id).toBeGreaterThan(0);
  return details;
}

export async function expectKnownOrderState(request: APIRequestContext, resources: OrderResources): Promise<OrderRecord | undefined> {
  const snapshot = await businessSnapshot(request);
  expect(ownedClient(snapshot.clientes)?.id).toBe(resources.client.id);
  expect(ownedProduct(snapshot.productos)?.id).toBe(resources.product.id);
  const lot = ownedLot(snapshot.lotes, resources.product);
  expect(lot?.id).toBe(resources.lot.id);
  const order = findReservedOrder(snapshot.pedidos, resources);
  expect(lot!.cantidadReservadaKg).toBe(order ? ORDER_QUANTITY_KG : 0);
  expect(lot!.cantidadDisponibleKg).toBe(order ? 0 : 1);
  if (order) await expectOrderDetails(request, order, resources);
  return order;
}
