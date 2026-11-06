-- VERIFICACIONES DE SOLO LECTURA. No sustituyen pruebas de la aplicación.
USE ritech_sprint2;
SELECT VERSION() AS version_mysql, DATABASE() AS base_actual;
SELECT COUNT(*) AS tablas_base_esperadas_16
FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE';
SHOW FULL TABLES;
SELECT TABLE_NAME AS tabla_hija, CONSTRAINT_NAME AS restriccion,
       COLUMN_NAME AS columna_hija, REFERENCED_TABLE_NAME AS tabla_padre,
       REFERENCED_COLUMN_NAME AS columna_padre
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION;
SELECT codigo,cantidad_inicial_kg,cantidad_reservada_kg,cantidad_despachada_kg,
       cantidad_disponible_kg FROM lotes ORDER BY id;
-- Demo: disponibilidades esperadas: 850.000 / 700.000 / 500.000 kg.
SELECT * FROM v_checklist_exportacion ORDER BY exportacion_id,exportacion_requisito_id;
-- Demo: EXP-001 tiene 3 documentos APROBADOS internos; EXP-002 tiene 3 PENDIENTES.
SELECT * FROM v_control_documental ORDER BY exportacion_id;
-- checklist_completo esperado: 1 para DEMO-EXP-001 y 0 para DEMO-EXP-002.
-- Esto no cambia el estado de exportación ni autoriza un despacho oficial.
SELECT * FROM v_trazabilidad_lote WHERE codigo_lote='DEMO-LOTE-001'
ORDER BY fecha_evento,origen_evento,referencia_id;
-- Debe devolver cero filas si las reservas de los datos demo son consistentes.
SELECT l.codigo,l.cantidad_reservada_kg,
       COALESCE(SUM(CASE WHEN p.estado='CONFIRMADO'
           AND (e.id IS NULL OR e.estado IN ('PREPARACION','LISTA_DESPACHO','ADUANA'))
           THEN dp.cantidad_kg ELSE 0 END),0) AS reserva_segun_pedidos
FROM lotes l LEFT JOIN detalle_pedido dp ON dp.lote_id=l.id
LEFT JOIN pedidos p ON p.id=dp.pedido_id
LEFT JOIN exportaciones e ON e.pedido_id=p.id
GROUP BY l.id,l.codigo,l.cantidad_reservada_kg
HAVING l.cantidad_reservada_kg <> reserva_segun_pedidos;

-- Conteos esperados inmediatamente después de cargar solo los datos demo.
SELECT 'roles' AS tabla, 6 AS esperado_demo, COUNT(*) AS obtenido FROM `roles`
UNION ALL
SELECT 'usuarios' AS tabla, 6 AS esperado_demo, COUNT(*) AS obtenido FROM `usuarios`
UNION ALL
SELECT 'clientes' AS tabla, 3 AS esperado_demo, COUNT(*) AS obtenido FROM `clientes`
UNION ALL
SELECT 'productos' AS tabla, 2 AS esperado_demo, COUNT(*) AS obtenido FROM `productos`
UNION ALL
SELECT 'lotes' AS tabla, 3 AS esperado_demo, COUNT(*) AS obtenido FROM `lotes`
UNION ALL
SELECT 'pedidos' AS tabla, 3 AS esperado_demo, COUNT(*) AS obtenido FROM `pedidos`
UNION ALL
SELECT 'detalle_pedido' AS tabla, 4 AS esperado_demo, COUNT(*) AS obtenido FROM `detalle_pedido`
UNION ALL
SELECT 'exportaciones' AS tabla, 2 AS esperado_demo, COUNT(*) AS obtenido FROM `exportaciones`
UNION ALL
SELECT 'requisitos_documentales' AS tabla, 3 AS esperado_demo, COUNT(*) AS obtenido FROM `requisitos_documentales`
UNION ALL
SELECT 'exportacion_requisitos' AS tabla, 6 AS esperado_demo, COUNT(*) AS obtenido FROM `exportacion_requisitos`
UNION ALL
SELECT 'documentos_exportacion' AS tabla, 3 AS esperado_demo, COUNT(*) AS obtenido FROM `documentos_exportacion`
UNION ALL
SELECT 'tramites_externos' AS tabla, 1 AS esperado_demo, COUNT(*) AS obtenido FROM `tramites_externos`
UNION ALL
SELECT 'historial_estados' AS tabla, 2 AS esperado_demo, COUNT(*) AS obtenido FROM `historial_estados`
UNION ALL
SELECT 'inspecciones_calidad' AS tabla, 2 AS esperado_demo, COUNT(*) AS obtenido FROM `inspecciones_calidad`
UNION ALL
SELECT 'eventos_trazabilidad' AS tabla, 2 AS esperado_demo, COUNT(*) AS obtenido FROM `eventos_trazabilidad`
UNION ALL
SELECT 'auditoria' AS tabla, 1 AS esperado_demo, COUNT(*) AS obtenido FROM `auditoria`;
