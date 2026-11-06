# RIT-45: contrato real de autenticación y autorización

Fuentes auditadas: `SecurityConfig.java`, anotaciones `@PreAuthorize` de los
controladores, `DemoPasswordInitializer.java` y `roleSections` de `js/app.js`.
Los usuarios locales se seleccionan por las variables de entorno de `roles.ts`;
no se incluyen valores de credenciales en este documento.

Todos los roles autenticados pueden leer `/api/session`, `/api/csrf`,
`/api/dashboard` y `/api/dashboard/operativo`. Login, CSS, JavaScript y `/error`
son públicos; las demás rutas requieren sesión. El login usa formulario
`email/password`, éxito en `/`, rechazo en `/login.html?error`. Logout requiere
CSRF, invalida la sesión y redirige a `/login.html?logout`.

## Matriz auditada

Dashboard está visible y permitido para los seis roles.

| Rol | Otros módulos visibles | Lecturas API permitidas además de dashboard | Escrituras propias | Restricciones importantes |
| --- | --- | --- | --- | --- |
| GESTOR_COMERCIAL | Clientes internacionales | Clientes | Alta, actualización, desactivación y estado de cliente | Productos/lotes, pedidos, exportaciones, calidad y trazabilidad |
| ADMINISTRADOR_VENTAS | Productos y lotes; Pedidos de exportación | Clientes, productos, lotes y pedidos/detalle | Productos, lotes y pedidos simples/múltiples | Exportaciones, calidad, trazabilidad y escritura de clientes |
| ESPECIALISTA_EXPORTACION | Expediente y documentación | Pedidos/detalle; exportaciones/detalle; catálogo, checklist y trámites documentales | Expediente, documentos/revisión y trámites | Clientes, inventario, calidad, trazabilidad; historial/cambio de estado logístico |
| COORDINADOR_LOGISTICO | Seguimiento de exportación | Exportaciones/detalle e historial | Estado de exportación | Pedidos, clientes, inventario, calidad, trazabilidad y operaciones documentales |
| INSPECTOR_CALIDAD | Inspecciones de calidad | Lotes; inspecciones e historial por lote | Registrar inspección | Productos, clientes, pedidos, exportaciones y trazabilidad |
| AUDITOR_PROCESO | Trazabilidad de lotes | Todas las consultas de los módulos anteriores y trazabilidad | Ninguna escritura de negocio | Mutaciones de clientes, inventario, pedidos, exportaciones, documentos y calidad |

La visibilidad del menú no equivale a autorización. Ventas puede leer clientes sin
tener ese módulo en su menú; exportaciones puede leer pedidos; auditoría tiene
lecturas amplias aunque solo muestra trazabilidad. Las excepciones documentales
se distinguen del listado/detalle general de exportación.

## Escenarios implementados

- `login.spec.ts`: login válido con redirect, dashboard e identidad real;
  contraseña incorrecta; usuario inexistente; email obligatorio vacío; contraseña
  obligatoria vacía; acceso anónimo a `/api/session`. Son seis escenarios.
- `permissions.spec.ts`: seis perfiles, cada uno con login real por UI. Se valida
  el menú exacto, ausencia de módulos ocultos, carga de módulos visibles y lecturas
  API permitidas/restringidas con la misma cookie jar del navegador. Auditoría
  consulta también la trazabilidad del código que ofrece el formulario real.
- `logout.spec.ts`: login UI, logout mediante su formulario CSRF real, retorno al
  login, rechazo de `/api/session` y acceso posterior a `/api/dashboard`.

Son 13 escenarios nuevos, además de los tres smoke/POM anteriores. Los casos de
campos requeridos comprueban validez nativa, foco del campo y ausencia de POST;
el resto de los rechazos de login valida el mensaje visible después del POST real.

## Autorización directa sin alterar datos

`context.request` comparte las cookies del contexto autenticado del navegador.
Las consultas directas no siguen redirects: un 200 HTML de login nunca se acepta
como una API JSON autenticada. Los 403 deben ser JSON con el error de permisos
real del backend, no una respuesta ambigua de CSRF.

Para autorización de carga documental se obtiene un token válido de `/api/csrf`
y se envía una parte multipart `file` presente pero vacía a la ruta real
`/api/exportaciones/0/requisitos/0/documentos`. El especialista debe obtener 400
con `Debe seleccionar un archivo`; los otros cinco roles deben obtener 403.
La primera guarda de `CargaDocumentoService.cargar` rechaza el archivo vacío antes
de consultar requisitos, asignar versiones o escribir archivos. Esto evita cambios
de negocio incluso si una regresión de permisos dejara pasar a otro rol.

No se usan CSRF desactivado, JWT, cookies manuales ni mocks de respuestas. No se
crean registros, no se cambian credenciales y no se modifican datos DEMO.

## Sesión, artefactos y límites

`signInAs` autentica por UI y valida el JSON de `/api/session` contra el perfil.
No se usa `storageState`: las sesiones son independientes y el logout no puede
invalidar una sesión compartida por otras pruebas. `playwright/.auth/` continúa
ignorado para una posible fase posterior.

Se conservan las opciones de screenshot, trace y video ante fallos y el reporte
HTML de RIT-43. No se automatiza el arranque del servidor ni se modifica GitHub
Actions en esta tarea. RiTech debe estar levantado en `http://localhost:8080`.
