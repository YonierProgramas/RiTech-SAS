# RIT-46: Clientes por UI con datos acotados y repetibles

## Contrato real auditado

Fuentes: `clientes()` y `roleSections` de `js/app.js`, `ClienteController`,
`ClienteService`, `ApiDtos.ClienteRequest`, entidad Cliente y DDL existente.
No se modificaron estas fuentes de producción.

- El menú `Clientes internacionales` y su formulario de alta son visibles para
  `GESTOR_COMERCIAL`. La pantalla se titula `Gestión de clientes`.
- La UI presenta siete campos obligatorios, correo de tipo email y país con
  maxlength 2. El formato alfabético ISO-2 se valida en el backend, no en HTML.
- La tabla muestra razón social, país, identificación, correo, contacto y estado.
  Teléfono y dirección pertenecen al formulario, pero no son columnas visibles.
- `GET /api/clientes`: comercial, ventas y auditoría. El menú no se muestra a los
  dos últimos, aunque sus consultas API están permitidas.
- `POST /api/clientes`, `PUT /api/clientes/{id}`,
  `PATCH /api/clientes/{id}/desactivar` y `PATCH /api/clientes/{id}/estado`:
  exclusivamente comercial, con CSRF para escrituras.
- Existen endpoints de actualización/estado, pero no botones ni formularios de
  edición o desactivación en esta UI. No se inventaron Page Objects ni escenarios
  para esas acciones y no hay tests omitidos por esa limitación.

## Nueve escenarios

1. Comercial abre Clientes y dispone de sus controles reales de alta.
2. Columnas, número de filas y datos visibles coinciden con el listado API real.
3. Exportaciones no ve el menú y recibe 403 JSON al consultar directamente la API.
4. Alta por UI del registro reservado si falta, o reutilización si existe;
   fila única y persistencia visible después de recargar la aplicación.
5. Los siete campos obligatorios vacíos bloquean el POST mediante validación nativa.
6. Correo mal formado: foco/validación nativa y ningún POST ni cambio de registro.
7. País no alfabético de dos caracteres: 400, mensaje visible de `pais` y datos intactos.
8. Razón social solo de espacios: 400, mensaje visible de `razonSocial` y datos intactos.
9. Identificación fiscal duplicada, ignorando mayúsculas: 400, mensaje real y registro intacto.

Se conserva `ClientesPage` y se amplía con locators de roles/labels/celdas exactas,
captura del POST, recarga segura, validación nativa y mensajes reales. No se usan
XPath, `nth()`, sleeps, mocks de respuestas ni cambios de producción.

## Único dato persistente permitido

El helper `tests/helpers/clients-data.ts` define una sola identidad reservada:

```text
Identificación fiscal: E2E-RIT46-CLIENTE-UNICO
Razón social: [E2E RIT-46] Cliente dedicado
Correo ficticio: rit46-clients@ritech.invalid
Estado final: INACTIVO
```

Su finalidad exclusiva es probar alta, validación, duplicados y recarga. No debe
utilizarse para pedidos reales. El ID lo asigna la base de datos y se descubre por
API; no se fija en el código. No se generan identificadores con UUID o timestamp.

`dedicatedClient`, fixture opt-in por prueba, funciona así:

1. Hace login real de comercial y abre el módulo mediante el POM.
2. Consulta la identidad reservada y exige como máximo una coincidencia.
3. Si existe, comprueba todos los campos de identidad antes de cualquier cambio.
   Si hay una colisión o datos diferentes, falla sin sobrescribir ese registro.
4. Si falta, crea por el formulario real, verifica HTTP 200, `Cliente registrado`,
   la fila y la persistencia. Las pruebas individuales que usan esta fixture pueden
   ejecutar esta preparación, sin depender del orden de otros escenarios.
5. En `finally`, usa el endpoint de desactivación con el CSRF de la misma sesión
   únicamente si el registro propio está activo. Verifica estado final inactivo.
   Un registro ya inactivo no recibe un PATCH adicional.
6. Compara todos los demás clientes por ID y contenido antes/después: incluidos
   DEMO, no se permite modificar ni añadir registros ajenos a la identidad reservada.
7. Adjunta al HTML report `cliente-e2e-lifecycle` con ID, creación/reutilización,
   estado final y resultado de la protección de los demás clientes.

No hay DELETE en la API: se conserva deliberadamente este único registro inactivo.
Así no aparece entre los clientes activos del formulario de Pedidos. La limpieza
por API es preparación/restauración del fixture; no se presenta como una prueba
de desactivación por UI.

En los negativos se utiliza la misma identificación fiscal ya existente como
barrera adicional: si una validación desapareciera, el control de duplicados y la
unicidad existente impedirían insertar otro cliente. Se verifican además los
mensajes del error específico y la igualdad del registro, incluida su versión.

Los casos de este archivo se ejecutan secuencialmente con modo `default`, sin
dependencia de orden ni sesiones compartidas. El diseño está previsto para una
invocación de la suite a la vez sobre esta base; no incluye bloqueo entre procesos
Playwright independientes. No cambia SQL, esquema ni datos DEMO.

## Ejecución

Con RiTech arrancado manualmente y las credenciales locales ya configuradas:

```powershell
npx.cmd playwright test tests/e2e/clients
npx.cmd playwright test
npm.cmd run test:e2e:report
```

En la primera corrida con fixture ausente se ejecuta el alta real. Las siguientes
verifican la reutilización y persistencia; no se considera necesario ni seguro
forzar otra creación sin un endpoint DELETE. Esta rama se identifica explícitamente
en las anotaciones y el adjunto de ciclo de vida, sin marcar tests como skipped.
