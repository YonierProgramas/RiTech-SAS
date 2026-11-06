# RIT-48: Calidad con una inspección E2E reutilizable

## Contrato real auditado

Fuentes: `calidad()`/`roleSections` en `static/js/app.js`, `CalidadController`,
`CalidadService`, `InspeccionRequest/View`, repositorio, entidad y DDL existente.
Estas fuentes de producción no se modifican.

- `INSPECTOR_CALIDAD` ve el menú `Inspecciones de calidad` y puede registrar.
- La pantalla muestra formulario y listado global con Lote, Inspector, Fecha,
  Humedad, Resultado y Observaciones.
- Campos reales: lote, humedad %, evaluación de taza, aroma, resultado, defectos
  y observaciones. Se usan roles/labels y los IDs del formulario/toast ya existentes.
- El select incluye todos los lotes devueltos por API, con código y producto.
  No filtra por estado ni disponibilidad. Resultado ofrece PENDIENTE/APROBADO/RECHAZADO.
- **No hay atributos `required` en el formulario**. No se inventan validaciones
  nativas de obligatoriedad. El DTO exige lote no nulo y resultado no vacío/válido.
  Como la UI usa selects, los negativos con IDs arbitrarios/resultado inválido
  comprueban la API desde la misma sesión autenticada.
- Humedad es opcional: HTML min 0, max 100, step 0.01 y backend DecimalMin/Max.
  Taza/aroma son opcionales, con máximo 150 en backend; HTML no limita su longitud.
  Defectos/observaciones no tienen regla de rechazo declarada en el DTO.
- `GET /api/calidad` y `GET /api/calidad/lotes/{loteId}` permiten calidad y auditoría.
  `POST /api/calidad` permite solo calidad, con CSRF activo.
- Crear devuelve HTTP 200; éxito visible: `Inspección registrada`. Errores de
  validación/negocio: 400 JSON y toast de error cuando el envío se hace por UI.
- Ambos métodos del repositorio ordenan por fecha DESC. Se comprueba ese orden,
  permitiendo empates; no se exige un desempate por ID que el backend no garantiza.
- No hay controles de historial filtrado, detalle, edición, borrado ni cambio de
  resultado. La API tampoco ofrece actualizar/eliminar inspecciones.

## Diez escenarios

1. Calidad abre el módulo y los siete controles; contrato HTML real sin required.
2. Seis columnas, filas/celdas y fechas en orden coinciden con el listado API.
3. Opciones de lotes/IDs/producto y únicamente los tres resultados válidos.
4. Alta única por UI si falta, o reutilización; recarga, fila única y stock intacto.
5. Comercial no ve el menú y recibe 403 JSON en listado, historial y POST. El POST
   usa CSRF válido e ID inexistente como barrera adicional contra inserciones.
6. Lote nulo y resultado nulo/vacío/desconocido: 400 del campo exacto, sin persistir.
7. Lote inexistente: ausente del selector, POST e historial rechazados con 400 real.
8. Humedad -0.01/100.01: foco y validación nativa, ningún POST; backend también
   rechaza ambos extremos con 400 del campo humedad, sin guardar.
9. Taza/aroma de 151 caracteres: envío real por UI, 400 y toast específico del campo.
10. Historial por lote contiene la inspección reservada y coincide con la API general
    filtrada y las filas visibles de ese lote en la tabla global.

El caso 10 no pretende que exista un botón/filtro de historial. La selección de
filas se hace mediante locators de celdas exactas sobre la tabla existente.
Los casos 6/7 complementan la UI por API donde los selects no permiten introducir
valores inexistentes. No se inyectan opciones ni respuestas ficticias.

## Dato único y estado conocido

Se reutiliza **E2E-RIT47-LOTE-UNICO**, del producto E2E-RIT47-PRODUCTO.
Precondición explícita: RIT-47 debe haber preparado su pedido/lote. Si falta o su
identidad/stock no coincide, se falla sin saltar tests ni crear otro lote. Para
prepararlo en una base nueva, ejecutar primero `tests/e2e/orders`.

La inspección tiene:

```text
Observaciones: [E2E RIT-48] Inspección única reutilizable; no modificar
Resultado final: APROBADO
Humedad ficticia: 12.34
Taza: [E2E RIT-48] Taza de prueba
Aroma: [E2E RIT-48] Aroma de prueba
Defectos: [E2E RIT-48] Sin defectos
```

El ID/fecha los asigna el backend y se descubren por API. El inspector se comprueba
contra la sesión real, sin hardcodear credenciales ni nombre. Antes del alta se
buscan inspecciones del lote o con marcador RIT-48. Se acepta como máximo una y
debe coincidir con todos los campos reservados. Una colisión/dato cambiado produce
un fallo sin sobrescribir ni añadir otra inspección.

Si falta, se registra mediante UI y se comprueban HTTP 200, toast de éxito, fila,
persistencia, campos e inspector. El helper temporal compartido admite únicamente
la diferencia de hasta 1 microsegundo entre Java LocalDateTime y MySQL DATETIME(6).
La inspección se conserva porque no hay DELETE; siguientes corridas la reutilizan.
No se utilizan timestamps/UUID para producir registros nuevos en cada ejecución.

Registrar calidad no altera lotes ni stock en el servicio auditado. La verificación
comprueba que el lote permanece ACTIVO, inicial 1 kg, reservado 1 kg, disponible
0 kg y despachado 0 kg, sin modificar el pedido RIT-47. No se crean clientes,
productos, lotes, pedidos, exportaciones ni despachos en RIT-48.

## Integridad, sesiones y evidencias

Cada escenario dispone de contexto aislado y login real por UI, sin storageState.
El fixture automático abre además un contexto de AUDITOR_PROCESO de solo lectura:
el inspector no tiene permiso para consultar clientes/productos/pedidos.
Antes/después se comparan:

- Todos los clientes, productos, lotes, pedidos y sus líneas, incluidos E2E previos.
- Todas las inspecciones preexistentes, incluidas DEMO, por ID y contenido completo.
- Como máximo una nueva inspección, exclusivamente la identidad RIT-48 permitida.
- Estado final y campos de la inspección propia; los negativos comparan además el
  snapshot completo para impedir cualquier persistencia accidental.

Los negativos directos de validación usan lote 0 cuando es posible: si una regla
desaparece, la inexistencia del lote sigue evitando inserciones. En los envíos UI
no se altera el selector; se verifican la regla/mensaje exactos y la integridad.

Adjuntos del reporte: `calidad-integridad-protegida` e `inspeccion-e2e-lifecycle`.
Se mantiene el worker único existente. Ejecutar un solo proceso contra esta base
a la vez; no se implementa bloqueo entre procesos independientes.

## Ejecución

Con RiTech arrancado manualmente y `.env.local` privado ya configurado:

```powershell
npx.cmd playwright test tests/e2e/quality
npx.cmd playwright test
npm.cmd run test:e2e:report
```

Se mantienen HTML report en `playwright-report/index.html` y screenshot/trace/video
ante fallos. No se modifica backend, seguridad, esquema MySQL ni Postman/Newman.
