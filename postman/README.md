# RiTech SAS — colección Postman

## Importar y configurar
1. Importa RiTech_SAS_API_Tests.postman_collection.json y RiTech_Local.postman_environment.json.
2. Selecciona RiTech_Local. baseUrl apunta a http://localhost:8080; username contiene el usuario demo de exportaciones. Ingresa la contraseña en la variable password dentro de Postman; no está guardada aquí.
3. IDs DEMO se inicializan en 1 y loteCodigo en DEMO-LOTE-001 según el SQL de inicio. Ajusta si tu BD difiere.

## Login, sesión y CSRF
1. Arranca la aplicación y ejecuta Authentication / Session → Iniciar sesión. El POST /login usa application/x-www-form-urlencoded con email/password, mecanismo form login real.
2. Postman mantiene JSESSIONID automáticamente en el cookie jar; no hay JWT. Ejecuta Sesión actual para verificar usuario/rol y Obtener CSRF para obtener el token.
3. El pre-request de colección obtiene CSRF desde /api/csrf si falta, guarda csrfToken/csrfHeaderName/csrfParameterName y añade el header a cada escritura. Tras login se limpia el token guardado para renovarlo bajo la sesión autenticada. CSRF sigue activo para todo excepto la excepción existente /login; logout también lleva CSRF.
4. Cerrar sesión está al final para que no invalide la sesión antes de las otras solicitudes.

Multipart usa form-data con el campo file. Selecciona un archivo .txt en Postman y no establezcas manualmente Content-Type: Postman añade el boundary. La cookie se mantiene con el jar de Postman.

## Escrituras seguras
Las plantillas de escritura usan DTOs inválidos o IDs inexistentes por defecto para no mutar datos DEMO. Un usuario autorizado normalmente recibirá 400 por validación/no encontrado; los roles no autorizados reciben 403. Para probar éxito, usa datos descartables. Pedidos reservan inventario; actualizar estados/documentos modifica registros y subir un archivo crea una versión documental.

## Inventario auditado de endpoints
- **Authentication / Session:** POST /login (email,password urlencoded); POST /logout; GET /api/session; GET /api/csrf.
- **Dashboard:** GET /api/dashboard; GET /api/dashboard/operativo.
- **Clients:** GET /api/clientes; POST /api/clientes; PUT /api/clientes/{id}; PATCH /api/clientes/{id}/desactivar; PATCH /api/clientes/{id}/estado.
- **Products:** GET /api/productos; POST /api/productos; PUT /api/productos/{id}; PATCH /api/productos/{id}/estado.
- **Lots:** GET /api/lotes; POST /api/lotes; PUT /api/lotes/{id}.
- **Orders:** GET /api/pedidos; POST /api/pedidos; POST /api/pedidos/multiple; GET /api/pedidos/{id}.
- **Exports:** GET /api/exportaciones; POST /api/exportaciones; GET /api/exportaciones/requisitos; GET /api/exportaciones/{id}; GET /api/exportaciones/{id}/checklist; GET /api/exportaciones/{id}/tramites; POST /api/exportaciones/{id}/tramites; GET /api/exportaciones/{id}/historial; POST /api/exportaciones/{id}/estado.
- **Documentation:** POST /api/exportaciones/{id}/requisitos/{requisitoId}/documentos (multipart file); PATCH /api/exportaciones/{id}/documentos/{documentoId}/estado.
- **Quality:** GET /api/calidad; POST /api/calidad; GET /api/calidad/lotes/{loteId}.
- **Traceability:** GET /api/trazabilidad/{codigoLote}.

No existen rutas DELETE en los controladores. Los endpoints protegidos por rol aceptan 403 como respuesta esperada cuando se usa una cuenta sin permiso. Cada solicitud tiene test de estado; a nivel colección se revisa respuesta menor a 5 s y JSON parseable cuando aplica. Respuestas JSON exitosas prueban Content-Type y campos esenciales; los creates capturan IDs si se editan para tener éxito.
