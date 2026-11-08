# Cambios respecto a la versión anterior (Casos de Uso 2: CU-03 a CU-08)

## Trazabilidad rúbrica -> código

| Req. | Prioridad | Estado | Dónde está |
|---|---|---|---|
| RF-03 Registrar productos (código, nombre, descripción, categoría, **unidad de medida**, stock inicial, **código de barras**) | MUST | Ampliado | `ProductoFormActivity`, `activity_producto_form.xml`, `DBHelper.insertarProducto` |
| RF-04 Consultar productos | MUST | Ya existía | `MainActivity`, `ProductoAdapter` (ahora con unidad de medida) |
| RF-05 Buscar por nombre / SKU (+ código de barras) | MUST | Ampliado | `DBHelper.buscarProductos` |
| RF-07 Modificar productos | MUST | Ajustado | El stock ya NO se edita a mano; solo cambia con movimientos |
| RF-08 Deshabilitar (baja lógica) | MUST | Ya existía | `DBHelper.deshabilitarProducto` (los movimientos se conservan) |
| RF-15 Registrar entradas | MUST | Nuevo | `MovimientoActivity` (tipo ENTRADA), `DBHelper.registrarMovimiento` |
| RF-16 Registrar salidas | MUST | Nuevo | `MovimientoActivity` (tipo SALIDA), `DBHelper.registrarMovimiento` |
| RF-17 Consultar stock actual/disponible | MUST | Nuevo | Catálogo, ficha del producto y formulario de movimientos |
| RF-18 Validar movimientos / sin stock negativo | MUST | Nuevo | Validación en pantalla + transacción en `registrarMovimiento` + `CHECK` en BD |
| RF-19 Informe de stock | SHOULD | Nuevo | `InformeStockActivity` |
| RF-20 Buscar por código de barras (cámara) | SHOULD | Nuevo | `EscanerCodigo` (ZXing), ícono en la barra del catálogo |
| RF-21 Historial de movimientos | SHOULD | Nuevo | `HistorialActivity` |
| RF-22 Filtrar informes (categoría, producto, estado) | SHOULD | Nuevo | `InformeStockActivity`, `DBHelper.generarInformeStock` |
| RF-24 Ubicación de la empresa en Google Maps | SHOULD | Nuevo | `UbicacionActivity` |
| RF-23 Exportar informe a PDF | COULD | Nuevo | `InformePdf` (menú ⋮ del informe) |
| RF-25 Ubicación del dispositivo (GPS, con permiso) | COULD | Nuevo | `UbicacionActivity` |
| RF-26 Ruta hacia la empresa | COULD | Nuevo | `UbicacionActivity` ("Cómo llegar") |

## Base de datos (versión 1 -> 3)
- `PRODUCTO`: columnas nuevas `codigo_barras` (único si se informa), `unidad_medida`, `ubicacion_fisica`.
- Tabla nueva `MOVIMIENTO` (producto, usuario, tipo, cantidad > 0, fecha, proveedor, documento, motivo, destinatario, observación, stock resultante).
- Tabla nueva `EMPRESA` (nombre, dirección, latitud, longitud).
- `onUpgrade` ya NO borra los datos: usa `ALTER TABLE` y crea las tablas nuevas.
- Rol nuevo **Consulta** (usuario de prueba `consulta` / `consulta123`): ve productos e informes, no registra movimientos.
- Usuarios de prueba que ya existían: `admin / admin123`, `bodeguero / bodega123`.

## Permisos por rol
| | Administrador | Operador | Consulta |
|---|---|---|---|
| Crear / modificar / deshabilitar productos | Sí | No | No |
| Registrar entradas y salidas | Sí | Sí | No |
| Catálogo, escáner, historial, informe, ubicación | Sí | Sí | Sí |
| Configurar ubicación de la empresa | Sí | No | No |

## Para probar
1. Sincronizar Gradle (se agregó la librería `zxing-android-embedded` 4.3.0).
2. Si ya tenías la app instalada, se migra sola (o desinstálala para partir de cero).
3. Los productos de ejemplo traen códigos EAN-13 válidos: 7801234500013, 7801234500020, 7801234500037, 7801234500044, 7801234500051 (puedes generarlos en cualquier sitio de "barcode generator" y escanearlos desde otra pantalla).
4. Las coordenadas de la empresa son las del Mercado Municipal de Ovalle (Independencia 362); si cambian, cámbialas con el administrador en Menú ⋮ > Ubicación de la empresa > Configurar.
