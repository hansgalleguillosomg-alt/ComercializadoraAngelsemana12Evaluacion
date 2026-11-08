package com.example.comercializadoraangel.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.comercializadoraangel.util.PasswordUtils;

import android.database.SQLException;

import java.util.ArrayList;
import java.util.List;

/**
 * Base de datos SQLite de la Comercializadora Angel.
 * Crea las tablas la primera vez (onCreate) y precarga los datos iniciales (Database Seeding):
 * roles, un administrador, un operador de prueba, categorías y algunos productos.
 */
public class DBHelper extends SQLiteOpenHelper {

    // Nombre físico del archivo de la base de datos y su versión
    private static final String DB_NAME = "BD_COMERCIALIZADORA.db";
    private static final int DB_VERSION = 3;

    // Resultados de registrarMovimiento (RF-15, RF-16, RF-18)
    public static final int MOV_OK = 0;
    public static final int MOV_PRODUCTO_NO_ENCONTRADO = 1;
    public static final int MOV_PRODUCTO_INACTIVO = 2;
    public static final int MOV_STOCK_INSUFICIENTE = 3;
    public static final int MOV_ERROR_ALMACENAMIENTO = 4;

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // Activa las claves foráneas en cada conexión (SQLite las trae desactivadas)
    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    // Se ejecuta UNA SOLA VEZ, al crear la base de datos por primera vez
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE ROL (" +
                "id_rol INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nombre_rol TEXT NOT NULL UNIQUE, " +
                "descripcion TEXT)");

        db.execSQL("CREATE TABLE USUARIO (" +
                "id_usuario INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT NOT NULL UNIQUE, " +
                "password_hash TEXT NOT NULL, " +
                "nombre_completo TEXT NOT NULL, " +
                "id_rol INTEGER NOT NULL, " +
                "activo INTEGER NOT NULL DEFAULT 1, " +
                "FOREIGN KEY (id_rol) REFERENCES ROL(id_rol) ON DELETE RESTRICT)");

        db.execSQL("CREATE TABLE CATEGORIA (" +
                "id_categoria INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nombre_categoria TEXT NOT NULL UNIQUE, " +
                "descripcion TEXT, " +
                "activo INTEGER NOT NULL DEFAULT 1)");

        db.execSQL("CREATE TABLE PROVEEDOR (" +
                "id_proveedor INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "rut_empresa TEXT NOT NULL UNIQUE, " +
                "razon_social TEXT NOT NULL, " +
                "telefono_contacto TEXT NOT NULL)");

        db.execSQL("CREATE TABLE PRODUCTO (" +
                "id_producto INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sku_codigo TEXT NOT NULL UNIQUE, " +
                "codigo_barras TEXT, " +
                "nombre TEXT NOT NULL, " +
                "descripcion TEXT, " +
                "unidad_medida TEXT NOT NULL DEFAULT 'Unidad', " +
                "ubicacion_fisica TEXT, " +
                "id_categoria INTEGER NOT NULL, " +
                "stock_actual INTEGER NOT NULL CHECK (stock_actual >= 0), " +
                "stock_minimo INTEGER NOT NULL DEFAULT 0, " +
                "activo INTEGER NOT NULL DEFAULT 1, " +
                "FOREIGN KEY (id_categoria) REFERENCES CATEGORIA(id_categoria) ON DELETE RESTRICT)");

        crearTablasMovimientosYEmpresa(db);
        cargarDatosIniciales(db);
    }

    /**
     * Migración v1 -> v2 SIN borrar datos: agrega columnas a PRODUCTO y crea MOVIMIENTO y EMPRESA.
     * (Antes onUpgrade borraba todas las tablas.)
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE PRODUCTO ADD COLUMN codigo_barras TEXT");
            db.execSQL("ALTER TABLE PRODUCTO ADD COLUMN unidad_medida TEXT NOT NULL DEFAULT 'Unidad'");
            db.execSQL("ALTER TABLE PRODUCTO ADD COLUMN ubicacion_fisica TEXT");
            crearTablasMovimientosYEmpresa(db);
            sembrarRolConsulta(db);
            db.execSQL("UPDATE ROL SET descripcion = ? WHERE nombre_rol = ?",
                    new Object[]{DESC_OPERADOR, Rol.OPERADOR});
        }
        if (oldVersion < 3) {
            // Reemplaza las coordenadas de ejemplo (Santiago) solo si nadie las había cambiado
            db.execSQL("UPDATE EMPRESA SET direccion = ?, latitud = ?, longitud = ? "
                            + "WHERE latitud = -33.4378 AND longitud = -70.6504",
                    new Object[]{DIRECCION_EMPRESA, LAT_EMPRESA, LNG_EMPRESA});
        }
    }

    private static final String DIRECCION_EMPRESA =
            "Independencia 362, Mercado Municipal de Ovalle, Región de Coquimbo, Chile";
    private static final double LAT_EMPRESA = -30.60274;
    private static final double LNG_EMPRESA = -71.20037;

    private static final String DESC_OPERADOR =
            "Consulta productos e informes y registra entradas y salidas de bodega";

    /** RF-15, RF-16, RF-21 (MOVIMIENTO), RF-24 (EMPRESA) y unicidad del código de barras. */
    private void crearTablasMovimientosYEmpresa(SQLiteDatabase db) {
        // Único solo cuando se informa (varios productos pueden no tener código de barras)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_producto_barras "
                + "ON PRODUCTO(codigo_barras) WHERE codigo_barras IS NOT NULL");

        db.execSQL("CREATE TABLE MOVIMIENTO (" +
                "id_movimiento INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "id_producto INTEGER NOT NULL, " +
                "id_usuario INTEGER NOT NULL, " +
                "tipo TEXT NOT NULL CHECK (tipo IN ('ENTRADA','SALIDA')), " +
                "cantidad INTEGER NOT NULL CHECK (cantidad > 0), " +
                "fecha TEXT NOT NULL, " +
                "id_proveedor INTEGER, " +
                "documento TEXT, " +
                "motivo TEXT, " +
                "destinatario TEXT, " +
                "observacion TEXT, " +
                "stock_resultante INTEGER NOT NULL, " +
                "FOREIGN KEY (id_producto) REFERENCES PRODUCTO(id_producto) ON DELETE RESTRICT, " +
                "FOREIGN KEY (id_usuario) REFERENCES USUARIO(id_usuario) ON DELETE RESTRICT, " +
                "FOREIGN KEY (id_proveedor) REFERENCES PROVEEDOR(id_proveedor) ON DELETE SET NULL)");
        db.execSQL("CREATE INDEX idx_movimiento_producto ON MOVIMIENTO(id_producto)");
        db.execSQL("CREATE INDEX idx_movimiento_fecha ON MOVIMIENTO(fecha)");

        db.execSQL("CREATE TABLE EMPRESA (" +
                "id_empresa INTEGER PRIMARY KEY CHECK (id_empresa = 1), " +
                "nombre TEXT NOT NULL, " +
                "direccion TEXT, " +
                "latitud REAL, " +
                "longitud REAL)");
        // Ubicación de la empresa (el administrador puede cambiarla desde la app)
        db.execSQL("INSERT INTO EMPRESA (id_empresa, nombre, direccion, latitud, longitud) VALUES (1, ?, ?, ?, ?)",
                new Object[]{"Comercializadora Angel", DIRECCION_EMPRESA, LAT_EMPRESA, LNG_EMPRESA});
    }

    /** Rol "Consulta" (actor Usuario de Consulta): ve productos e informes, no registra movimientos. */
    private void sembrarRolConsulta(SQLiteDatabase db) {
        db.execSQL("INSERT OR IGNORE INTO ROL (nombre_rol, descripcion) VALUES (?, ?)",
                new Object[]{Rol.CONSULTA, "Solo consulta productos, informes y ubicación (no modifica información)"});
        db.execSQL("INSERT OR IGNORE INTO USUARIO (username, password_hash, nombre_completo, id_rol, activo) "
                        + "SELECT ?, ?, ?, id_rol, 1 FROM ROL WHERE nombre_rol = ?",
                new Object[]{"consulta", PasswordUtils.hash("consulta123"), "Usuario de Consulta", Rol.CONSULTA});
    }

    private void cargarDatosIniciales(SQLiteDatabase db) {
        // Roles (id 1 = Administrador, id 2 = Operador)
        db.execSQL("INSERT INTO ROL (nombre_rol, descripcion) VALUES (?, ?)",
                new Object[]{Rol.ADMINISTRADOR, "Gestión completa: usuarios, roles, categorías, proveedores y productos"});
        db.execSQL("INSERT INTO ROL (nombre_rol, descripcion) VALUES (?, ?)",
                new Object[]{Rol.OPERADOR, DESC_OPERADOR});

        // Usuarios iniciales
        db.execSQL("INSERT INTO USUARIO (username, password_hash, nombre_completo, id_rol, activo) VALUES (?, ?, ?, ?, ?)",
                new Object[]{"admin", PasswordUtils.hash("admin123"), "Nathalie Angel Rivera", 1, 1});
        db.execSQL("INSERT INTO USUARIO (username, password_hash, nombre_completo, id_rol, activo) VALUES (?, ?, ?, ?, ?)",
                new Object[]{"bodeguero", PasswordUtils.hash("bodega123"), "Bodeguero de Prueba", 2, 1});

        sembrarRolConsulta(db);

        // Categorías del negocio
        db.execSQL("INSERT INTO CATEGORIA (nombre_categoria, descripcion, activo) VALUES (?, ?, ?)",
                new Object[]{"Frutos secos", "Nueces, almendras, maní, pistachos, etc.", 1});
        db.execSQL("INSERT INTO CATEGORIA (nombre_categoria, descripcion, activo) VALUES (?, ?, ?)",
                new Object[]{"Frutas deshidratadas", "Pasas, orejones, ciruelas secas, etc.", 1});
        db.execSQL("INSERT INTO CATEGORIA (nombre_categoria, descripcion, activo) VALUES (?, ?, ?)",
                new Object[]{"Quesos", "Quesos frescos y maduros", 1});

        // Productos de ejemplo
        // Los códigos de barras de ejemplo son EAN-13 válidos, para poder probar el escáner
        insertarProductoInicial(db, "FS-001", "7801234500013", "Nueces mariposa", "Nuez pelada, bolsa de 1 kg", 1, "Bolsa", "Pasillo A - Estante 1", 40, 10);
        insertarProductoInicial(db, "FS-002", "7801234500020", "Almendras", "Almendra natural, bolsa de 1 kg", 1, "Bolsa", "Pasillo A - Estante 2", 25, 8);
        insertarProductoInicial(db, "FD-001", "7801234500037", "Pasas rubias", "Pasas sin pepa, bolsa de 500 g", 2, "Bolsa", "Pasillo B - Estante 1", 30, 10);
        insertarProductoInicial(db, "QS-001", "7801234500044", "Queso gauda", "Gauda laminado, pieza", 3, "Pieza", "Cámara de frío 1", 12, 5);
        insertarProductoInicial(db, "QS-002", "7801234500051", "Queso mantecoso", "Queso mantecoso, pieza", 3, "Pieza", "Cámara de frío 1", 15, 5);
    }

    private void insertarProductoInicial(SQLiteDatabase db, String sku, String codigoBarras, String nombre,
                                         String descripcion, int idCategoria, String unidad,
                                         String ubicacion, int stockActual, int stockMinimo) {
        db.execSQL("INSERT INTO PRODUCTO (sku_codigo, codigo_barras, nombre, descripcion, id_categoria, "
                        + "unidad_medida, ubicacion_fisica, stock_actual, stock_minimo, activo) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)",
                new Object[]{sku, codigoBarras, nombre, descripcion, idCategoria, unidad, ubicacion,
                        stockActual, stockMinimo});
    }

    // =====================================================================
    //  Utilidades de lectura / escritura (cada método abre y cierra la BD)
    // =====================================================================

    /** Cuenta las filas que devuelve un SELECT COUNT(*) con parámetros. */
    private int contar(String sql, String... args) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(sql, args)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            db.close();
        }
    }

    private static String texto(Cursor c, String columna) {
        return c.getString(c.getColumnIndexOrThrow(columna));
    }

    private static int entero(Cursor c, String columna) {
        return c.getInt(c.getColumnIndexOrThrow(columna));
    }

    // ---------------------------- ROL ----------------------------

    public void insertarRol(Rol rol) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.execSQL("INSERT INTO ROL (nombre_rol, descripcion) VALUES (?, ?)",
                    new Object[]{rol.nombreRol, rol.descripcion});
        } finally {
            db.close();
        }
    }

    public int contarRolesPorNombre(String nombre) {
        return contar("SELECT COUNT(*) FROM ROL WHERE nombre_rol = ? COLLATE NOCASE", nombre);
    }

    public Rol buscarRolPorId(int idRol) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM ROL WHERE id_rol = ? LIMIT 1",
                new String[]{String.valueOf(idRol)})) {
            return c.moveToFirst() ? leerRol(c) : null;
        } finally {
            db.close();
        }
    }

    public List<Rol> listarRoles() {
        List<Rol> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM ROL ORDER BY nombre_rol ASC", null)) {
            while (c.moveToNext()) {
                lista.add(leerRol(c));
            }
        } finally {
            db.close();
        }
        return lista;
    }

    private Rol leerRol(Cursor c) {
        Rol r = new Rol();
        r.idRol = entero(c, "id_rol");
        r.nombreRol = texto(c, "nombre_rol");
        r.descripcion = texto(c, "descripcion");
        return r;
    }

    // --------------------------- USUARIO ---------------------------

    /** CU-01 paso 4: consulta la entidad USUARIO por username. */
    public Usuario buscarUsuarioPorUsername(String username) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM USUARIO WHERE username = ? COLLATE NOCASE LIMIT 1",
                new String[]{username})) {
            if (!c.moveToFirst()) {
                return null;
            }
            Usuario u = new Usuario();
            u.idUsuario = entero(c, "id_usuario");
            u.username = texto(c, "username");
            u.passwordHash = texto(c, "password_hash");
            u.nombreCompleto = texto(c, "nombre_completo");
            u.idRol = entero(c, "id_rol");
            u.activo = entero(c, "activo");
            return u;
        } finally {
            db.close();
        }
    }

    /** CU-27 flujo alternativo 6a: validación previa de username duplicado. */
    public int contarUsuariosPorUsername(String username) {
        return contar("SELECT COUNT(*) FROM USUARIO WHERE username = ? COLLATE NOCASE", username);
    }

    public void insertarUsuario(Usuario u) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.execSQL("INSERT INTO USUARIO (username, password_hash, nombre_completo, id_rol, activo) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    new Object[]{u.username, u.passwordHash, u.nombreCompleto, u.idRol, u.activo});
        } finally {
            db.close();
        }
    }

    /** RF-27: activa (1) o desactiva (0) un usuario. Un usuario inactivo no puede iniciar sesión. */
    public void cambiarEstadoUsuario(int idUsuario, int activo) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.execSQL("UPDATE USUARIO SET activo = ? WHERE id_usuario = ?",
                    new Object[]{activo, idUsuario});
        } finally {
            db.close();
        }
    }

    /** CU-27 paso 2: listado de usuarios con el nombre de su rol. */
    public List<UsuarioItem> listarUsuariosConRol() {
        List<UsuarioItem> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT u.id_usuario, u.username, u.nombre_completo, u.activo, r.nombre_rol "
                + "FROM USUARIO u INNER JOIN ROL r ON r.id_rol = u.id_rol "
                + "ORDER BY u.nombre_completo COLLATE NOCASE ASC", null)) {
            while (c.moveToNext()) {
                UsuarioItem u = new UsuarioItem();
                u.idUsuario = entero(c, "id_usuario");
                u.username = texto(c, "username");
                u.nombreCompleto = texto(c, "nombre_completo");
                u.activo = entero(c, "activo");
                u.nombreRol = texto(c, "nombre_rol");
                lista.add(u);
            }
        } finally {
            db.close();
        }
        return lista;
    }

    // -------------------------- CATEGORIA --------------------------

    /** CU-09 paso 5: valida nombre repetido (sin distinguir mayúsculas). */
    public int contarCategoriasPorNombre(String nombre) {
        return contar("SELECT COUNT(*) FROM CATEGORIA WHERE nombre_categoria = ? COLLATE NOCASE", nombre);
    }

    /** CU-09 paso 6: inserta con activo = 1 por defecto. */
    public void insertarCategoria(Categoria cat) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.execSQL("INSERT INTO CATEGORIA (nombre_categoria, descripcion, activo) VALUES (?, ?, ?)",
                    new Object[]{cat.nombreCategoria, cat.descripcion, cat.activo});
        } finally {
            db.close();
        }
    }

    /** Todas las categorías (activas primero), para que el administrador pueda habilitarlas o deshabilitarlas. */
    public List<Categoria> listarTodasLasCategorias() {
        List<Categoria> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM CATEGORIA "
                + "ORDER BY activo DESC, nombre_categoria ASC", null)) {
            while (c.moveToNext()) {
                Categoria cat = new Categoria();
                cat.idCategoria = entero(c, "id_categoria");
                cat.nombreCategoria = texto(c, "nombre_categoria");
                cat.descripcion = texto(c, "descripcion");
                cat.activo = entero(c, "activo");
                lista.add(cat);
            }
        } finally {
            db.close();
        }
        return lista;
    }

    /** Habilita (1) o deshabilita (0) una categoría. Una categoría deshabilitada no aparece al registrar productos. */
    public void cambiarEstadoCategoria(int idCategoria, int activo) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.execSQL("UPDATE CATEGORIA SET activo = ? WHERE id_categoria = ?",
                    new Object[]{activo, idCategoria});
        } finally {
            db.close();
        }
    }

    /** CU-10 paso 2: solo categorías activas, ordenadas por nombre. */
    public List<Categoria> listarCategoriasActivas() {
        List<Categoria> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM CATEGORIA WHERE activo = 1 "
                + "ORDER BY nombre_categoria ASC", null)) {
            while (c.moveToNext()) {
                Categoria cat = new Categoria();
                cat.idCategoria = entero(c, "id_categoria");
                cat.nombreCategoria = texto(c, "nombre_categoria");
                cat.descripcion = texto(c, "descripcion");
                cat.activo = entero(c, "activo");
                lista.add(cat);
            }
        } finally {
            db.close();
        }
        return lista;
    }

    // -------------------------- PROVEEDOR --------------------------

    public int contarProveedoresPorRut(String rut) {
        return contar("SELECT COUNT(*) FROM PROVEEDOR WHERE rut_empresa = ? COLLATE NOCASE", rut);
    }

    public void insertarProveedor(Proveedor p) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.execSQL("INSERT INTO PROVEEDOR (rut_empresa, razon_social, telefono_contacto) VALUES (?, ?, ?)",
                    new Object[]{p.rutEmpresa, p.razonSocial, p.telefonoContacto});
        } finally {
            db.close();
        }
    }

    public List<Proveedor> listarProveedores() {
        List<Proveedor> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM PROVEEDOR ORDER BY razon_social COLLATE NOCASE ASC", null)) {
            while (c.moveToNext()) {
                Proveedor p = new Proveedor();
                p.idProveedor = entero(c, "id_proveedor");
                p.rutEmpresa = texto(c, "rut_empresa");
                p.razonSocial = texto(c, "razon_social");
                p.telefonoContacto = texto(c, "telefono_contacto");
                lista.add(p);
            }
        } finally {
            db.close();
        }
        return lista;
    }

    // --------------------------- PRODUCTO ---------------------------

    public int contarProductosPorSku(String sku) {
        return contar("SELECT COUNT(*) FROM PRODUCTO WHERE sku_codigo = ? COLLATE NOCASE", sku);
    }

    /** CU-03: el código de barras debe ser único (excluye al propio producto al modificar). */
    public int contarProductosPorBarras(String codigoBarras, int idProductoExcluido) {
        return contar("SELECT COUNT(*) FROM PRODUCTO WHERE codigo_barras = ? AND id_producto <> ?",
                codigoBarras, String.valueOf(idProductoExcluido));
    }

    /** RF-03: alta de producto. Los campos opcionales vacíos se guardan como NULL. */
    public void insertarProducto(Producto p) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.execSQL("INSERT INTO PRODUCTO (sku_codigo, codigo_barras, nombre, descripcion, unidad_medida, "
                            + "ubicacion_fisica, id_categoria, stock_actual, stock_minimo, activo) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{p.skuCodigo, vacioANulo(p.codigoBarras), p.nombre, p.descripcion,
                            p.unidadMedida, vacioANulo(p.ubicacionFisica), p.idCategoria,
                            p.stockActual, p.stockMinimo, p.activo});
        } finally {
            db.close();
        }
    }

    private static String vacioANulo(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static final String SELECT_PRODUCTO_ITEM =
            "SELECT p.id_producto, p.sku_codigo, p.codigo_barras, p.nombre, p.descripcion, "
                    + "p.unidad_medida, p.ubicacion_fisica, p.stock_actual, p.stock_minimo, p.activo, "
                    + "p.id_categoria, c.nombre_categoria "
                    + "FROM PRODUCTO p INNER JOIN CATEGORIA c ON c.id_categoria = p.id_categoria ";

    private static ProductoItem leerProductoItem(Cursor c) {
        ProductoItem p = new ProductoItem();
        p.idProducto = entero(c, "id_producto");
        p.skuCodigo = texto(c, "sku_codigo");
        p.codigoBarras = texto(c, "codigo_barras");
        p.nombre = texto(c, "nombre");
        p.descripcion = texto(c, "descripcion");
        p.unidadMedida = texto(c, "unidad_medida");
        p.ubicacionFisica = texto(c, "ubicacion_fisica");
        p.stockActual = entero(c, "stock_actual");
        p.stockMinimo = entero(c, "stock_minimo");
        p.activo = entero(c, "activo");
        p.idCategoria = entero(c, "id_categoria");
        p.nombreCategoria = texto(c, "nombre_categoria");
        return p;
    }

    /** RF-04 y RF-05: catálogo + búsqueda por nombre, SKU o código de barras (solo productos activos). */
    public List<ProductoItem> buscarProductos(String busqueda) {
        List<ProductoItem> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(SELECT_PRODUCTO_ITEM
                        + "WHERE p.activo = 1 "
                        + "AND (p.nombre LIKE '%' || ? || '%' OR p.sku_codigo LIKE '%' || ? || '%' "
                        + "OR p.codigo_barras LIKE '%' || ? || '%') "
                        + "ORDER BY p.nombre COLLATE NOCASE ASC",
                new String[]{busqueda, busqueda, busqueda})) {
            while (c.moveToNext()) {
                lista.add(leerProductoItem(c));
            }
        } finally {
            db.close();
        }
        return lista;
    }

    /** RF-20: busca por código de barras (o, si no coincide, por SKU). Devuelve también productos inactivos. */
    public ProductoItem buscarProductoPorCodigo(String codigo) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(SELECT_PRODUCTO_ITEM
                        + "WHERE p.codigo_barras = ? OR p.sku_codigo = ? COLLATE NOCASE "
                        + "ORDER BY CASE WHEN p.codigo_barras = ? THEN 0 ELSE 1 END LIMIT 1",
                new String[]{codigo, codigo, codigo})) {
            return c.moveToFirst() ? leerProductoItem(c) : null;
        } finally {
            db.close();
        }
    }

    /** Producto con su categoría y stock actual (para refrescar el stock tras un movimiento). */
    public ProductoItem buscarProductoItemPorId(int idProducto) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(SELECT_PRODUCTO_ITEM + "WHERE p.id_producto = ? LIMIT 1",
                new String[]{String.valueOf(idProducto)})) {
            return c.moveToFirst() ? leerProductoItem(c) : null;
        } finally {
            db.close();
        }
    }

    public Producto buscarProductoPorId(int idProducto) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM PRODUCTO WHERE id_producto = ? LIMIT 1",
                new String[]{String.valueOf(idProducto)})) {
            if (!c.moveToFirst()) {
                return null;
            }
            Producto p = new Producto();
            p.idProducto = entero(c, "id_producto");
            p.skuCodigo = texto(c, "sku_codigo");
            p.codigoBarras = texto(c, "codigo_barras");
            p.nombre = texto(c, "nombre");
            p.descripcion = texto(c, "descripcion");
            p.unidadMedida = texto(c, "unidad_medida");
            p.ubicacionFisica = texto(c, "ubicacion_fisica");
            p.idCategoria = entero(c, "id_categoria");
            p.stockActual = entero(c, "stock_actual");
            p.stockMinimo = entero(c, "stock_minimo");
            p.activo = entero(c, "activo");
            return p;
        } finally {
            db.close();
        }
    }

    /**
     * RF-07: modifica los datos del producto. El SKU no cambia y el stock actual tampoco:
     * el stock solo se modifica registrando entradas y salidas (RF-15 / RF-16).
     */
    public void actualizarProducto(int idProducto, String nombre, String descripcion, String codigoBarras,
                                   String unidadMedida, String ubicacionFisica, int stockMinimo,
                                   int idCategoria) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.execSQL("UPDATE PRODUCTO SET nombre = ?, descripcion = ?, codigo_barras = ?, unidad_medida = ?, "
                            + "ubicacion_fisica = ?, stock_minimo = ?, id_categoria = ? WHERE id_producto = ?",
                    new Object[]{nombre, descripcion, vacioANulo(codigoBarras), unidadMedida,
                            vacioANulo(ubicacionFisica), stockMinimo, idCategoria, idProducto});
        } finally {
            db.close();
        }
    }

    /** RF-08: baja lógica (activo = 0). Los movimientos históricos se conservan. */
    public void deshabilitarProducto(int idProducto) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.execSQL("UPDATE PRODUCTO SET activo = 0 WHERE id_producto = ?",
                    new Object[]{idProducto});
        } finally {
            db.close();
        }
    }

    // ---------------------------- MOVIMIENTOS ----------------------------

    /**
     * RF-15 / RF-16 / RF-18: registra una entrada o salida y actualiza el stock en UNA SOLA TRANSACCIÓN.
     * Si algo falla (o el stock quedaría negativo) no se guarda nada y el stock queda igual.
     * Devuelve MOV_OK u otro código MOV_*. En MOV_OK deja el stock nuevo en m.stockResultante.
     */
    public int registrarMovimiento(Movimiento m) {
        if (m.cantidad <= 0) {
            return MOV_ERROR_ALMACENAMIENTO;
        }
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.beginTransaction();
            try {
                int stock;
                int activo;
                try (Cursor c = db.rawQuery("SELECT stock_actual, activo FROM PRODUCTO WHERE id_producto = ?",
                        new String[]{String.valueOf(m.idProducto)})) {
                    if (!c.moveToFirst()) {
                        return MOV_PRODUCTO_NO_ENCONTRADO;
                    }
                    stock = c.getInt(0);
                    activo = c.getInt(1);
                }
                if (activo != 1) {
                    return MOV_PRODUCTO_INACTIVO;
                }

                long nuevoStock = Movimiento.ENTRADA.equals(m.tipo)
                        ? (long) stock + m.cantidad
                        : (long) stock - m.cantidad;
                if (nuevoStock < 0) {
                    return MOV_STOCK_INSUFICIENTE;
                }
                if (nuevoStock > Integer.MAX_VALUE) {
                    return MOV_ERROR_ALMACENAMIENTO;
                }

                db.execSQL("UPDATE PRODUCTO SET stock_actual = ? WHERE id_producto = ?",
                        new Object[]{(int) nuevoStock, m.idProducto});
                db.execSQL("INSERT INTO MOVIMIENTO (id_producto, id_usuario, tipo, cantidad, fecha, id_proveedor, "
                                + "documento, motivo, destinatario, observacion, stock_resultante) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        new Object[]{m.idProducto, m.idUsuario, m.tipo, m.cantidad, m.fecha,
                                m.idProveedor > 0 ? m.idProveedor : null,
                                vacioANulo(m.documento), vacioANulo(m.motivo), vacioANulo(m.destinatario),
                                vacioANulo(m.observacion), (int) nuevoStock});
                db.setTransactionSuccessful();
                m.stockResultante = (int) nuevoStock;
                return MOV_OK;
            } finally {
                db.endTransaction(); // sin setTransactionSuccessful => ROLLBACK
            }
        } catch (SQLException e) {
            return MOV_ERROR_ALMACENAMIENTO;
        } finally {
            db.close();
        }
    }

    /**
     * RF-21 / RF-22: historial de entradas y salidas.
     *
     * @param idProducto 0 = todos los productos
     * @param tipo       Movimiento.ENTRADA, Movimiento.SALIDA o null (ambos)
     * @param texto      filtra por nombre/SKU del producto o nombre/usuario de quien lo registró ("" = sin filtro)
     */
    public List<MovimientoItem> listarMovimientos(int idProducto, String tipo, String texto) {
        List<MovimientoItem> lista = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT m.id_movimiento, m.tipo, m.cantidad, m.fecha, m.documento, m.motivo, m.destinatario, "
                        + "m.observacion, m.stock_resultante, p.sku_codigo, p.nombre AS nombre_producto, "
                        + "p.unidad_medida, u.nombre_completo AS nombre_usuario, pr.razon_social AS razon_proveedor "
                        + "FROM MOVIMIENTO m "
                        + "INNER JOIN PRODUCTO p ON p.id_producto = m.id_producto "
                        + "INNER JOIN USUARIO u ON u.id_usuario = m.id_usuario "
                        + "LEFT JOIN PROVEEDOR pr ON pr.id_proveedor = m.id_proveedor "
                        + "WHERE 1 = 1 ");
        List<String> args = new ArrayList<>();
        if (idProducto > 0) {
            sql.append("AND m.id_producto = ? ");
            args.add(String.valueOf(idProducto));
        }
        if (tipo != null) {
            sql.append("AND m.tipo = ? ");
            args.add(tipo);
        }
        if (texto != null && !texto.isEmpty()) {
            sql.append("AND (p.nombre LIKE '%' || ? || '%' OR p.sku_codigo LIKE '%' || ? || '%' "
                    + "OR u.nombre_completo LIKE '%' || ? || '%' OR u.username LIKE '%' || ? || '%') ");
            for (int i = 0; i < 4; i++) {
                args.add(texto);
            }
        }
        sql.append("ORDER BY m.fecha DESC, m.id_movimiento DESC LIMIT 500");

        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                MovimientoItem m = new MovimientoItem();
                m.idMovimiento = entero(c, "id_movimiento");
                m.tipo = texto(c, "tipo");
                m.cantidad = entero(c, "cantidad");
                m.fecha = texto(c, "fecha");
                m.documento = texto(c, "documento");
                m.motivo = texto(c, "motivo");
                m.destinatario = texto(c, "destinatario");
                m.observacion = texto(c, "observacion");
                m.stockResultante = entero(c, "stock_resultante");
                m.skuCodigo = texto(c, "sku_codigo");
                m.nombreProducto = texto(c, "nombre_producto");
                m.unidadMedida = texto(c, "unidad_medida");
                m.nombreUsuario = texto(c, "nombre_usuario");
                m.razonSocialProveedor = texto(c, "razon_proveedor");
                lista.add(m);
            }
        } finally {
            db.close();
        }
        return lista;
    }

    // ------------------------------ INFORME ------------------------------

    /**
     * RF-19 / RF-22: informe de stock de productos ACTIVOS.
     *
     * @param idCategoria 0 = todas las categorías
     * @param texto       nombre, SKU o código de barras ("" = todos)
     * @param estado      ProductoItem.ESTADO_* o null (todos)
     */
    public List<ProductoItem> generarInformeStock(int idCategoria, String texto, String estado) {
        List<ProductoItem> lista = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_PRODUCTO_ITEM + "WHERE p.activo = 1 ");
        List<String> args = new ArrayList<>();
        if (idCategoria > 0) {
            sql.append("AND p.id_categoria = ? ");
            args.add(String.valueOf(idCategoria));
        }
        if (texto != null && !texto.isEmpty()) {
            // El texto también coincide con el nombre de la categoría (ej: "quesos")
            sql.append("AND (p.nombre LIKE '%' || ? || '%' OR p.sku_codigo LIKE '%' || ? || '%' "
                    + "OR p.codigo_barras LIKE '%' || ? || '%' OR c.nombre_categoria LIKE '%' || ? || '%') ");
            for (int i = 0; i < 4; i++) {
                args.add(texto);
            }
        }
        if (ProductoItem.ESTADO_SIN_STOCK.equals(estado)) {
            sql.append("AND p.stock_actual <= 0 ");
        } else if (ProductoItem.ESTADO_CRITICO.equals(estado)) {
            sql.append("AND p.stock_actual > 0 AND p.stock_actual <= p.stock_minimo ");
        } else if (ProductoItem.ESTADO_NORMAL.equals(estado)) {
            sql.append("AND p.stock_actual > p.stock_minimo AND p.stock_actual > 0 ");
        }
        sql.append("ORDER BY c.nombre_categoria COLLATE NOCASE ASC, p.nombre COLLATE NOCASE ASC");

        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                lista.add(leerProductoItem(c));
            }
        } finally {
            db.close();
        }
        return lista;
    }

    // ------------------------------ EMPRESA ------------------------------

    /** RF-24: datos y coordenadas de la empresa (siempre hay una fila con id 1). */
    public Empresa obtenerEmpresa() {
        Empresa e = new Empresa();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT nombre, direccion, latitud, longitud FROM EMPRESA WHERE id_empresa = 1", null)) {
            if (c.moveToFirst()) {
                e.nombre = c.getString(0);
                e.direccion = c.getString(1);
                e.latitud = c.isNull(2) ? null : c.getDouble(2);
                e.longitud = c.isNull(3) ? null : c.getDouble(3);
            }
        } finally {
            db.close();
        }
        return e;
    }

    public void guardarEmpresa(Empresa e) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.execSQL("INSERT OR REPLACE INTO EMPRESA (id_empresa, nombre, direccion, latitud, longitud) "
                            + "VALUES (1, ?, ?, ?, ?)",
                    new Object[]{e.nombre, e.direccion, e.latitud, e.longitud});
        } finally {
            db.close();
        }
    }
}
