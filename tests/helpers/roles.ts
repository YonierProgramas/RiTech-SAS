import type { RiTechModule } from '../pages/AppPage';

type RoleProfile = {
  role: string;
  usernameVariable: string;
  visibleModules: readonly RiTechModule[];
  allowedReads: readonly string[];
  forbiddenReads: readonly string[];
};

/** Audited against roleSections in app.js and @PreAuthorize in the controllers. */
export const ROLE_PROFILES = {
  comercial: {
    role: 'GESTOR_COMERCIAL',
    usernameVariable: 'RITECH_E2E_COMERCIAL_USERNAME',
    visibleModules: ['dashboard', 'clientes'],
    allowedReads: ['/api/clientes'],
    forbiddenReads: ['/api/productos', '/api/pedidos', '/api/exportaciones'],
  },
  ventas: {
    role: 'ADMINISTRADOR_VENTAS',
    usernameVariable: 'RITECH_E2E_VENTAS_USERNAME',
    visibleModules: ['dashboard', 'inventario', 'pedidos'],
    allowedReads: ['/api/clientes', '/api/productos', '/api/lotes', '/api/pedidos'],
    forbiddenReads: ['/api/exportaciones', '/api/calidad', '/api/trazabilidad/DEMO-LOTE-001'],
  },
  exportaciones: {
    role: 'ESPECIALISTA_EXPORTACION',
    usernameVariable: 'RITECH_E2E_EXPORTACIONES_USERNAME',
    visibleModules: ['dashboard', 'exportaciones'],
    allowedReads: ['/api/exportaciones', '/api/pedidos', '/api/exportaciones/requisitos'],
    forbiddenReads: ['/api/clientes', '/api/productos', '/api/calidad'],
  },
  logistica: {
    role: 'COORDINADOR_LOGISTICO',
    usernameVariable: 'RITECH_E2E_LOGISTICA_USERNAME',
    visibleModules: ['dashboard', 'seguimiento'],
    allowedReads: ['/api/exportaciones'],
    forbiddenReads: ['/api/exportaciones/requisitos', '/api/pedidos', '/api/calidad'],
  },
  calidad: {
    role: 'INSPECTOR_CALIDAD',
    usernameVariable: 'RITECH_E2E_CALIDAD_USERNAME',
    visibleModules: ['dashboard', 'calidad'],
    allowedReads: ['/api/lotes', '/api/calidad'],
    forbiddenReads: ['/api/productos', '/api/pedidos', '/api/exportaciones'],
  },
  auditoria: {
    role: 'AUDITOR_PROCESO',
    usernameVariable: 'RITECH_E2E_AUDITORIA_USERNAME',
    visibleModules: ['dashboard', 'trazabilidad'],
    allowedReads: ['/api/clientes', '/api/productos', '/api/lotes', '/api/pedidos', '/api/exportaciones', '/api/calidad'],
    forbiddenReads: [],
  },
} as const satisfies Record<string, RoleProfile>;

export type RiTechRole = keyof typeof ROLE_PROFILES;
export const ROLE_KEYS = Object.keys(ROLE_PROFILES) as RiTechRole[];
