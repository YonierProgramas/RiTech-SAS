# RiTech SAS – Prototipo Sprint 2

Proyecto base de **RiTech SAS – Sistema de Gestión de Exportaciones** para la asignatura Calidad de Software.

## Stack

- Java / Spring Boot 3.5.16
- Spring Web, Spring Security, Spring Data JPA y Bean Validation
- MySQL (`ritech_sprint2`)
- HTML5, CSS3, JavaScript y Bootstrap 5.3.8 (con estilos propios de respaldo)
- Maven (el proyecto incluye un lanzador local, no requiere `mvn` instalado globalmente)

## Requisitos

1. Java 25 instalado (el proyecto compila con compatibilidad Java 21 y puede ejecutarse con JDK 25).
2. MySQL 8 en ejecución.
3. Base `ritech_sprint2` creada con el script entregado y ya verificada.
4. Internet la primera vez para descargar Maven y dependencias.

## Ejecutar en Windows

1. Descomprime el proyecto.
2. Abre la carpeta `ritech-sas-starter` en VS Code.
3. Ejecuta `run-windows.cmd` desde el Explorador de Windows, o desde la terminal:

```bat
run-windows.cmd
```

4. Cuando lo solicite, escribe la contraseña de MySQL del usuario `root`.
5. Espera hasta ver que Spring Boot inició en el puerto `8080`.
6. Abre en el navegador:

```text
http://localhost:8080
```

> No vuelvas a ejecutar `database/ritech_database.sql` si la base `ritech_sprint2` ya existe.

## Cuentas DEMO

Contraseña común del prototipo:

```text
Ritech2026*
```

| Correo                         | Contraseña demo | Rol                      |
| ------------------------------ | --------------- | ------------------------ |
| `comercial@ritech.example`     | `Ritech2026*`   | GESTOR_COMERCIAL         |
| `ventas@ritech.example`        | `Ritech2026*`   | ADMINISTRADOR_VENTAS     |
| `exportaciones@ritech.example` | `Ritech2026*`   | ESPECIALISTA_EXPORTACION |
| `logistica@ritech.example`     | `Ritech2026*`   | COORDINADOR_LOGISTICO    |
| `calidad@ritech.example`       | `Ritech2026*`   | INSPECTOR_CALIDAD        |
| `auditoria@ritech.example`     | `Ritech2026*`   | AUDITOR_PROCESO          |

Al iniciar la aplicación, las contraseñas de estas seis cuentas DEMO se normalizan a la contraseña anterior usando BCrypt.

## Smoke test

Con la aplicación levantada y la base disponible, ejecuta:

```powershell
.\scripts\smoke-test.ps1
```

También acepta una URL y contraseña distintas:

```powershell
.\scripts\smoke-test.ps1 -BaseUrl http://localhost:8080 -Password 'Ritech2026*'
```

El script solo realiza autenticación y consultas GET sobre dashboard, clientes, productos, lotes, pedidos, exportaciones, calidad y trazabilidad.

## Módulos incluidos en la base del prototipo

- Autenticación y autorización por rol.
- Dashboard operativo.
- Clientes internacionales.
- Productos y lotes.
- Pedidos con validación de disponibilidad.
- Expedientes de exportación y consulta de checklist documental.
- Seguimiento e historial de estados.
- Inspecciones de calidad.
- Consulta de trazabilidad por lote.

## Reglas importantes

- Hibernate usa `ddl-auto=validate`: **el programa no crea ni modifica las tablas**.
- La base MySQL ya creada es la fuente de verdad.
- Los datos `DEMO-*` son ficticios y solo sirven para la presentación académica.
- El prototipo no reemplaza ni se conecta oficialmente con DIAN, VUCE u otras autoridades.

## Estructura principal

```text
src/main/java/co/edu/ucc/ritech/
├─ config/       Seguridad y configuración DEMO
├─ domain/       Entidades JPA
├─ repository/   Acceso a datos
├─ service/      Reglas de negocio
└─ web/          API REST y DTOs

src/main/resources/static/
├─ login.html
├─ index.html
├─ css/app.css
└─ js/app.js
```

## Si la aplicación no inicia

- Confirma que MySQL80 esté en ejecución.
- Confirma que existe `ritech_sprint2`.
- Verifica la contraseña introducida para `root`.
- Copia el error completo de la terminal; no vuelvas a crear la base.

## Pruebas unitarias incluidas

La suite incluye siete pruebas JUnit/Mockito enfocadas en reglas críticas:

- cliente con identificación fiscal duplicada;
- lote con código duplicado;
- pedido con cantidad superior a la disponibilidad;
- creación correcta de pedido y reserva de lote;
- inspección rechazada cuando el lote no existe;
- trazabilidad con eventos;
- trazabilidad sin eventos.

Después de la primera descarga de dependencias se ejecutan con:

```bat
mvnw.cmd test
```
