import { expect, type APIRequestContext, type APIResponse } from '@playwright/test';

type JsonResponse = Pick<APIResponse, 'headers' | 'url' | 'json'>;

export async function jsonBody(response: JsonResponse, detail = response.url()): Promise<unknown> {
  expect(response.headers()['content-type'] ?? '', `${detail}: Content-Type JSON`).toContain('application/json');
  return response.json();
}

export async function expectForbidden(response: APIResponse, detail: string): Promise<void> {
  expect(response.status(), `${detail}: autorización backend`).toBe(403);
  expect(await jsonBody(response, detail)).toMatchObject({
    status: 403, message: 'No tienes permisos para esta operación',
  });
}

export function expectUnauthenticatedRedirect(response: APIResponse): void {
  expect(response.status()).toBe(302);
  const location = response.headers()['location'];
  expect(location, 'Location del login requerido').toBeTruthy();
  const target = new URL(location, response.url());
  expect(target.origin).toBe(new URL(response.url()).origin);
  expect(target.pathname).toBe('/login.html');
  expect(target.searchParams.has('error')).toBe(false);
}

export async function csrfHeaders(request: APIRequestContext): Promise<Record<string, string>> {
  const response = await request.get('/api/csrf', { maxRedirects: 0 });
  expect(response.status()).toBe(200);
  const csrf = await jsonBody(response) as { headerName: string; token: string };
  expect(csrf.headerName).toEqual(expect.any(String));
  expect(csrf.token).toEqual(expect.any(String));
  expect(csrf.headerName.length).toBeGreaterThan(0);
  expect(csrf.token.length).toBeGreaterThan(0);
  return { [csrf.headerName]: csrf.token };
}

/** Empty multipart file reaches the service guard before any database/file mutation. */
export async function probeDocumentUploadPermission(request: APIRequestContext): Promise<APIResponse> {
  return request.post('/api/exportaciones/0/requisitos/0/documentos', {
    maxRedirects: 0,
    headers: await csrfHeaders(request),
    multipart: { file: { name: 'empty.txt', mimeType: 'text/plain', buffer: Buffer.alloc(0) } },
  });
}
