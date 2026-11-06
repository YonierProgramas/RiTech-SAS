import { expect, type APIRequestContext } from '@playwright/test';
import { jsonBody } from './authorization';
import { readList } from './orders-data';
import { qualitySnapshot, qualityLot, findQualityInspection, type QualitySnapshot } from './quality-data';
import type { TraceEvent, TraceView } from '../pages/TrazabilidadPage';

export const MISSING_TRACE_CODE = 'E2E-TRACE-NO-EXISTE';
export const TRACE_NOT_FOUND = 'No se encontró trazabilidad para el lote indicado';
export const DEMO_TRACE_CODE = 'DEMO-LOTE-001';
export type ExportRecord = { id: number; codigo: string; pedido: string; estado: string };
export type ExportHistory = { id: number; estadoNuevo: string; observacion: string };
export type TraceSnapshot = QualitySnapshot & {
  exports: ExportRecord[];
  exportDetails: { id: number; history: ExportHistory[]; checklist: unknown[]; procedures: unknown[] }[];
  traces: TraceView[];
};

export function tracePath(code: string): string { return `/api/trazabilidad/${encodeURIComponent(code)}`; }

export function expectTraceContract(view: TraceView, code: string): void {
  expect(view).toMatchObject({ codigoLote: code, eventos: expect.any(Array) });
  expect(view.eventos.length, 'La vista siempre incluye REGISTRO_LOTE para un lote existente').toBeGreaterThan(0);
  for (const event of view.eventos) {
    expect(event).toMatchObject({ origen_evento: expect.any(String), tipo_evento: expect.any(String),
      fecha_evento: expect.any(String), descripcion: expect.any(String), referencia_id: expect.any(Number), usuario_id: expect.any(Number) });
    expect(event.origen_evento).toMatch(/\S/);
    expect(event.tipo_evento).toMatch(/\S/);
    expect(event.descripcion).toMatch(/\S/);
    for (const id of [event.referencia_id, event.usuario_id]) {
      expect(Number.isSafeInteger(id)).toBe(true);
      expect(id).toBeGreaterThan(0);
    }
    expect(event.fecha_evento).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?$/);
    expect(Number.isNaN(Date.parse(event.fecha_evento))).toBe(false);
  }
}

export async function readTrace(request: APIRequestContext, code: string): Promise<TraceView> {
  const response = await request.get(tracePath(code), { maxRedirects: 0 });
  expect(response.status()).toBe(200);
  const view = await jsonBody(response) as TraceView;
  expectTraceContract(view, code);
  return view;
}

/** SQL guarantees fecha_evento ASC, origen_evento ASC, referencia_id ASC. */
export function expectTraceOrder(events: TraceEvent[]): void {
  const normalize = (value: string) => {
    const [seconds, fraction = ''] = value.split('.');
    return `${seconds}.${fraction.padEnd(9, '0')}`;
  };
  const compareString = (a: string, b: string) => a < b ? -1 : a > b ? 1 : 0;
  expect(events).toEqual([...events].sort((a, b) =>
    compareString(normalize(a.fecha_evento), normalize(b.fecha_evento))
    || compareString(a.origen_evento, b.origen_evento) || a.referencia_id - b.referencia_id));
}

/** Read all available domain records and every lot's trace, never a business mutation. */
export async function traceSnapshot(request: APIRequestContext): Promise<TraceSnapshot> {
  const [quality, exports] = await Promise.all([
    qualitySnapshot(request), readList<ExportRecord>(request, '/api/exportaciones'),
  ]);
  const [traces, exportDetails] = await Promise.all([
    Promise.all([...quality.business.lotes].sort((a, b) => a.id - b.id).map(lot => readTrace(request, lot.codigo))),
    Promise.all([...exports].sort((a, b) => a.id - b.id).map(async exp => {
      const [history, checklist, procedures] = await Promise.all([
        readList<ExportHistory>(request, `/api/exportaciones/${exp.id}/historial`),
        readList<unknown>(request, `/api/exportaciones/${exp.id}/checklist`),
        readList<unknown>(request, `/api/exportaciones/${exp.id}/tramites`),
      ]);
      return { id: exp.id, history, checklist, procedures };
    })),
  ]);
  return { ...quality, exports, exportDetails, traces };
}

/** Precondition only: reuse RIT-47/RIT-48 resources; never create missing data here. */
export function reservedTraceRelations(snapshot: TraceSnapshot) {
  const lot = qualityLot(snapshot.business.lotes);
  const orders = snapshot.business.pedidos.filter(order => order.loteId === lot.id);
  expect(orders, 'Precondición: pedido E2E RIT-47 existente').toHaveLength(1);
  const order = orders[0];
  expect(order).toMatchObject({ cantidadKg: 1, estado: 'CONFIRMADO' });
  const details = snapshot.orderDetails.find(d => d.id === order.id)?.details.filter(d => d.loteId === lot.id);
  expect(details, 'Línea del pedido que referencia el lote reservado').toHaveLength(1);
  const inspection = findQualityInspection(snapshot.inspections, lot);
  expect(inspection, 'Precondición: inspección E2E RIT-48 existente').toBeDefined();
  const view = snapshot.traces.find(trace => trace.codigoLote === lot.codigo);
  expect(view).toBeDefined();
  return { lot, order, orderDetail: details![0], inspection: inspection!, view: view! };
}
