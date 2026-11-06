import { expect, type APIRequestContext } from '@playwright/test';
import { businessSnapshot, ORDER_LOT, ORDER_PRODUCT, readList, type BusinessSnapshot, type LotRecord, type OrderDetail } from './orders-data';
import type { InspeccionFormData, VisibleInspection } from '../pages/CalidadPage';
import { expectMySqlTimestamp } from './timestamps';

export const QUALITY_MARKER = '[E2E RIT-48]';
export const QUALITY_INSPECTION = Object.freeze({
  humedadPorcentaje: 12.34, evaluacionTaza: '[E2E RIT-48] Taza de prueba',
  aroma: '[E2E RIT-48] Aroma de prueba', defectos: '[E2E RIT-48] Sin defectos',
  observaciones: '[E2E RIT-48] Inspección única reutilizable; no modificar', resultado: 'APROBADO' as const,
});
export type InspectionRecord = VisibleInspection & {
  id: number; loteId: number; evaluacionTaza: string | null; aroma: string | null; defectos: string | null;
};
export type QualitySnapshot = {
  business: BusinessSnapshot; inspections: InspectionRecord[];
  orderDetails: { id: number; details: OrderDetail[] }[];
};

export async function qualitySnapshot(request: APIRequestContext): Promise<QualitySnapshot> {
  const [business, inspections] = await Promise.all([
    businessSnapshot(request), readList<InspectionRecord>(request, '/api/calidad'),
  ]);
  const orderDetails = await Promise.all([...business.pedidos].sort((a, b) => a.id - b.id).map(async order => ({
    id: order.id, details: await readList<OrderDetail>(request, `/api/pedidos/${order.id}`),
  })));
  return { business, inspections, orderDetails };
}

export function qualityLot(lots: LotRecord[]): LotRecord {
  const matches = lots.filter(l => l.codigo.toUpperCase() === ORDER_LOT.codigo);
  expect(matches, 'Precondición: ejecutar RIT-47 para disponer de su único lote reservado').toHaveLength(1);
  const lot = matches[0];
  expect(lot).toMatchObject({ codigo: ORDER_LOT.codigo, producto: ORDER_PRODUCT.nombre, tipoProducto: ORDER_PRODUCT.tipo,
    origen: ORDER_LOT.origen, fechaRecepcion: ORDER_LOT.fechaRecepcion, estado: 'ACTIVO',
    cantidadInicialKg: 1, cantidadReservadaKg: 1, cantidadDespachadaKg: 0, cantidadDisponibleKg: 0 });
  expect(lot.id).toBeGreaterThan(0);
  return lot;
}

/** Treat any inspection of the reserved lot as a collision unless it is our exact fixture. */
export function findQualityInspection(records: InspectionRecord[], lot: LotRecord, inspector?: string): InspectionRecord | undefined {
  const candidates = records.filter(i => i.loteId === lot.id || i.observaciones?.startsWith(QUALITY_MARKER)
    || i.evaluacionTaza?.startsWith(QUALITY_MARKER));
  expect(candidates.length, 'RIT-48 permite una sola inspección, nunca registros nuevos por corrida').toBeLessThanOrEqual(1);
  const inspection = candidates[0];
  if (inspection) {
    expect(inspection, 'Colisión o datos modificados: no sobrescribir ni crear otra inspección').toMatchObject({
      ...QUALITY_INSPECTION, loteId: lot.id, lote: lot.codigo,
    });
    expect(inspection.id).toBeGreaterThan(0);
    expect(inspection.inspector).toMatch(/\S/);
    if (inspector) expect(inspection.inspector).toBe(inspector);
    expect(Number.isNaN(Date.parse(inspection.fechaInspeccion))).toBe(false);
  }
  return inspection;
}

export function inspectionForm(lot: LotRecord): InspeccionFormData {
  return { loteId: lot.id, ...QUALITY_INSPECTION };
}

export function expectPersistedInspection(saved: InspectionRecord, stored: InspectionRecord): void {
  const { fechaInspeccion: responseDate, ...responseFields } = saved;
  const { fechaInspeccion: storedDate, ...storedFields } = stored;
  expect(storedFields).toEqual(responseFields);
  expectMySqlTimestamp(responseDate, storedDate);
}

export function expectDescendingInspectionDates(records: InspectionRecord[]): void {
  // Both repository methods explicitly order by fechaInspeccion DESC; no ID tie-break is promised.
  const normalize = (value: string) => {
    const [seconds, fraction = ''] = value.split('.');
    return `${seconds}.${fraction.padEnd(9, '0')}`;
  };
  for (let index = 1; index < records.length; index++) {
    const previous = normalize(records[index - 1].fechaInspeccion);
    const current = normalize(records[index].fechaInspeccion);
    expect(previous >= current, 'Fechas en orden descendente; empates permitidos').toBe(true);
  }
}

export function expectQualityIntegrity(before: QualitySnapshot, after: QualitySnapshot): InspectionRecord | undefined {
  expect(after.business, 'Calidad no modifica clientes/productos/lotes/pedidos ni stock').toEqual(before.business);
  expect(after.orderDetails, 'Las líneas de los pedidos existentes permanecen intactas').toEqual(before.orderDetails);
  const sort = (rows: InspectionRecord[]) => [...rows].sort((a, b) => a.id - b.id);
  const originalIds = new Set(before.inspections.map(i => i.id));
  expect(sort(after.inspections.filter(i => originalIds.has(i.id))), 'Todas las inspecciones preexistentes, incluidas DEMO, son inmutables')
    .toEqual(sort(before.inspections));
  const lot = qualityLot(after.business.lotes);
  const owned = findQualityInspection(after.inspections, lot);
  const added = after.inspections.filter(i => !originalIds.has(i.id));
  expect(added.length).toBeLessThanOrEqual(1);
  if (added.length) expect(added).toEqual([owned]);
  return owned;
}
