-- RiTech SAS | Sprint 2 | Modelo de base de datos v1.0
-- Objetivo: MySQL 8.0.43. Requiere MySQL 8.0.16 o posterior por CHECK.
-- EJECUTAR UNA VEZ como usuario con permisos de creación de esquema y triggers.
-- No elimina bases ni modifica Universidad. Si el esquema ya existe, DETENER.
-- Registros DEMO, no evidencia de implementación previa ni de autorizaciones oficiales.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET SESSION time_zone = '+00:00';
SET @ritech_old_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION,ERROR_FOR_DIVISION_BY_ZERO,ONLY_FULL_GROUP_BY';
CREATE DATABASE ritech_sprint2 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE ritech_sprint2;

-- Catálogo de los seis roles funcionales de RiTech.
CREATE TABLE `roles` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `codigo` VARCHAR(40) NOT NULL,
  `nombre` VARCHAR(80) NOT NULL,
  `descripcion` VARCHAR(255) NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT uq_roles_1 UNIQUE (`codigo`)
) ENGINE=InnoDB;

-- Cuentas de acceso de la aplicación; no son cuentas del motor MySQL.
CREATE TABLE `usuarios` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `rol_id` BIGINT NOT NULL,
  `nombre` VARCHAR(120) NOT NULL,
  `email` VARCHAR(150) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `activo` BOOLEAN NOT NULL DEFAULT TRUE,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `version` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  CONSTRAINT uq_usuarios_1 UNIQUE (`email`),
  CONSTRAINT fk_usuarios_1 FOREIGN KEY (`rol_id`) REFERENCES `roles` (`id`),
  CONSTRAINT ck_usuarios_1 CHECK (activo IN (0,1)),
  CONSTRAINT ck_usuarios_2 CHECK (version >= 0)
) ENGINE=InnoDB;

-- Compradores internacionales y su información de contacto.
CREATE TABLE `clientes` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `razon_social` VARCHAR(150) NOT NULL,
  `pais` CHAR(2) NOT NULL,
  `identificacion_fiscal` VARCHAR(50) NOT NULL,
  `email` VARCHAR(150) NOT NULL,
  `telefono` VARCHAR(30) NOT NULL,
  `direccion` VARCHAR(255) NOT NULL,
  `contacto_principal` VARCHAR(120) NOT NULL,
  `activo` BOOLEAN NOT NULL DEFAULT TRUE,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `version` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  CONSTRAINT uq_clientes_1 UNIQUE (`identificacion_fiscal`),
  CONSTRAINT ck_clientes_1 CHECK (activo IN (0,1)),
  CONSTRAINT ck_clientes_2 CHECK (version >= 0),
  INDEX ix_clientes_1 (`razon_social`)
) ENGINE=InnoDB;

-- Catálogo de café y cacao utilizados por los lotes.
CREATE TABLE `productos` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `codigo` VARCHAR(30) NOT NULL,
  `nombre` VARCHAR(120) NOT NULL,
  `tipo` VARCHAR(10) NOT NULL,
  `descripcion` VARCHAR(255) NULL,
  `unidad_medida` CHAR(2) NOT NULL DEFAULT 'KG',
  `activo` BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (`id`),
  CONSTRAINT uq_productos_1 UNIQUE (`codigo`),
  CONSTRAINT ck_productos_1 CHECK (tipo IN ('CAFE','CACAO')),
  CONSTRAINT ck_productos_2 CHECK (unidad_medida = 'KG'),
  CONSTRAINT ck_productos_3 CHECK (activo IN (0,1))
) ENGINE=InnoDB;

-- Existencias por lote, procedencia y cantidades destinadas a pedidos.
CREATE TABLE `lotes` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `codigo` VARCHAR(40) NOT NULL,
  `producto_id` BIGINT NOT NULL,
  `origen` VARCHAR(255) NOT NULL,
  `proveedor_referencia` VARCHAR(150) NULL,
  `fecha_recepcion` DATE NOT NULL,
  `cantidad_inicial_kg` DECIMAL(14,3) NOT NULL,
  `cantidad_reservada_kg` DECIMAL(14,3) NOT NULL DEFAULT 0,
  `cantidad_despachada_kg` DECIMAL(14,3) NOT NULL DEFAULT 0,
  `cantidad_disponible_kg` DECIMAL(14,3) GENERATED ALWAYS AS (cantidad_inicial_kg - cantidad_reservada_kg - cantidad_despachada_kg) STORED,
  `estado` VARCHAR(12) NOT NULL DEFAULT 'ACTIVO',
  `creado_por` BIGINT NOT NULL,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `version` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  CONSTRAINT uq_lotes_1 UNIQUE (`codigo`),
  CONSTRAINT fk_lotes_1 FOREIGN KEY (`producto_id`) REFERENCES `productos` (`id`),
  CONSTRAINT fk_lotes_2 FOREIGN KEY (`creado_por`) REFERENCES `usuarios` (`id`),
  CONSTRAINT ck_lotes_1 CHECK (cantidad_inicial_kg > 0),
  CONSTRAINT ck_lotes_2 CHECK (cantidad_reservada_kg >= 0),
  CONSTRAINT ck_lotes_3 CHECK (cantidad_despachada_kg >= 0),
  CONSTRAINT ck_lotes_4 CHECK (cantidad_reservada_kg + cantidad_despachada_kg <= cantidad_inicial_kg),
  CONSTRAINT ck_lotes_5 CHECK (estado IN ('ACTIVO','BLOQUEADO','CERRADO')),
  CONSTRAINT ck_lotes_6 CHECK (version >= 0),
  INDEX ix_lotes_1 (`producto_id`, `estado`)
) ENGINE=InnoDB;

-- Cabecera de pedidos y cliente que solicita la exportación.
CREATE TABLE `pedidos` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `codigo` VARCHAR(40) NOT NULL,
  `cliente_id` BIGINT NOT NULL,
  `pais_destino` CHAR(2) NOT NULL,
  `estado` VARCHAR(15) NOT NULL DEFAULT 'BORRADOR',
  `fecha_pedido` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `observaciones` TEXT NULL,
  `creado_por` BIGINT NOT NULL,
  `version` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  CONSTRAINT uq_pedidos_1 UNIQUE (`codigo`),
  CONSTRAINT uq_pedidos_2 UNIQUE (`id`, `pais_destino`),
  CONSTRAINT fk_pedidos_1 FOREIGN KEY (`cliente_id`) REFERENCES `clientes` (`id`),
  CONSTRAINT fk_pedidos_2 FOREIGN KEY (`creado_por`) REFERENCES `usuarios` (`id`),
  CONSTRAINT ck_pedidos_1 CHECK (estado IN ('BORRADOR','CONFIRMADO','CANCELADO','FINALIZADO')),
  CONSTRAINT ck_pedidos_2 CHECK (version >= 0),
  INDEX ix_pedidos_1 (`cliente_id`, `fecha_pedido`)
) ENGINE=InnoDB;

-- Líneas que relacionan cada pedido con los lotes y cantidades solicitadas.
CREATE TABLE `detalle_pedido` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `pedido_id` BIGINT NOT NULL,
  `lote_id` BIGINT NOT NULL,
  `cantidad_kg` DECIMAL(14,3) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT uq_detalle_pedido_1 UNIQUE (`pedido_id`, `lote_id`),
  CONSTRAINT fk_detalle_pedido_1 FOREIGN KEY (`pedido_id`) REFERENCES `pedidos` (`id`),
  CONSTRAINT fk_detalle_pedido_2 FOREIGN KEY (`lote_id`) REFERENCES `lotes` (`id`),
  CONSTRAINT ck_detalle_pedido_1 CHECK (cantidad_kg > 0)
) ENGINE=InnoDB;

-- Expediente logístico de un pedido, su destino y sus fechas.
CREATE TABLE `exportaciones` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `codigo` VARCHAR(40) NOT NULL,
  `pedido_id` BIGINT NOT NULL,
  `pais_destino` CHAR(2) NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'PREPARACION',
  `requisitos_revisados` BOOLEAN NOT NULL DEFAULT FALSE,
  `fecha_salida_prevista` DATE NULL,
  `fecha_salida_real` DATETIME(6) NULL,
  `fecha_entrega` DATETIME(6) NULL,
  `responsable_id` BIGINT NOT NULL,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `version` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  CONSTRAINT uq_exportaciones_1 UNIQUE (`codigo`),
  CONSTRAINT uq_exportaciones_2 UNIQUE (`pedido_id`),
  CONSTRAINT fk_exportaciones_1 FOREIGN KEY (`pedido_id`, `pais_destino`) REFERENCES `pedidos` (`id`, `pais_destino`),
  CONSTRAINT fk_exportaciones_2 FOREIGN KEY (`responsable_id`) REFERENCES `usuarios` (`id`),
  CONSTRAINT ck_exportaciones_1 CHECK (estado IN ('PREPARACION','LISTA_DESPACHO','ADUANA','EN_TRANSITO','ENTREGADA','CANCELADA')),
  CONSTRAINT ck_exportaciones_2 CHECK (requisitos_revisados IN (0,1)),
  CONSTRAINT ck_exportaciones_3 CHECK (fecha_entrega IS NULL OR (fecha_salida_real IS NOT NULL AND fecha_entrega >= fecha_salida_real)),
  CONSTRAINT ck_exportaciones_4 CHECK (estado NOT IN ('EN_TRANSITO','ENTREGADA') OR fecha_salida_real IS NOT NULL),
  CONSTRAINT ck_exportaciones_5 CHECK (estado <> 'ENTREGADA' OR fecha_entrega IS NOT NULL),
  CONSTRAINT ck_exportaciones_6 CHECK (version >= 0),
  INDEX ix_exportaciones_1 (`estado`, `creado_en`)
) ENGINE=InnoDB;

-- Catálogo versionado de requisitos documentales configurables.
CREATE TABLE `requisitos_documentales` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `codigo` VARCHAR(40) NOT NULL,
  `version` INT NOT NULL DEFAULT 1,
  `nombre` VARCHAR(150) NOT NULL,
  `descripcion` TEXT NULL,
  `pais_destino` CHAR(2) NULL,
  `tipo_producto` VARCHAR(10) NULL,
  `obligatorio` BOOLEAN NOT NULL DEFAULT TRUE,
  `activo` BOOLEAN NOT NULL DEFAULT TRUE,
  `fuente_referencia` VARCHAR(500) NULL,
  `vigente_desde` DATE NOT NULL,
  `vigente_hasta` DATE NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT uq_requisitos_documentales_1 UNIQUE (`codigo`, `version`),
  CONSTRAINT ck_requisitos_documentales_1 CHECK (version > 0),
  CONSTRAINT ck_requisitos_documentales_2 CHECK (tipo_producto IS NULL OR tipo_producto IN ('CAFE','CACAO')),
  CONSTRAINT ck_requisitos_documentales_3 CHECK (obligatorio IN (0,1)),
  CONSTRAINT ck_requisitos_documentales_4 CHECK (activo IN (0,1)),
  CONSTRAINT ck_requisitos_documentales_5 CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde)
) ENGINE=InnoDB;

-- Checklist de requisitos aplicables a una exportación, exista o no un archivo cargado.
CREATE TABLE `exportacion_requisitos` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `exportacion_id` BIGINT NOT NULL,
  `requisito_id` BIGINT NOT NULL,
  `nombre_requisito` VARCHAR(150) NOT NULL,
  `obligatorio` BOOLEAN NOT NULL DEFAULT TRUE,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT uq_exportacion_requisitos_1 UNIQUE (`exportacion_id`, `requisito_id`),
  CONSTRAINT fk_exportacion_requisitos_1 FOREIGN KEY (`exportacion_id`) REFERENCES `exportaciones` (`id`),
  CONSTRAINT fk_exportacion_requisitos_2 FOREIGN KEY (`requisito_id`) REFERENCES `requisitos_documentales` (`id`),
  CONSTRAINT ck_exportacion_requisitos_1 CHECK (obligatorio IN (0,1))
) ENGINE=InnoDB;

-- Archivos y versiones documentales correspondientes al checklist de una exportación.
CREATE TABLE `documentos_exportacion` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `exportacion_requisito_id` BIGINT NOT NULL,
  `numero_version` INT NOT NULL DEFAULT 1,
  `vigente` BOOLEAN NOT NULL DEFAULT TRUE,
  `requisito_vigente_id` BIGINT GENERATED ALWAYS AS (CASE WHEN vigente = 1 THEN exportacion_requisito_id ELSE NULL END) STORED,
  `nombre_original` VARCHAR(200) NOT NULL,
  `ruta_archivo` VARCHAR(500) NOT NULL,
  `mime_type` VARCHAR(100) NOT NULL,
  `tamano_bytes` BIGINT NOT NULL,
  `sha256` CHAR(64) NOT NULL,
  `estado` VARCHAR(12) NOT NULL DEFAULT 'CARGADO',
  `cargado_por` BIGINT NOT NULL,
  `cargado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `revisado_por` BIGINT NULL,
  `revisado_en` DATETIME(6) NULL,
  `fecha_vencimiento` DATE NULL,
  `observacion_revision` VARCHAR(500) NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT uq_documentos_exportacion_1 UNIQUE (`exportacion_requisito_id`, `numero_version`),
  CONSTRAINT uq_documentos_exportacion_2 UNIQUE (`requisito_vigente_id`),
  CONSTRAINT fk_documentos_exportacion_1 FOREIGN KEY (`exportacion_requisito_id`) REFERENCES `exportacion_requisitos` (`id`),
  CONSTRAINT fk_documentos_exportacion_2 FOREIGN KEY (`cargado_por`) REFERENCES `usuarios` (`id`),
  CONSTRAINT fk_documentos_exportacion_3 FOREIGN KEY (`revisado_por`) REFERENCES `usuarios` (`id`),
  CONSTRAINT ck_documentos_exportacion_1 CHECK (numero_version > 0),
  CONSTRAINT ck_documentos_exportacion_2 CHECK (vigente IN (0,1)),
  CONSTRAINT ck_documentos_exportacion_3 CHECK (tamano_bytes > 0),
  CONSTRAINT ck_documentos_exportacion_4 CHECK (CHAR_LENGTH(sha256) = 64),
  CONSTRAINT ck_documentos_exportacion_5 CHECK (estado IN ('CARGADO','EN_REVISION','APROBADO','RECHAZADO')),
  CONSTRAINT ck_documentos_exportacion_6 CHECK (estado NOT IN ('APROBADO','RECHAZADO') OR (revisado_por IS NOT NULL AND revisado_en IS NOT NULL)),
  CONSTRAINT ck_documentos_exportacion_7 CHECK (revisado_en IS NULL OR revisado_en >= cargado_en)
) ENGINE=InnoDB;

-- Referencias de trámites realizados fuera de RiTech.
CREATE TABLE `tramites_externos` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `exportacion_id` BIGINT NOT NULL,
  `entidad` VARCHAR(60) NOT NULL,
  `tipo_tramite` VARCHAR(100) NOT NULL,
  `referencia` VARCHAR(120) NOT NULL,
  `estado_reportado` VARCHAR(60) NOT NULL,
  `observaciones` TEXT NULL,
  `registrado_por` BIGINT NOT NULL,
  `registrado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT uq_tramites_externos_1 UNIQUE (`exportacion_id`, `entidad`, `referencia`),
  CONSTRAINT fk_tramites_externos_1 FOREIGN KEY (`exportacion_id`) REFERENCES `exportaciones` (`id`),
  CONSTRAINT fk_tramites_externos_2 FOREIGN KEY (`registrado_por`) REFERENCES `usuarios` (`id`)
) ENGINE=InnoDB;

-- Registro cronológico de los cambios de estado de las exportaciones.
CREATE TABLE `historial_estados` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `exportacion_id` BIGINT NOT NULL,
  `estado_anterior` VARCHAR(20) NULL,
  `estado_nuevo` VARCHAR(20) NOT NULL,
  `fecha_evento` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `usuario_id` BIGINT NOT NULL,
  `observacion` VARCHAR(500) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT fk_historial_estados_1 FOREIGN KEY (`exportacion_id`) REFERENCES `exportaciones` (`id`),
  CONSTRAINT fk_historial_estados_2 FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`),
  CONSTRAINT ck_historial_estados_1 CHECK (estado_anterior IS NULL OR estado_anterior IN ('PREPARACION','LISTA_DESPACHO','ADUANA','EN_TRANSITO','ENTREGADA','CANCELADA')),
  CONSTRAINT ck_historial_estados_2 CHECK (estado_nuevo IN ('PREPARACION','LISTA_DESPACHO','ADUANA','EN_TRANSITO','ENTREGADA','CANCELADA')),
  CONSTRAINT ck_historial_estados_3 CHECK (estado_anterior IS NULL OR estado_anterior <> estado_nuevo),
  INDEX ix_historial_estados_1 (`exportacion_id`, `fecha_evento`, `id`)
) ENGINE=InnoDB;

-- Resultados de inspecciones técnicas sobre un lote.
CREATE TABLE `inspecciones_calidad` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `lote_id` BIGINT NOT NULL,
  `inspector_id` BIGINT NOT NULL,
  `fecha_inspeccion` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `humedad_porcentaje` DECIMAL(5,2) NULL,
  `evaluacion_taza` VARCHAR(150) NULL,
  `aroma` VARCHAR(150) NULL,
  `defectos` TEXT NULL,
  `observaciones` TEXT NULL,
  `resultado` VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
  PRIMARY KEY (`id`),
  CONSTRAINT fk_inspecciones_calidad_1 FOREIGN KEY (`lote_id`) REFERENCES `lotes` (`id`),
  CONSTRAINT fk_inspecciones_calidad_2 FOREIGN KEY (`inspector_id`) REFERENCES `usuarios` (`id`),
  CONSTRAINT ck_inspecciones_calidad_1 CHECK (humedad_porcentaje IS NULL OR humedad_porcentaje BETWEEN 0 AND 100),
  CONSTRAINT ck_inspecciones_calidad_2 CHECK (resultado IN ('PENDIENTE','APROBADO','RECHAZADO')),
  INDEX ix_inspecciones_calidad_1 (`lote_id`, `fecha_inspeccion`, `id`)
) ENGINE=InnoDB;

-- Hitos adicionales del lote no representados por pedidos, inspecciones o estados de exportación.
CREATE TABLE `eventos_trazabilidad` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `lote_id` BIGINT NOT NULL,
  `tipo_evento` VARCHAR(40) NOT NULL,
  `descripcion` VARCHAR(500) NOT NULL,
  `fecha_evento` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `usuario_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT fk_eventos_trazabilidad_1 FOREIGN KEY (`lote_id`) REFERENCES `lotes` (`id`),
  CONSTRAINT fk_eventos_trazabilidad_2 FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`),
  INDEX ix_eventos_trazabilidad_1 (`lote_id`, `fecha_evento`, `id`)
) ENGINE=InnoDB;

-- Registro técnico de operaciones críticas realizadas en la aplicación.
CREATE TABLE `auditoria` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `usuario_id` BIGINT NULL,
  `fecha_evento` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `accion` VARCHAR(40) NOT NULL,
  `tabla_afectada` VARCHAR(64) NOT NULL,
  `registro_id` BIGINT NULL,
  `detalle` JSON NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT fk_auditoria_1 FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`),
  INDEX ix_auditoria_1 (`tabla_afectada`, `registro_id`, `fecha_evento`)
) ENGINE=InnoDB;


-- Datos ficticios. Ejecutar una sola vez después de 01_estructura.sql.
USE ritech_sprint2;
SET NAMES utf8mb4;
SET SESSION time_zone = '+00:00';
SET @demo_now = CURRENT_TIMESTAMP(6);
START TRANSACTION;
INSERT INTO `roles` (`id`, `codigo`, `nombre`, `descripcion`) VALUES (1, 'GESTOR_COMERCIAL', 'Gestor Comercial', 'Rol académico: Gestor Comercial');
INSERT INTO `roles` (`id`, `codigo`, `nombre`, `descripcion`) VALUES (2, 'ADMINISTRADOR_VENTAS', 'Administrador de Ventas', 'Rol académico: Administrador de Ventas');
INSERT INTO `roles` (`id`, `codigo`, `nombre`, `descripcion`) VALUES (3, 'ESPECIALISTA_EXPORTACION', 'Especialista en Exportación', 'Rol académico: Especialista en Exportación');
INSERT INTO `roles` (`id`, `codigo`, `nombre`, `descripcion`) VALUES (4, 'COORDINADOR_LOGISTICO', 'Coordinador Logístico', 'Rol académico: Coordinador Logístico');
INSERT INTO `roles` (`id`, `codigo`, `nombre`, `descripcion`) VALUES (5, 'INSPECTOR_CALIDAD', 'Inspector de Calidad', 'Rol académico: Inspector de Calidad');
INSERT INTO `roles` (`id`, `codigo`, `nombre`, `descripcion`) VALUES (6, 'AUDITOR_PROCESO', 'Auditor de Proceso', 'Rol académico: Auditor de Proceso');
INSERT INTO `usuarios` (`id`, `rol_id`, `nombre`, `email`, `password_hash`, `creado_en`) VALUES (1, 1, 'Demo Gestor Comercial', 'comercial@ritech.example', '$2a$12$TG2M6rqr4woWB15oF2nTf.tcMz9nlHBBpaXBwlB9LRQeklFOg753K', @demo_now);
INSERT INTO `usuarios` (`id`, `rol_id`, `nombre`, `email`, `password_hash`, `creado_en`) VALUES (2, 2, 'Demo Administrador de Ventas', 'ventas@ritech.example', '$2a$12$7KIKJTcO9.YsNnVCSiPv7ewfeDYCM68VHGezMnrVq0.jEp98oQky2', @demo_now);
INSERT INTO `usuarios` (`id`, `rol_id`, `nombre`, `email`, `password_hash`, `creado_en`) VALUES (3, 3, 'Demo Especialista en Exportación', 'exportaciones@ritech.example', '$2a$12$7CMVlEQT.RNWjFLvIFyXOetEscYyEovop8IYErqJ6MNil.pvijbQq', @demo_now);
INSERT INTO `usuarios` (`id`, `rol_id`, `nombre`, `email`, `password_hash`, `creado_en`) VALUES (4, 4, 'Demo Coordinador Logístico', 'logistica@ritech.example', '$2a$12$p6Jwej6QY4lZauglyzfOG.kkjl9M3LAcqPPrivAXs0lybdJiMSVCC', @demo_now);
INSERT INTO `usuarios` (`id`, `rol_id`, `nombre`, `email`, `password_hash`, `creado_en`) VALUES (5, 5, 'Demo Inspector de Calidad', 'calidad@ritech.example', '$2a$12$MN9MiKAWDJyQAilSrMA2aeWePvDgvsrwh7PHdPTdDPXsu738x1wKW', @demo_now);
INSERT INTO `usuarios` (`id`, `rol_id`, `nombre`, `email`, `password_hash`, `creado_en`) VALUES (6, 6, 'Demo Auditor de Proceso', 'auditoria@ritech.example', '$2a$12$xS9k0Iwd/05acn2JOa6VmefCJFtX0PA3wvsNOkEX9mEUKr3mRNPNy', @demo_now);
INSERT INTO `clientes` (`id`, `razon_social`, `pais`, `identificacion_fiscal`, `email`, `telefono`, `direccion`, `contacto_principal`, `creado_en`) VALUES (1, 'Cliente Demo España', 'ES', 'DEMO-FISCAL-001', 'compras1@example.com', '+000000001', 'Dirección ficticia para pruebas', 'Contacto Demo 1', @demo_now);
INSERT INTO `clientes` (`id`, `razon_social`, `pais`, `identificacion_fiscal`, `email`, `telefono`, `direccion`, `contacto_principal`, `creado_en`) VALUES (2, 'Cliente Demo Alemania', 'DE', 'DEMO-FISCAL-002', 'compras2@example.com', '+000000002', 'Dirección ficticia para pruebas', 'Contacto Demo 2', @demo_now);
INSERT INTO `clientes` (`id`, `razon_social`, `pais`, `identificacion_fiscal`, `email`, `telefono`, `direccion`, `contacto_principal`, `creado_en`) VALUES (3, 'Cliente Demo Francia', 'FR', 'DEMO-FISCAL-003', 'compras3@example.com', '+000000003', 'Dirección ficticia para pruebas', 'Contacto Demo 3', @demo_now);
INSERT INTO `productos` (`id`, `codigo`, `nombre`, `tipo`, `descripcion`) VALUES (1, 'DEMO-CAFE-01', 'Café verde de demostración', 'CAFE', 'Producto de prueba académica; no inventario comercial real.');
INSERT INTO `productos` (`id`, `codigo`, `nombre`, `tipo`, `descripcion`) VALUES (2, 'DEMO-CACAO-01', 'Cacao en grano de demostración', 'CACAO', 'Producto de prueba académica; no inventario comercial real.');
INSERT INTO `lotes` (`id`, `codigo`, `producto_id`, `origen`, `proveedor_referencia`, `fecha_recepcion`, `cantidad_inicial_kg`, `cantidad_reservada_kg`, `cantidad_despachada_kg`, `creado_por`, `creado_en`) VALUES (1, 'DEMO-LOTE-001', 1, 'Sandoná, Nariño, Colombia', 'Proveedor ficticio 1', '2026-09-30', 1000, 150, 0, 2, @demo_now);
INSERT INTO `lotes` (`id`, `codigo`, `producto_id`, `origen`, `proveedor_referencia`, `fecha_recepcion`, `cantidad_inicial_kg`, `cantidad_reservada_kg`, `cantidad_despachada_kg`, `creado_por`, `creado_en`) VALUES (2, 'DEMO-LOTE-002', 2, 'Tumaco, Nariño, Colombia', 'Proveedor ficticio 2', '2026-09-30', 800, 100, 0, 2, @demo_now);
INSERT INTO `lotes` (`id`, `codigo`, `producto_id`, `origen`, `proveedor_referencia`, `fecha_recepcion`, `cantidad_inicial_kg`, `cantidad_reservada_kg`, `cantidad_despachada_kg`, `creado_por`, `creado_en`) VALUES (3, 'DEMO-LOTE-003', 1, 'La Unión, Nariño, Colombia', 'Proveedor ficticio 3', '2026-09-30', 500, 0, 0, 2, @demo_now);
INSERT INTO `pedidos` (`id`, `codigo`, `cliente_id`, `pais_destino`, `estado`, `observaciones`, `creado_por`, `fecha_pedido`) VALUES (1, 'DEMO-PED-001', 1, 'ES', 'CONFIRMADO', 'Pedido ficticio para demostración académica.', 2, @demo_now);
INSERT INTO `pedidos` (`id`, `codigo`, `cliente_id`, `pais_destino`, `estado`, `observaciones`, `creado_por`, `fecha_pedido`) VALUES (2, 'DEMO-PED-002', 2, 'DE', 'CONFIRMADO', 'Pedido ficticio para demostración académica.', 2, @demo_now);
INSERT INTO `pedidos` (`id`, `codigo`, `cliente_id`, `pais_destino`, `estado`, `observaciones`, `creado_por`, `fecha_pedido`) VALUES (3, 'DEMO-PED-003', 3, 'FR', 'BORRADOR', 'Pedido ficticio para demostración académica.', 2, @demo_now);
INSERT INTO `detalle_pedido` (`id`, `pedido_id`, `lote_id`, `cantidad_kg`) VALUES (1, 1, 1, 100);
INSERT INTO `detalle_pedido` (`id`, `pedido_id`, `lote_id`, `cantidad_kg`) VALUES (2, 1, 2, 100);
INSERT INTO `detalle_pedido` (`id`, `pedido_id`, `lote_id`, `cantidad_kg`) VALUES (3, 2, 1, 50);
INSERT INTO `detalle_pedido` (`id`, `pedido_id`, `lote_id`, `cantidad_kg`) VALUES (4, 3, 3, 25);
INSERT INTO `exportaciones` (`id`, `codigo`, `pedido_id`, `pais_destino`, `estado`, `requisitos_revisados`, `responsable_id`, `creado_en`) VALUES (1, 'DEMO-EXP-001', 1, 'ES', 'PREPARACION', 1, 3, @demo_now);
INSERT INTO `exportaciones` (`id`, `codigo`, `pedido_id`, `pais_destino`, `estado`, `requisitos_revisados`, `responsable_id`, `creado_en`) VALUES (2, 'DEMO-EXP-002', 2, 'DE', 'PREPARACION', 1, 3, @demo_now);
INSERT INTO `requisitos_documentales` (`id`, `codigo`, `version`, `nombre`, `descripcion`, `obligatorio`, `fuente_referencia`, `vigente_desde`) VALUES (1, 'DEMO-REQ-001', 1, 'Factura comercial de ejemplo', 'Regla interna de demostración, no obligación legal validada.', 1, 'Regla académica interna. Verificar requisitos oficiales antes de uso real.', '2026-09-30');
INSERT INTO `requisitos_documentales` (`id`, `codigo`, `version`, `nombre`, `descripcion`, `obligatorio`, `fuente_referencia`, `vigente_desde`) VALUES (2, 'DEMO-REQ-002', 1, 'Lista de empaque de ejemplo', 'Regla interna de demostración, no obligación legal validada.', 1, 'Regla académica interna. Verificar requisitos oficiales antes de uso real.', '2026-09-30');
INSERT INTO `requisitos_documentales` (`id`, `codigo`, `version`, `nombre`, `descripcion`, `obligatorio`, `fuente_referencia`, `vigente_desde`) VALUES (3, 'DEMO-REQ-003', 1, 'Ficha de inspección interna', 'Regla interna de demostración, no obligación legal validada.', 1, 'Regla académica interna. Verificar requisitos oficiales antes de uso real.', '2026-09-30');
INSERT INTO `exportacion_requisitos` (`id`, `exportacion_id`, `requisito_id`, `nombre_requisito`, `obligatorio`, `creado_en`) VALUES (1, 1, 1, 'Factura comercial de ejemplo', 1, @demo_now);
INSERT INTO `exportacion_requisitos` (`id`, `exportacion_id`, `requisito_id`, `nombre_requisito`, `obligatorio`, `creado_en`) VALUES (2, 1, 2, 'Lista de empaque de ejemplo', 1, @demo_now);
INSERT INTO `exportacion_requisitos` (`id`, `exportacion_id`, `requisito_id`, `nombre_requisito`, `obligatorio`, `creado_en`) VALUES (3, 1, 3, 'Ficha de inspección interna', 1, @demo_now);
INSERT INTO `exportacion_requisitos` (`id`, `exportacion_id`, `requisito_id`, `nombre_requisito`, `obligatorio`, `creado_en`) VALUES (4, 2, 1, 'Factura comercial de ejemplo', 1, @demo_now);
INSERT INTO `exportacion_requisitos` (`id`, `exportacion_id`, `requisito_id`, `nombre_requisito`, `obligatorio`, `creado_en`) VALUES (5, 2, 2, 'Lista de empaque de ejemplo', 1, @demo_now);
INSERT INTO `exportacion_requisitos` (`id`, `exportacion_id`, `requisito_id`, `nombre_requisito`, `obligatorio`, `creado_en`) VALUES (6, 2, 3, 'Ficha de inspección interna', 1, @demo_now);
INSERT INTO `documentos_exportacion` (`id`, `exportacion_requisito_id`, `numero_version`, `vigente`, `nombre_original`, `ruta_archivo`, `mime_type`, `tamano_bytes`, `sha256`, `estado`, `cargado_por`, `revisado_por`, `revisado_en`, `observacion_revision`, `cargado_en`) VALUES (1, 1, 1, 1, 'documento_1.txt', 'demo/DEMO-EXP-001/documento_1.txt', 'text/plain', 254, 'ddb140ccec92447db5205065618de5c3f9c0ca4d69a85a4518e6debcf417cecf', 'APROBADO', 3, 3, @demo_now, 'Aprobación interna ficticia para probar el flujo, no validación legal.', @demo_now);
INSERT INTO `documentos_exportacion` (`id`, `exportacion_requisito_id`, `numero_version`, `vigente`, `nombre_original`, `ruta_archivo`, `mime_type`, `tamano_bytes`, `sha256`, `estado`, `cargado_por`, `revisado_por`, `revisado_en`, `observacion_revision`, `cargado_en`) VALUES (2, 2, 1, 1, 'documento_2.txt', 'demo/DEMO-EXP-001/documento_2.txt', 'text/plain', 253, '56e16617ba930c43633a25b80c8c969864fd80310e1ee97690db1627fada6856', 'APROBADO', 3, 3, @demo_now, 'Aprobación interna ficticia para probar el flujo, no validación legal.', @demo_now);
INSERT INTO `documentos_exportacion` (`id`, `exportacion_requisito_id`, `numero_version`, `vigente`, `nombre_original`, `ruta_archivo`, `mime_type`, `tamano_bytes`, `sha256`, `estado`, `cargado_por`, `revisado_por`, `revisado_en`, `observacion_revision`, `cargado_en`) VALUES (3, 3, 1, 1, 'documento_3.txt', 'demo/DEMO-EXP-001/documento_3.txt', 'text/plain', 254, '5c8116f80918c82f740163fb9cc2f31741890978fcb80e88a331c632ed6bc2f2', 'APROBADO', 3, 3, @demo_now, 'Aprobación interna ficticia para probar el flujo, no validación legal.', @demo_now);
INSERT INTO `tramites_externos` (`id`, `exportacion_id`, `entidad`, `tipo_tramite`, `referencia`, `estado_reportado`, `observaciones`, `registrado_por`, `registrado_en`) VALUES (1, 1, 'VUCE', 'Referencia ficticia de demostración', 'DEMO-NO-VALIDA-001', 'EJEMPLO_SIN_VERIFICAR', 'No se realizó un trámite real ni conexión con la entidad.', 3, @demo_now);
INSERT INTO `historial_estados` (`id`, `exportacion_id`, `estado_anterior`, `estado_nuevo`, `usuario_id`, `observacion`, `fecha_evento`) VALUES (1, 1, NULL, 'PREPARACION', 3, 'Creación de expediente de demostración.', @demo_now);
INSERT INTO `historial_estados` (`id`, `exportacion_id`, `estado_anterior`, `estado_nuevo`, `usuario_id`, `observacion`, `fecha_evento`) VALUES (2, 2, NULL, 'PREPARACION', 3, 'Creación de expediente de demostración.', @demo_now);
INSERT INTO `inspecciones_calidad` (`id`, `lote_id`, `inspector_id`, `humedad_porcentaje`, `evaluacion_taza`, `aroma`, `defectos`, `observaciones`, `resultado`, `fecha_inspeccion`) VALUES (1, 1, 5, 11.5, 'Evaluación descriptiva ficticia', 'Descripción de ejemplo', 'Sin observaciones en el ejemplo académico', 'No representa ensayo de laboratorio ni certificación.', 'APROBADO', @demo_now);
INSERT INTO `inspecciones_calidad` (`id`, `lote_id`, `inspector_id`, `humedad_porcentaje`, `evaluacion_taza`, `aroma`, `defectos`, `observaciones`, `resultado`, `fecha_inspeccion`) VALUES (2, 2, 5, 7.5, NULL, 'Cacao, descripción ficticia', 'Sin observaciones en el ejemplo académico', 'Valor ficticio, no umbral normativo.', 'APROBADO', @demo_now);
INSERT INTO `eventos_trazabilidad` (`id`, `lote_id`, `tipo_evento`, `descripcion`, `usuario_id`, `fecha_evento`) VALUES (1, 1, 'RECEPCION_BODEGA', 'Hito adicional ficticio: verificación de recepción en bodega.', 2, @demo_now);
INSERT INTO `eventos_trazabilidad` (`id`, `lote_id`, `tipo_evento`, `descripcion`, `usuario_id`, `fecha_evento`) VALUES (2, 2, 'RECEPCION_BODEGA', 'Hito adicional ficticio: verificación de recepción en bodega.', 2, @demo_now);
INSERT INTO `auditoria` (`id`, `usuario_id`, `accion`, `tabla_afectada`, `registro_id`, `detalle`, `fecha_evento`) VALUES (1, NULL, 'CARGA_DEMO', 'sistema', NULL, '{"origen": "datos de demostracion", "nota": "No representa trabajo previo ni pruebas de la aplicacion"}', @demo_now);
COMMIT;

-- Consultas reutilizables y registros de solo anexado.
USE ritech_sprint2;
CREATE VIEW v_checklist_exportacion AS
SELECT er.id AS exportacion_requisito_id, er.exportacion_id,
       er.requisito_id, er.nombre_requisito, er.obligatorio,
       d.id AS documento_id, d.numero_version,
       COALESCE(d.estado, 'PENDIENTE') AS estado,
       d.fecha_vencimiento, d.revisado_por, d.revisado_en
FROM exportacion_requisitos er
LEFT JOIN documentos_exportacion d
  ON d.exportacion_requisito_id = er.id AND d.vigente = TRUE;

CREATE VIEW v_control_documental AS
SELECT e.id AS exportacion_id, e.codigo, e.requisitos_revisados,
       COUNT(c.exportacion_requisito_id) AS requisitos_configurados,
       SUM(CASE WHEN c.obligatorio = TRUE AND
           (c.estado <> 'APROBADO' OR
            (c.fecha_vencimiento IS NOT NULL AND c.fecha_vencimiento < UTC_DATE()))
           THEN 1 ELSE 0 END) AS obligatorios_no_conformes,
       CASE WHEN e.requisitos_revisados = TRUE
                 AND COUNT(c.exportacion_requisito_id) > 0
                 AND SUM(CASE WHEN c.obligatorio = TRUE AND
                     (c.estado <> 'APROBADO' OR
                      (c.fecha_vencimiento IS NOT NULL AND c.fecha_vencimiento < UTC_DATE()))
                     THEN 1 ELSE 0 END) = 0
            THEN TRUE ELSE FALSE END AS checklist_completo
FROM exportaciones e
LEFT JOIN v_checklist_exportacion c ON c.exportacion_id = e.id
GROUP BY e.id, e.codigo, e.requisitos_revisados;

CREATE VIEW v_trazabilidad_lote AS
SELECT l.id AS lote_id, l.codigo AS codigo_lote,
       CAST('LOTE' AS CHAR(24)) AS origen_evento, l.id AS referencia_id,
       CAST('REGISTRO_LOTE' AS CHAR(40)) AS tipo_evento,
       l.creado_en AS fecha_evento, l.creado_por AS usuario_id,
       CAST(CONCAT('Registro del lote: ',l.codigo,'. Origen: ',l.origen) AS CHAR(1000)) AS descripcion
FROM lotes l
UNION ALL
SELECT l.id,l.codigo,'INSPECCION',i.id,'INSPECCION_CALIDAD',
       i.fecha_inspeccion,i.inspector_id,CONCAT('Resultado registrado: ',i.resultado)
FROM inspecciones_calidad i JOIN lotes l ON l.id=i.lote_id
UNION ALL
SELECT l.id,l.codigo,'PEDIDO',dp.id,'ASOCIACION_PEDIDO',
       p.fecha_pedido,p.creado_por,CONCAT('Pedido ',p.codigo,'; cantidad: ',dp.cantidad_kg,' kg')
FROM detalle_pedido dp JOIN lotes l ON l.id=dp.lote_id JOIN pedidos p ON p.id=dp.pedido_id
UNION ALL
SELECT l.id,l.codigo,'ESTADO_EXPORTACION',h.id,'CAMBIO_ESTADO',
       h.fecha_evento,h.usuario_id,CONCAT('Exportación ',e.codigo,': ',h.estado_nuevo,'. ',h.observacion)
FROM lotes l JOIN detalle_pedido dp ON dp.lote_id=l.id
JOIN exportaciones e ON e.pedido_id=dp.pedido_id
JOIN historial_estados h ON h.exportacion_id=e.id
UNION ALL
SELECT l.id,l.codigo,'DOCUMENTO',d.id,'CARGA_DOCUMENTO',
       d.cargado_en,d.cargado_por,CONCAT('Exportación ',e.codigo,'; archivo ',d.nombre_original,'; versión ',d.numero_version)
FROM lotes l JOIN detalle_pedido dp ON dp.lote_id=l.id
JOIN exportaciones e ON e.pedido_id=dp.pedido_id
JOIN exportacion_requisitos er ON er.exportacion_id=e.id
JOIN documentos_exportacion d ON d.exportacion_requisito_id=er.id
UNION ALL
SELECT l.id,l.codigo,'EVENTO_LOTE',ev.id,ev.tipo_evento,
       ev.fecha_evento,ev.usuario_id,ev.descripcion
FROM eventos_trazabilidad ev JOIN lotes l ON l.id=ev.lote_id;

-- No se autoriza UPDATE/DELETE del historial, eventos adicionales ni auditoría.
-- Estos triggers NO crean eventos automáticamente por cada acción del backend.
DELIMITER $$
CREATE TRIGGER tr_historial_estados_update BEFORE UPDATE ON historial_estados
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registro de solo anexado: no se permite UPDATE en historial_estados';
END$$
CREATE TRIGGER tr_historial_estados_delete BEFORE DELETE ON historial_estados
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registro de solo anexado: no se permite DELETE en historial_estados';
END$$
CREATE TRIGGER tr_eventos_trazabilidad_update BEFORE UPDATE ON eventos_trazabilidad
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registro de solo anexado: no se permite UPDATE en eventos_trazabilidad';
END$$
CREATE TRIGGER tr_eventos_trazabilidad_delete BEFORE DELETE ON eventos_trazabilidad
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registro de solo anexado: no se permite DELETE en eventos_trazabilidad';
END$$
CREATE TRIGGER tr_auditoria_update BEFORE UPDATE ON auditoria
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registro de solo anexado: no se permite UPDATE en auditoria';
END$$
CREATE TRIGGER tr_auditoria_delete BEFORE DELETE ON auditoria
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registro de solo anexado: no se permite DELETE en auditoria';
END$$
DELIMITER ;

SET SESSION sql_mode = @ritech_old_sql_mode;
SELECT 'Base de datos creada. Ejecutar 04_verificar.sql y comprobar sus resultados.' AS mensaje;
