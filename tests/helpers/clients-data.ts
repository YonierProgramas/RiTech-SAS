import { expect, type APIRequestContext } from '@playwright/test';
import type { ClienteFormData } from '../pages/ClientesPage';
import { csrfHeaders, jsonBody } from './authorization';

export type ClientRecord = ClienteFormData & {
  id: number;
  activo: boolean;
  creadoEn: string;
  version: number;
};

/** One reserved record for every run. Never generate timestamps/UUIDs for client IDs. */
export const DEDICATED_CLIENT: Readonly<ClienteFormData> = Object.freeze({
  razonSocial: '[E2E RIT-46] Cliente dedicado',
  pais: 'ES',
  identificacionFiscal: 'E2E-RIT46-CLIENTE-UNICO',
  email: 'rit46-clients@ritech.invalid',
  telefono: '0000000000',
  contactoPrincipal: 'Equipo E2E RIT-46',
  direccion: 'Registro exclusivo de pruebas automatizadas RIT-46',
});

export async function readClients(request: APIRequestContext): Promise<ClientRecord[]> {
  const response = await request.get('/api/clientes', { maxRedirects: 0 });
  expect(response.status()).toBe(200);
  const data = await jsonBody(response);
  expect(Array.isArray(data)).toBe(true);
  return data as ClientRecord[];
}

export function isDedicatedClient(client: ClientRecord): boolean {
  return client.identificacionFiscal.toUpperCase() === DEDICATED_CLIENT.identificacionFiscal;
}

export function assertOwnedClient(client: ClientRecord): void {
  expect(Number.isInteger(client.id)).toBe(true);
  expect(client.id).toBeGreaterThan(0);
  const fields = Object.fromEntries(Object.keys(DEDICATED_CLIENT).map(key => [key, client[key as keyof ClienteFormData]]));
  expect(fields, 'Se rehúsa modificar un registro que no coincide con la identidad completa del fixture E2E').toEqual(DEDICATED_CLIENT);
  expect(typeof client.activo).toBe('boolean');
}

export function findOwnedClient(clients: ClientRecord[]): ClientRecord | undefined {
  const matches = clients.filter(isDedicatedClient);
  expect(matches.length, 'Debe existir como máximo un cliente con la identificación reservada E2E').toBeLessThanOrEqual(1);
  const client = matches[0];
  if (client) assertOwnedClient(client);
  return client;
}

export async function dedicatedClientSnapshot(request: APIRequestContext): Promise<ClientRecord> {
  const client = findOwnedClient(await readClients(request));
  expect(client, 'El fixture dedicado debe existir').toBeDefined();
  return client!;
}

export function protectedClientsSnapshot(clients: ClientRecord[]): ClientRecord[] {
  return clients.filter(client => !isDedicatedClient(client)).sort((a, b) => a.id - b.id);
}

/** Cleanup only: no UI action is invented for the frontend's absent deactivate button. */
export async function leaveDedicatedClientInactive(request: APIRequestContext): Promise<ClientRecord | undefined> {
  const client = findOwnedClient(await readClients(request));
  if (!client) return undefined;
  if (client.activo) {
    // Ownership was checked against all fixture fields immediately before this mutation.
    const response = await request.patch(`/api/clientes/${client.id}/desactivar`, {
      maxRedirects: 0, headers: await csrfHeaders(request),
    });
    expect(response.status(), 'La limpieza del único cliente E2E debe completarse').toBe(200);
    const result = await jsonBody(response) as ClientRecord;
    assertOwnedClient(result);
    expect(result.id).toBe(client.id);
    expect(result.activo).toBe(false);
  }
  const final = await dedicatedClientSnapshot(request);
  expect(final.id).toBe(client.id);
  expect(final.activo, 'Estado final conocido: cliente E2E inactivo').toBe(false);
  return final;
}
