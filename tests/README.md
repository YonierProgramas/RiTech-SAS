# RiTech SAS: Page Objects y E2E (RIT-44 a RIT-48 y Trazabilidad)

La aplicación debe estar levantada manualmente en `http://localhost:8080`.
Los tres tests públicos de smoke/POM no necesitan credenciales. Los escenarios de
autenticación usan las variables locales descritas abajo y envían login/logout
reales. Las pruebas de permisos no crean ni modifican registros de negocio.
RIT-46 añade nueve escenarios de Clientes con un único registro E2E reservado,
creado por UI solo si falta y dejado inactivo al terminar. Su contrato de datos
está documentado en `e2e/clients/README.md`.
RIT-47 añade doce escenarios de Pedidos, con un cliente/producto/lote propios y
un único pedido confirmado que reserva 1 kg ficticio una sola vez. El contrato y
los límites están documentados en `e2e/orders/README.md`.
RIT-48 añade diez escenarios de Calidad y una inspección única sobre el lote
reservado RIT-47, sin cambios de stock. Su contrato está en `e2e/quality/README.md`.
Trazabilidad añade nueve escenarios de lectura sobre esos recursos y un lote DEMO,
con comparaciones UI/API y snapshots; véase `e2e/traceability/README.md`.

## Estructura

```text
tests/
  pages/       AppPage, LoginPage, DashboardPage, ClientesPage,
               PedidosPage, CalidadPage, TrazabilidadPage
  fixtures/    ritech.fixture.ts, clients.fixture.ts, orders.fixture.ts, quality.fixture.ts
               traceability.fixture.ts
  helpers/     credentials.ts
               env.ts, roles.ts, authorization.ts, clients-data.ts,
               ui-session.ts, orders-data.ts, timestamps.ts, quality-data.ts
               traceability-data.ts
  e2e/auth/    login.spec.ts, permissions.spec.ts, logout.spec.ts
  e2e/clients/ clients.spec.ts
  e2e/orders/  orders.spec.ts
  e2e/quality/ quality.spec.ts
  e2e/traceability/ traceability.spec.ts
  smoke/       ritech-smoke.spec.ts
  pom/         login-page.spec.ts
```

Las fixtures crean Page Objects por prueba, usando el mismo `page` aislado de
Playwright. Los constructores solo definen locators; no navegan, autentican ni
modifican datos. Los tests importan `test` y `expect` desde `fixtures/ritech.fixture`.
La fixture `signInAs(role)` hace login por la UI solo cuando se invoca; valida el
redirect, dashboard, usuario/rol y `/api/session` usando las cookies del navegador.

## Contrato auditado del frontend

Se revisaron `src/main/resources/static/login.html`, `index.html` y `js/app.js`.
La aplicación autenticada es una SPA en `/`: los módulos se abren con botones del
menú y no tienen rutas propias como `/clientes` o `/pedidos`.

| Objeto | Elementos y operaciones reales |
| --- | --- |
| LoginPage | `/login.html`, correo, contraseña, Ingresar y mensaje de error; `goto`, `openHome`, `login`, `submit`, `expectVisible`, `expectHidden`, `expectRequiredField`, `expectError`. |
| DashboardPage | Shell autenticado, usuario/rol, indicadores, sesión activa y tabla por estado; `goto`, `open`, `expectLoaded`, `openModule`. |
| ClientesPage | Alta, tabla y validación; `open`, `fillForm`, `saveAndWaitForResponse`, `reloadList`, `expectClientRow`, validaciones nativas y mensajes de éxito/error. |
| PedidosPage | Cliente, país, lote, cantidad y observaciones; `open`, `fillForm`, `createAndWaitForResponse`, `expectOptions`, validaciones nativas, mensajes, `reloadList`, `expectOrderSummary`. |
| CalidadPage | Lote, humedad, taza, aroma, resultado, defectos y observaciones; `open`, `fillForm`, `registerAndWaitForResponse`, `expectOptions`, rango/mensajes, `reloadList`, `expectInspectionRows`, `rowsForLot`. |
| TrazabilidadPage | Búsqueda real por código, estado inicial/vacío/error y tabla de eventos; `open`, `searchAndWaitForResponse`, `expectTrace`, `expectEmptySearchBlocked`, `expectError`, `expectEventDescription`. |
| AppPage | Navegación compartida y notificación transitoria; `expectAuthenticated`, `openModule`, `expectModuleLoaded`, `expectNavigation`, `expectMessage`, `logout`. |

Los módulos también tienen `expectLoaded`. Los métodos que guardan datos solo
actúan cuando una prueba los invoca explícitamente. Clientes usa el alta por UI
solo para su fixture dedicado RIT-46; no crea un cliente diferente en cada corrida.
Después de `login`, la prueba debe usar `dashboardPage.expectLoaded()` para
comprobar el ingreso, o `loginPage.expectError()` para comprobar el rechazo.

Limitaciones reales del frontend actual:

- Clientes muestra alta y listado, pero no botones de edición, desactivación ni
  cambio de estado. No se inventaron acciones POM para endpoints sin controles UI.
- Pedidos muestra el resumen en una fila; no ofrece botón/modal de detalle ni
  formulario de pedido múltiple. `summaryByCode` representa solo ese resumen.
- Calidad no tiene campos required, ni vista/filtro de historial, edición o borrado.
  La obligatoriedad se comprueba por API; el historial API se compara con las filas
  correspondientes de la tabla global.
- El menú depende del rol. `openModule` espera un botón visible; no elude los
  permisos ni intenta abrir rutas que el frontend no ofrece.

## Locators

Se priorizan `getByRole`, `getByLabel` y `getByText`. Las filas se identifican por
celdas exactas (identificación fiscal o código), no por posiciones.
Trazabilidad usa el único textbox del formulario existente, porque no tiene label.
Se usan IDs ya existentes para formularios sin nombre accesible, el título
de módulo (`#page-title`), usuario/rol, `#toast`, `#login-message` y `#trace-result`.
No se añadieron test IDs ni cambios de producción. No se utiliza XPath ni `nth()`.

## Credenciales locales

El archivo privado `.env.local` en la raíz del proyecto contiene estas variables;
sus valores no se documentan en archivos de pruebas ni en este README:

```text
RITECH_E2E_COMERCIAL_USERNAME
RITECH_E2E_VENTAS_USERNAME
RITECH_E2E_EXPORTACIONES_USERNAME
RITECH_E2E_LOGISTICA_USERNAME
RITECH_E2E_CALIDAD_USERNAME
RITECH_E2E_AUDITORIA_USERNAME
RITECH_E2E_PASSWORD
```

`playwright.config.ts` invoca `loadLocalE2EEnvironment()`, que lee `.env.local` con
la API nativa de Node `loadEnvFile`. Las variables ya inyectadas en el entorno
tienen precedencia; no hay dependencia adicional de dotenv ni valores por defecto.
El archivo está excluido en `.gitignore`; no se debe agregar ni commitear.
En un entorno sin archivo privado se deben inyectar las mismas variables.

`getRiTechCredentials(role)` usa la variable de usuario del perfil y la contraseña
compartida. Su rol predeterminado es `exportaciones`. Si falta una variable, el
error indica su nombre, sin imprimir valores; no se omite la prueba para ocultarlo.
Los roles aceptados son `comercial`, `ventas`, `exportaciones`, `logistica`,
`calidad` y `auditoria`.

Composición de un login por la UI:

```ts
const credentials = getRiTechCredentials('exportaciones');
await loginPage.goto();
await loginPage.login(credentials.username, credentials.password);
await dashboardPage.expectLoaded();
```

## Reutilización de sesión evaluada

RiTech usa sesión de servidor y cookies, no JWT. Más adelante `storageState` puede
guardar `JSESSIONID` y las cookies CSRF, con un archivo por rol bajo
`playwright/.auth/`, que ya está ignorado por Git. El estado necesita una sesión
todavía válida; un logout o reinicio del servidor puede invalidarlo.

En estos bloques no se genera ni se carga `storageState`. Cada escenario autenticado
realiza login por UI en su contexto aislado; esto mantiene visible la validación
de autenticación y permite comprobar logout sin invalidar estados compartidos.

## Ejecución y alcance verificado

```powershell
npx.cmd playwright test tests/e2e/auth
npx.cmd playwright test tests/e2e/clients
npx.cmd playwright test tests/e2e/orders
npx.cmd playwright test tests/e2e/quality
npx.cmd playwright test tests/e2e/traceability
npx.cmd playwright test
npm.cmd run test:e2e:report
```

Se ejecutan el smoke refactorizado, la edición del correo mediante `LoginPage` sin
enviar el formulario y la presentación de `/login.html?error`. Este último solo
valida la vista de error, no un intento real de autenticación fallido.
Además se ejecutan 13 escenarios de autenticación y autorización real. La matriz,
la cobertura concreta y las precauciones de los probes están descritas en
`e2e/auth/README.md`. El reporte HTML se genera en `playwright-report/index.html`.
Los nueve escenarios adicionales de Clientes se describen en `e2e/clients/README.md`.
La edición y desactivación por UI no están disponibles en el frontend actual;
los flujos de los demás módulos quedan para tareas posteriores.
Los doce escenarios de Pedidos validan creación simple, reutilización, permisos,
validaciones y stock propio. Detalle y negativos con IDs inexistentes usan API
como complemento donde la UI no ofrece esos controles. No se inventa creación
múltiple ni cancelación. La suite usa un worker para proteger las comparaciones
de datos reales entre archivos; ejecutar un solo proceso contra esta base a la vez.
Los diez escenarios de Calidad usan la inspección reservada RIT-48 y el lote
RIT-47 ya preparado. Un auditor aislado de solo lectura verifica datos de negocio,
líneas de pedidos e inspecciones DEMO antes/después. El inspector conserva sus
permisos reales; no se le concede acceso extra para facilitar las comprobaciones.
Los nueve escenarios de Trazabilidad reutilizan lote/pedido/inspección existentes.
Comprueban eventos, relaciones, orden SQL, errores y permisos sin escribir datos.
El historial de todos los lotes y los registros de negocio se comparan exactamente
antes/después. No se inventan filtros, edición ni eventos de prueba.
