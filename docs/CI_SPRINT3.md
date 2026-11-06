# Sprint 3: GitHub Actions, MySQL y evidencia de pruebas

Workflow: `.github/workflows/playwright.yml`. Un job `sprint3-tests` en
`ubuntu-24.04`, con permisos `contents: read`, para push y pull request a `main`.
No usa `pull_request_target`, no despliega, no hace commit ni push y checkout no
conserva credenciales. Las cuentas y datos de CI son descartables.

## Secuencia

1. Checkout, Java 21 Temurin con caché Maven y Node.js 24 con caché npm.
2. Configurar contraseña de las seis cuentas DEMO, sin mostrarla.
3. MySQL 8.0 como service container, puerto 3306. Su healthcheck de disponibilidad
   se complementa con un `SELECT 1` autenticado, con 30 intentos acotados.
4. Rechazar un esquema preexistente; importar `database/ritech_database.sql` y
   ejecutar `database/RiTech_Verificar_BD.sql`. No se usa MYSQL_DATABASE, porque el
   SQL versionado ya contiene CREATE DATABASE. No hay DROP ni recreación por JPA.
5. `mvn --batch-mode --no-transfer-progress clean verify`: JUnit, integración MySQL,
   JaCoCo y JAR ejecutable. Se usa Maven disponible en la imagen Ubuntu; no el
   launcher Windows interactivo ni la descarga personalizada de `mvnw`.
6. `check-junit.cjs` verifica al menos las 161 pruebas actuales, cero errores,
   fallos u omitidos y ejecución efectiva de ExportacionMySqlRepositoryTest.
7. Regenerar `jacoco:report` si existe jacoco.exec, incluso tras un fallo, y publicar
   la carpeta de cobertura si existe. El reporte contiene código/cobertura, no
   valores de credenciales en ejecución.
8. `npm ci`, nueve pruebas de helpers CI y
   `npx playwright install --with-deps chromium`. No se instalan Firefox/WebKit.
9. Iniciar con nohup el JAR `target/ritech-sas-0.1.0-SNAPSHOT.jar`, con la misma BD.
   Comprobar PID y HTTP 200 en `/login.html`, junto con RiTech y el formulario
   `action="/login"`. Límite 120 segundos, curl con timeouts; sleep solo entre
   intentos. Ante fallo se muestra el log saneado y se falla el step.
10. `npx playwright test`: 56 escenarios actuales, Chromium, worker único,
    sesiones aisladas y login real por UI, sin storageState. Se conserva la
    configuración existente de HTML/screenshot/trace/video y retries CI.
11. `npm run test:api`: Newman local y reporter CLI/htmlextra. Se exige corrida
    completa de 38 requests/148 assertions sin fallos y sesión cerrada verificada.
12. Sanear, inspeccionar y publicar artefactos; detener Spring en `always()`.

El contenedor MySQL lo inicia GitHub antes de ejecutar los steps. La importación
solo ocurre después de configurar Java/Node y autenticar la conexión.

Si falla JUnit no se arranca la aplicación, pero cobertura y diagnóstico se
procesan cuando existan. Si falla Playwright, Newman todavía puede ejecutarse con
Spring disponible. No se usa continue-on-error: el job conserva sus fallos.

## Variables exactas

| Uso | Variables |
| --- | --- |
| Spring/MySQL | `RITECH_DB_URL`, `RITECH_DB_USER`, `RITECH_DB_PASSWORD` |
| Integración JUnit | `RITECH_RUN_MYSQL_INTEGRATION=true`, `RITECH_TEST_DB_URL`, `RITECH_TEST_DB_USER`, `RITECH_TEST_DB_PASSWORD` |
| Bootstrap DEMO | `RITECH_DEMO_PASSWORD`, `RITECH_DEMO_RESET_PASSWORDS=true` solo al arrancar Spring |
| Archivos de la app | `RITECH_DOCUMENTOS_DIR` bajo runner.temp, solo en el step Spring |
| Comercial | `RITECH_E2E_COMERCIAL_USERNAME` |
| Ventas | `RITECH_E2E_VENTAS_USERNAME` |
| Exportaciones | `RITECH_E2E_EXPORTACIONES_USERNAME` |
| Logística | `RITECH_E2E_LOGISTICA_USERNAME` |
| Calidad | `RITECH_E2E_CALIDAD_USERNAME` |
| Auditoría | `RITECH_E2E_AUDITORIA_USERNAME` |
| Contraseña E2E/API | `RITECH_E2E_PASSWORD` |

Las dos URL JDBC apuntan a
`jdbc:mysql://127.0.0.1:3306/ritech_sprint2?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`.
El usuario del motor es root del contenedor. Su contraseña pública efímera está
definida exclusivamente para CI en el workflow; no se toma de la máquina local.
El service usa `MYSQL_ROOT_PASSWORD`, `MYSQL_ROOT_HOST` y `TZ` de la imagen MySQL.

La aplicación continúa con `ddl-auto=validate`. El perfil test no recrea tablas
y conserva su configuración separada; la normalización DEMO se activa únicamente
para el proceso Spring iniciado después de JUnit.

## Secret opcional y DEMO público

Opcional: crear **RITECH_E2E_PASSWORD** en Settings → Secrets and variables →
Actions del repositorio. `demo-env.cjs` prioriza ese secret y configura el mismo
valor para Spring y las pruebas, usando GITHUB_ENV y máscara de GitHub.

Si no existe, incluidas PR de forks donde no se entregan secrets, se extrae el
DEMO deliberadamente público de `static/login.html` y se comprueba que también
está publicado en README.md. No se copia un password privado desde `.env.local`
ni se agrega otro literal de contraseña DEMO al workflow. Si se retira ese DEMO
público, el bootstrap falla y exige el secret.

Los emails DEMO son públicos y se definen en el workflow. No hacen falta secrets
de MySQL, tokens personales GitHub, JWT ni credenciales de servicios externos.
El inicializador existente ajusta las seis cuentas del contenedor a la contraseña
elegida; no se modifica código de seguridad para conseguirlo.

## Newman y artefactos seguros

`run-newman.cjs` carga la colección/environment existentes y cambia username y
password **en memoria** con las variables E2E. No modifica esos JSON, no exporta
environment/cookie jar y deja uploadFile vacío: se prueba el rechazo multipart
ya definido, sin subir archivos. Sigue usando sesión y CSRF; redirects desactivados
permiten comprobar los destinos y la request final confirma logout efectivo.

El reporter htmlextra omite headers, cuerpos, variables y console logs. Antes de
publicar, `sanitize-artifacts.cjs` procesa además los reportes/traces:

- Enmascara passwords conocidos y representaciones JSON/URL/HTML/base64.
- Descubre cookies/tokens CSRF en datos estructurados de las capturas.
- Reescribe ZIP de traces y ZIP embebido en el HTML Playwright, incluidos adjuntos
  JSON base64 y JSONL; conserva screenshots/videos binarios.
- Inspecciona los bytes finales antes de habilitar los outputs de publicación.
  Si detecta un valor sensible restante, falla y no autoriza subir artefactos.
- Publica copias bajo `target/ci-artifacts/`, nunca los traces/reportes crudos.

La página de login muestra únicamente el DEMO público; una contraseña privada
inyectada no se muestra como texto en la UI. Los valores de password del formulario
y los tokens/cookies de las capturas sí se eliminan de los archivos estructurados.

| Artifact | Origen publicado | Condición |
| --- | --- | --- |
| jacoco-report | target/site/jacoco/ | Existe index.html, incluso tras fallos |
| playwright-report | target/ci-artifacts/playwright-report/ | HTML inspeccionado, incluso tras fallos |
| playwright-test-results | target/ci-artifacts/test-results/ | Hay diagnóstico real: traces/screenshots/videos/etc. |
| newman-report | target/ci-artifacts/newman-report/ | HTML generado e inspeccionado, incluso con assertions fallidas |

Retención: 14 días. `.last-run.json` solo no genera un artifact diagnóstico vacío.
Logs, reportes crudos/saneados y fixtures de validación local quedan bajo target,
ya ignorado. `.env.local`, uploads y reportes Playwright también siguen ignorados.

## Dependencias y validación local

DevDependencies añadidas: Newman 6.2.3, newman-reporter-htmlextra 1.23.1 y fflate
0.8.3 para ZIP. Playwright permanece 1.65.0. Override Handlebars 4.7.10: parche
compatible de la misma rama que elimina el aviso crítico del reporter.

`npm ci` todavía informa **20 avisos transitivos de desarrollo: 13 high y 7 moderate**.
No se afirma que desaparecieron. No se aplicó npm audit fix --force ni se degradó
Newman para silenciar avisos. Su actualización requiere revisar compatibilidad
con la colección/runtime; estos paquetes no son dependencias del backend Java.

Comandos locales, sin imprimir contraseñas:

```powershell
npm ci
npm run test:ci:helpers
node --env-file=.env.local scripts/ci/run-newman.cjs
node --env-file=.env.local scripts/ci/sanitize-artifacts.cjs
node scripts/ci/check-junit.cjs
```

El último comando verifica reportes existentes; **no ejecuta Maven**. En CI se
ejecuta después de clean verify. Para los scripts Bash, usar bash -n; no ejecutar
mysql-init.sh contra una BD local existente ni start-app.sh sobre el servidor
local ya levantado.

Validaciones realizadas en esta tarea:

- actionlint 1.7.12, descargado del proyecto oficial y comprobado con SHA-256:
  YAML/expresiones del workflow válidos; Bash -n sin errores.
- npm ci reproducible; nueve pruebas Node de los helpers, todas PASS.
- Newman real local: 38 requests, 148 assertions, cero fallos y logout verificado.
- HTML Newman saneado abierto en Chromium: totales visibles y password ausente.
- Trace real de login: ZIP saneado legible, contraseña/cookies/CSRF capturados
  ausentes de sus entradas. Pruebas adicionales cubren ZIP anidados/base64/JSONL.
- Gate JUnit sobre reportes locales existentes: 161 pruebas, cero failures/errors/
  skipped, integración incluida. No fue una nueva ejecución Maven.
- Playwright 1.65.0 y descubrimiento de 56 escenarios; la última ejecución completa
  del bloque anterior dio 56 PASS. No se atribuye ese resultado a GitHub Actions.

El workflow Linux/MySQL nuevo **todavía no se ejecutó en GitHub Actions**; requiere
que el usuario publique estos cambios. Esta tarea no hace commit, push ni ejecución
remota. La primera corrida remota debe confirmar JUnit, bootstrap en BD fresca,
los 56 escenarios y los artifacts completos.

Versiones de acciones comprobadas en sus releases oficiales:
[checkout](https://github.com/actions/checkout/releases/tag/v7.0.1),
[setup-java](https://github.com/actions/setup-java/releases/tag/v6.2.0),
[setup-node](https://github.com/actions/setup-node/releases/tag/v7.1.0),
[upload-artifact](https://github.com/actions/upload-artifact/releases/tag/v7.0.2).
Referencia del [reporter htmlextra](https://github.com/DannyDainton/newman-reporter-htmlextra).
