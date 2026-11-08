package com.example.comercializadoraangel;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.comercializadoraangel.data.DBHelper;
import com.example.comercializadoraangel.data.Movimiento;
import com.example.comercializadoraangel.data.ProductoItem;
import com.example.comercializadoraangel.data.Proveedor;
import com.example.comercializadoraangel.util.EscanerCodigo;
import com.example.comercializadoraangel.util.SessionManager;
import com.example.comercializadoraangel.util.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * CU-04 (registrar entrada) y CU-05 (registrar salida): RF-15, RF-16, RF-17 y RF-18.
 * Una sola pantalla que se adapta según EXTRA_TIPO (ENTRADA o SALIDA).
 * Pueden usarla el Administrador y el Operador/Bodeguero.
 */
public class MovimientoActivity extends AppCompatActivity {

    public static final String EXTRA_TIPO = "tipo";
    /** Opcional: producto ya seleccionado (por ejemplo, desde el resultado del escáner). */
    public static final String EXTRA_ID_PRODUCTO = "id_producto";

    private DBHelper dbHelper;
    private SessionManager session;
    private EscanerCodigo escaner;

    private boolean esEntrada;
    private ProductoItem producto; // producto seleccionado (null = ninguno)

    private TextInputLayout tilProducto, tilCantidad, tilMotivo;
    private TextInputEditText etProducto, etCantidad, etDocumento, etMotivo, etDestinatario, etObservacion;
    private MaterialCardView cardProducto;
    private TextView tvProductoNombre, tvProductoDetalle, tvProductoStock;
    private Spinner spProveedor;
    private final List<Proveedor> proveedores = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        session = new SessionManager(this);
        if (!session.haySesion() || !session.puedeRegistrarMovimientos()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_movimiento);
        UiUtils.aplicarInsets(findViewById(R.id.main));

        dbHelper = new DBHelper(this);
        esEntrada = !Movimiento.SALIDA.equals(getIntent().getStringExtra(EXTRA_TIPO));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(esEntrada ? "Registrar entrada" : "Registrar salida");

        tilProducto = findViewById(R.id.tilProducto);
        tilCantidad = findViewById(R.id.tilCantidad);
        tilMotivo = findViewById(R.id.tilMotivo);
        etProducto = findViewById(R.id.etProducto);
        etCantidad = findViewById(R.id.etCantidad);
        etDocumento = findViewById(R.id.etDocumento);
        etMotivo = findViewById(R.id.etMotivo);
        etDestinatario = findViewById(R.id.etDestinatario);
        etObservacion = findViewById(R.id.etObservacion);
        cardProducto = findViewById(R.id.cardProducto);
        tvProductoNombre = findViewById(R.id.tvProductoNombre);
        tvProductoDetalle = findViewById(R.id.tvProductoDetalle);
        tvProductoStock = findViewById(R.id.tvProductoStock);
        spProveedor = findViewById(R.id.spProveedor);
        MaterialButton btnBuscar = findViewById(R.id.btnBuscarProducto);
        MaterialButton btnConfirmar = findViewById(R.id.btnConfirmar);

        findViewById(R.id.llEntrada).setVisibility(esEntrada ? View.VISIBLE : View.GONE);
        findViewById(R.id.llSalida).setVisibility(esEntrada ? View.GONE : View.VISIBLE);
        btnConfirmar.setText(esEntrada ? "Confirmar entrada" : "Confirmar salida");

        cargarProveedores();

        // Selección del producto por nombre, SKU o código de barras (paso 13 / 23)
        escaner = new EscanerCodigo(this, new EscanerCodigo.Listener() {
            @Override
            public void onCodigoLeido(String codigo) {
                etProducto.setText(codigo);
                buscarProducto();
            }

            @Override
            public void onPermisoDenegado() {
                UiUtils.mensaje(findViewById(R.id.main),
                        "Sin permiso de cámara. Escriba el nombre, SKU o código del producto.");
            }
        });
        tilProducto.setEndIconOnClickListener(v -> escaner.escanear());
        btnBuscar.setOnClickListener(v -> buscarProducto());
        etProducto.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                buscarProducto();
                return true;
            }
            return false;
        });

        btnConfirmar.setOnClickListener(v -> confirmar());

        int idPreseleccionado = getIntent().getIntExtra(EXTRA_ID_PRODUCTO, -1);
        if (idPreseleccionado != -1) {
            ProductoItem p = dbHelper.buscarProductoItemPorId(idPreseleccionado);
            if (p != null) {
                seleccionarProducto(p);
            }
        }
    }

    private void cargarProveedores() {
        proveedores.clear();
        proveedores.addAll(dbHelper.listarProveedores());
        List<String> nombres = new ArrayList<>();
        nombres.add("Proveedor (opcional)");
        for (Proveedor p : proveedores) {
            nombres.add(p.razonSocial);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, nombres);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProveedor.setAdapter(adapter);
    }

    // ------------------------ Buscar / seleccionar producto ------------------------

    private void buscarProducto() {
        tilProducto.setError(null);
        final String texto = UiUtils.texto(etProducto);
        if (texto.isEmpty()) {
            tilProducto.setError("Ingrese el nombre, SKU o código de barras");
            return;
        }

        // Coincidencia exacta de código de barras o SKU
        ProductoItem exacto = dbHelper.buscarProductoPorCodigo(texto);
        if (exacto != null) {
            seleccionarProducto(exacto);
            return;
        }

        // Búsqueda por nombre (solo activos)
        final List<ProductoItem> encontrados = dbHelper.buscarProductos(texto);
        if (encontrados.isEmpty()) {
            limpiarProducto();
            tilProducto.setError("Producto no encontrado. Intente buscar nuevamente");
        } else if (encontrados.size() == 1) {
            seleccionarProducto(encontrados.get(0));
        } else {
            String[] opciones = new String[encontrados.size()];
            for (int i = 0; i < opciones.length; i++) {
                ProductoItem p = encontrados.get(i);
                opciones[i] = p.nombre + "  (" + p.skuCodigo + ")";
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Seleccione el producto")
                    .setItems(opciones, (d, which) -> seleccionarProducto(encontrados.get(which)))
                    .setNegativeButton("Cancelar", null)
                    .show();
        }
    }

    private void seleccionarProducto(ProductoItem p) {
        if (p.activo != 1) {
            limpiarProducto();
            tilProducto.setError("El producto \"" + p.nombre + "\" está deshabilitado");
            return;
        }
        tilProducto.setError(null);
        producto = p;
        etProducto.setText(p.nombre);
        mostrarProducto();
    }

    private void limpiarProducto() {
        producto = null;
        cardProducto.setVisibility(View.GONE);
    }

    /** Pasos 14 y 24: muestra el producto con su stock actual (RF-17). */
    private void mostrarProducto() {
        cardProducto.setVisibility(View.VISIBLE);
        tvProductoNombre.setText(producto.nombre);
        String ubicacion = producto.ubicacionFisica == null ? "" : "  ·  " + producto.ubicacionFisica;
        tvProductoDetalle.setText("SKU: " + producto.skuCodigo + "  ·  " + producto.nombreCategoria + ubicacion);

        String estado = producto.estadoStock();
        boolean alerta = !ProductoItem.ESTADO_NORMAL.equals(estado);
        int fondo = MaterialColors.getColor(tvProductoStock,
                alerta ? com.google.android.material.R.attr.colorErrorContainer
                        : com.google.android.material.R.attr.colorSecondaryContainer);
        int texto = MaterialColors.getColor(tvProductoStock,
                alerta ? com.google.android.material.R.attr.colorOnErrorContainer
                        : com.google.android.material.R.attr.colorOnSecondaryContainer);
        tvProductoStock.setBackgroundTintList(ColorStateList.valueOf(fondo));
        tvProductoStock.setTextColor(texto);
        tvProductoStock.setText((esEntrada ? "Stock actual: " : "Stock disponible: ")
                + producto.stockActual + " " + producto.unidadMedida + "  ·  " + estado);
    }

    // ----------------------------- Confirmar -----------------------------

    private void confirmar() {
        tilProducto.setError(null);
        tilCantidad.setError(null);
        tilMotivo.setError(null);

        if (producto == null) {
            tilProducto.setError("Seleccione un producto");
            return;
        }

        // Se relee el producto para validar contra el stock real
        ProductoItem actual = dbHelper.buscarProductoItemPorId(producto.idProducto);
        if (actual == null) {
            limpiarProducto();
            tilProducto.setError("Producto no encontrado. Intente buscar nuevamente");
            return;
        }
        producto = actual;
        mostrarProducto();

        // RF-18: cantidad entera mayor que cero
        int cantidad;
        try {
            cantidad = Integer.parseInt(UiUtils.texto(etCantidad));
        } catch (NumberFormatException e) {
            cantidad = 0;
        }
        boolean valido = true;
        if (cantidad <= 0) {
            tilCantidad.setError("Ingrese una cantidad entera mayor que cero");
            valido = false;
        } else if (!esEntrada && cantidad > producto.stockActual) {
            // RF-18: no se permite stock negativo
            tilCantidad.setError("Stock insuficiente. Disponible: " + producto.stockActual
                    + " " + producto.unidadMedida);
            valido = false;
        }
        if (!esEntrada && UiUtils.texto(etMotivo).isEmpty()) {
            tilMotivo.setError("El motivo de la salida es obligatorio");
            valido = false;
        }
        if (!valido) {
            return;
        }

        Movimiento m = new Movimiento();
        m.idProducto = producto.idProducto;
        m.idUsuario = session.getIdUsuario();
        m.tipo = esEntrada ? Movimiento.ENTRADA : Movimiento.SALIDA;
        m.cantidad = cantidad;
        m.fecha = UiUtils.ahora();
        m.observacion = UiUtils.texto(etObservacion);
        if (esEntrada) {
            int posProveedor = spProveedor.getSelectedItemPosition();
            m.idProveedor = posProveedor > 0 ? proveedores.get(posProveedor - 1).idProveedor : 0;
            m.documento = UiUtils.texto(etDocumento);
        } else {
            m.motivo = UiUtils.texto(etMotivo);
            m.destinatario = UiUtils.texto(etDestinatario);
        }

        // Movimiento + actualización de stock en una sola transacción (DBHelper)
        int resultado = dbHelper.registrarMovimiento(m);
        switch (resultado) {
            case DBHelper.MOV_OK:
                producto.stockActual = m.stockResultante;
                mostrarProducto();
                limpiarFormulario();
                UiUtils.mensaje(findViewById(R.id.main),
                        (esEntrada ? "Entrada registrada" : "Salida registrada")
                                + ". Nuevo stock: " + m.stockResultante + " " + producto.unidadMedida);
                break;
            case DBHelper.MOV_STOCK_INSUFICIENTE:
                tilCantidad.setError("Stock insuficiente. No se registró la salida");
                actualizarStockMostrado();
                break;
            case DBHelper.MOV_PRODUCTO_INACTIVO:
                limpiarProducto();
                tilProducto.setError("El producto está deshabilitado");
                break;
            case DBHelper.MOV_PRODUCTO_NO_ENCONTRADO:
                limpiarProducto();
                tilProducto.setError("Producto no encontrado. Intente buscar nuevamente");
                break;
            default:
                // Error de almacenamiento: la transacción hizo rollback y el stock no cambió
                UiUtils.mensaje(findViewById(R.id.main),
                        "No se pudo guardar el movimiento. El stock permanece sin cambios.");
                actualizarStockMostrado();
                break;
        }
    }

    private void actualizarStockMostrado() {
        ProductoItem p = dbHelper.buscarProductoItemPorId(producto.idProducto);
        if (p != null) {
            producto = p;
            mostrarProducto();
        }
    }

    private void limpiarFormulario() {
        etCantidad.setText("");
        etDocumento.setText("");
        etMotivo.setText("");
        etDestinatario.setText("");
        etObservacion.setText("");
        spProveedor.setSelection(0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
