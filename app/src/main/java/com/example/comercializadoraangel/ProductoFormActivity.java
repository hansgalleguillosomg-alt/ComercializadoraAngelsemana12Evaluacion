package com.example.comercializadoraangel;

import android.database.sqlite.SQLiteConstraintException;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.comercializadoraangel.data.DBHelper;
import com.example.comercializadoraangel.data.Categoria;
import com.example.comercializadoraangel.data.Producto;
import com.example.comercializadoraangel.util.EscanerCodigo;
import com.example.comercializadoraangel.util.SessionManager;
import com.example.comercializadoraangel.util.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/** CU-03: RF-03 (registrar producto) y RF-07 (modificar producto). Solo para el administrador. */
public class ProductoFormActivity extends AppCompatActivity {

    public static final String EXTRA_ID_PRODUCTO = "id_producto";

    private static final String[] UNIDADES = {"Unidad", "Kg", "Gramos", "Litro", "Bolsa", "Caja", "Paquete", "Pieza"};

    private TextInputLayout tilSku, tilNombre, tilCodigoBarras, tilStockActual, tilStockMinimo;
    private TextInputEditText etSku, etNombre, etDescripcion, etCodigoBarras, etStockActual, etStockMinimo, etUbicacion;
    private Spinner spCategoria, spUnidad;
    private ArrayAdapter<String> unidadesAdapter;
    private EscanerCodigo escaner;
    private TextView tvAviso;
    private ArrayAdapter<String> categoriasAdapter;
    private MaterialButton btnGuardar;

    private DBHelper dbHelper;
    private int idProducto = -1;
    private boolean editando = false;

    private final List<Categoria> categorias = new ArrayList<>();
    private Producto productoActual;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        SessionManager session = new SessionManager(this);
        if (!session.haySesion() || !session.esAdministrador()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_producto_form);
        UiUtils.aplicarInsets(findViewById(R.id.main));

        dbHelper = new DBHelper(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tilSku = findViewById(R.id.tilSku);
        tilNombre = findViewById(R.id.tilNombre);
        tilCodigoBarras = findViewById(R.id.tilCodigoBarras);
        tilStockActual = findViewById(R.id.tilStockActual);
        tilStockMinimo = findViewById(R.id.tilStockMinimo);
        etSku = findViewById(R.id.etSku);
        etNombre = findViewById(R.id.etNombre);
        etDescripcion = findViewById(R.id.etDescripcion);
        etCodigoBarras = findViewById(R.id.etCodigoBarras);
        etUbicacion = findViewById(R.id.etUbicacion);
        spUnidad = findViewById(R.id.spUnidad);
        etStockActual = findViewById(R.id.etStockActual);
        etStockMinimo = findViewById(R.id.etStockMinimo);
        spCategoria = findViewById(R.id.spCategoria);
        tvAviso = findViewById(R.id.tvAviso);
        btnGuardar = findViewById(R.id.btnGuardar);

        idProducto = getIntent().getIntExtra(EXTRA_ID_PRODUCTO, -1);
        editando = idProducto != -1;

        // RF-20: el ícono del campo abre la cámara y rellena el código de barras
        escaner = new EscanerCodigo(this, new EscanerCodigo.Listener() {
            @Override
            public void onCodigoLeido(String codigo) {
                etCodigoBarras.setText(codigo);
            }

            @Override
            public void onPermisoDenegado() {
                UiUtils.mensaje(findViewById(R.id.main),
                        "Sin permiso de cámara. Puede escribir el código de barras manualmente.");
            }
        });
        tilCodigoBarras.setEndIconOnClickListener(v -> escaner.escanear());

        unidadesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new ArrayList<>(java.util.Arrays.asList(UNIDADES)));
        unidadesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spUnidad.setAdapter(unidadesAdapter);

        if (editando) {
            getSupportActionBar().setTitle("Modificar producto");
            etSku.setEnabled(false);
            // El stock solo cambia con entradas y salidas (RF-15 / RF-16), no editándolo a mano
            etStockActual.setEnabled(false);
            tilStockActual.setHint("Stock actual (solo cambia con movimientos)");
            tvAviso.setVisibility(View.VISIBLE);
        } else {
            getSupportActionBar().setTitle("Nuevo producto");
        }

        // CU-10: categorías activas para el selector
        categorias.addAll(dbHelper.listarCategoriasActivas());
        List<String> nombres = new ArrayList<>();
        for (Categoria c : categorias) {
            nombres.add(c.nombreCategoria);
        }
        categoriasAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, nombres);
        categoriasAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategoria.setAdapter(categoriasAdapter);

        if (editando) {
            cargarProducto();
        }

        btnGuardar.setOnClickListener(v -> guardar());
    }

    /** RF-07: carga los datos del producto a modificar y selecciona su categoría en el Spinner. */
    private void cargarProducto() {
        final Producto p = dbHelper.buscarProductoPorId(idProducto);
        if (p == null) {
            finish();
            return;
        }
        productoActual = p;
        etSku.setText(p.skuCodigo);
        etNombre.setText(p.nombre);
        etDescripcion.setText(p.descripcion);
        etStockActual.setText(String.valueOf(p.stockActual));
        etStockMinimo.setText(String.valueOf(p.stockMinimo));
        etCodigoBarras.setText(p.codigoBarras);
        etUbicacion.setText(p.ubicacionFisica);

        int posUnidad = unidadesAdapter.getPosition(p.unidadMedida);
        if (posUnidad < 0) {
            unidadesAdapter.add(p.unidadMedida);
            posUnidad = unidadesAdapter.getCount() - 1;
        }
        spUnidad.setSelection(posUnidad);

        boolean encontrada = false;
        for (int i = 0; i < categorias.size(); i++) {
            if (categorias.get(i).idCategoria == p.idCategoria) {
                spCategoria.setSelection(i);
                encontrada = true;
                break;
            }
        }
        // Si la categoría del producto fue deshabilitada, igual se muestra para no cambiarla sin querer
        if (!encontrada) {
            for (Categoria c : dbHelper.listarTodasLasCategorias()) {
                if (c.idCategoria == p.idCategoria) {
                    categorias.add(c);
                    categoriasAdapter.add(c.nombreCategoria + " (deshabilitada)");
                    spCategoria.setSelection(categorias.size() - 1);
                    break;
                }
            }
        }
    }

    private void guardar() {
        tilSku.setError(null);
        tilNombre.setError(null);
        tilCodigoBarras.setError(null);
        tilStockActual.setError(null);
        tilStockMinimo.setError(null);

        final String sku = UiUtils.texto(etSku);
        final String nombre = UiUtils.texto(etNombre);
        final String descripcion = UiUtils.texto(etDescripcion);
        final String codigoBarras = UiUtils.texto(etCodigoBarras);
        final String ubicacion = UiUtils.texto(etUbicacion);
        final String unidad = (String) spUnidad.getSelectedItem();
        final int stockMinimo = parsearEntero(UiUtils.texto(etStockMinimo));
        // En modo edición el stock no se toca (campo deshabilitado)
        final int stockActual = editando ? 0 : parsearEntero(UiUtils.texto(etStockActual));

        boolean valido = true;
        if (!editando && sku.isEmpty()) {
            tilSku.setError("El código SKU es obligatorio");
            valido = false;
        }
        if (nombre.isEmpty()) {
            tilNombre.setError("El nombre es obligatorio");
            valido = false;
        }
        if (!editando && stockActual < 0) {
            tilStockActual.setError("Ingrese un número entero mayor o igual a 0");
            valido = false;
        }
        if (stockMinimo < 0) {
            tilStockMinimo.setError("Ingrese un número entero mayor o igual a 0");
            valido = false;
        }
        int posicion = spCategoria.getSelectedItemPosition();
        if (categorias.isEmpty() || posicion < 0 || posicion >= categorias.size()) {
            UiUtils.mensaje(findViewById(R.id.main),
                    "Primero registre una categoría para poder asignarla al producto");
            valido = false;
        }
        if (!valido) {
            return;
        }

        // CU-03: el código de barras debe ser único cuando se informa
        if (!codigoBarras.isEmpty()
                && dbHelper.contarProductosPorBarras(codigoBarras, editando ? idProducto : 0) > 0) {
            barrasDuplicado();
            return;
        }

        final int idCategoria = categorias.get(posicion).idCategoria;
        if (editando) {
            // RF-07
            try {
                dbHelper.actualizarProducto(idProducto, nombre, descripcion, codigoBarras, unidad,
                        ubicacion, stockMinimo, idCategoria);
            } catch (SQLiteConstraintException e) {
                barrasDuplicado();
                return;
            }
            Toast.makeText(this, "Producto actualizado", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // RF-03: el SKU debe ser único
        if (dbHelper.contarProductosPorSku(sku) > 0) {
            skuDuplicado();
            return;
        }

        Producto p = new Producto();
        p.skuCodigo = sku;
        p.nombre = nombre;
        p.descripcion = descripcion;
        p.codigoBarras = codigoBarras.isEmpty() ? null : codigoBarras;
        p.unidadMedida = unidad;
        p.ubicacionFisica = ubicacion.isEmpty() ? null : ubicacion;
        p.idCategoria = idCategoria;
        p.stockActual = stockActual;
        p.stockMinimo = stockMinimo;
        p.activo = 1;

        try {
            dbHelper.insertarProducto(p);
        } catch (SQLiteConstraintException e) {
            // La BD rechazó el INSERT: se informa cuál código ya existe
            if (dbHelper.contarProductosPorSku(sku) > 0) {
                skuDuplicado();
            } else {
                barrasDuplicado();
            }
            return;
        }

        Toast.makeText(this, "Producto registrado", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void skuDuplicado() {
        tilSku.setError("El código SKU ya existe");
    }

    private void barrasDuplicado() {
        tilCodigoBarras.setError("Este código de barras ya está asociado a otro producto");
    }

    /** Devuelve el entero (>= 0) o -1 si el texto no es válido. */
    private int parsearEntero(String texto) {
        try {
            int valor = Integer.parseInt(texto);
            return valor >= 0 ? valor : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
