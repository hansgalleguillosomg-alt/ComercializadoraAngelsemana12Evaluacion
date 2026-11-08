package com.example.comercializadoraangel;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.comercializadoraangel.adapter.ProductoAdapter;
import com.example.comercializadoraangel.data.DBHelper;
import com.example.comercializadoraangel.data.Movimiento;
import com.example.comercializadoraangel.data.ProductoItem;
import com.example.comercializadoraangel.util.EscanerCodigo;
import com.example.comercializadoraangel.util.ProductoDetalleDialog;
import com.example.comercializadoraangel.util.SessionManager;
import com.example.comercializadoraangel.util.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/** Pantalla principal: catálogo de productos con búsqueda y escáner (RF-04, RF-05, RF-07, RF-08, RF-17, RF-20). */
public class MainActivity extends AppCompatActivity {

    private SessionManager session;
    private DBHelper dbHelper;
    private ProductoAdapter adapter;
    private RecyclerView rvProductos;
    private TextView tvVacio;
    private TextInputEditText etBuscar;
    private EscanerCodigo escaner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        session = new SessionManager(this);
        if (!session.haySesion()) {
            volverAlLogin();
            return;
        }

        setContentView(R.layout.activity_main);
        UiUtils.aplicarInsets(findViewById(R.id.main));

        dbHelper = new DBHelper(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Catálogo de productos");
        getSupportActionBar().setSubtitle(session.getNombreCompleto() + " · " + session.getNombreRol());

        rvProductos = findViewById(R.id.rvProductos);
        tvVacio = findViewById(R.id.tvVacio);
        etBuscar = findViewById(R.id.etBuscar);
        ExtendedFloatingActionButton fab = findViewById(R.id.fabNuevoProducto);

        final boolean esAdmin = session.esAdministrador();

        adapter = new ProductoAdapter(esAdmin, new ProductoAdapter.Acciones() {
            @Override
            public void onEditar(ProductoItem producto) {
                Intent i = new Intent(MainActivity.this, ProductoFormActivity.class);
                i.putExtra(ProductoFormActivity.EXTRA_ID_PRODUCTO, producto.idProducto);
                startActivity(i);
            }

            @Override
            public void onDeshabilitar(ProductoItem producto) {
                confirmarDeshabilitar(producto);
            }

            @Override
            public void onDetalle(ProductoItem producto) {
                ProductoDetalleDialog.mostrar(MainActivity.this, producto, session.puedeRegistrarMovimientos());
            }
        });
        rvProductos.setLayoutManager(new LinearLayoutManager(this));
        rvProductos.setAdapter(adapter);

        // RF-03: solo el administrador registra productos
        if (esAdmin) {
            fab.setOnClickListener(v -> startActivity(new Intent(this, ProductoFormActivity.class)));
        } else {
            fab.setVisibility(View.GONE);
        }

        // RF-05: búsqueda por nombre o SKU mientras se escribe
        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                cargarProductos(s.toString().trim());
            }
        });

        // CU-07: lector de códigos de barras (se registra aquí, antes de que la pantalla esté visible)
        escaner = new EscanerCodigo(this, new EscanerCodigo.Listener() {
            @Override
            public void onCodigoLeido(String codigo) {
                mostrarResultadoEscaneo(codigo);
            }

            @Override
            public void onPermisoDenegado() {
                // Flujo alterno: permiso denegado -> se ofrece la búsqueda manual
                UiUtils.mensaje(findViewById(R.id.main),
                        "Sin permiso de cámara. Use la búsqueda manual por nombre, SKU o código.");
                etBuscar.requestFocus();
            }
        });

        cargarProductos("");
    }

    /** CU-07 pasos 45 a 47: consulta el producto asociado al código leído y muestra su ficha. */
    private void mostrarResultadoEscaneo(String codigo) {
        ProductoItem p = dbHelper.buscarProductoPorCodigo(codigo);
        if (p == null) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Producto no encontrado")
                    .setMessage("No hay ningún producto asociado al código \"" + codigo + "\".")
                    .setPositiveButton("Escanear otro", (d, w) -> escaner.escanear())
                    .setNegativeButton("Cerrar", null)
                    .show();
        } else if (p.activo != 1) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Producto deshabilitado")
                    .setMessage("\"" + p.nombre + "\" (SKU " + p.skuCodigo + ") está deshabilitado.")
                    .setPositiveButton("Escanear otro", (d, w) -> escaner.escanear())
                    .setNegativeButton("Cerrar", null)
                    .show();
        } else {
            ProductoDetalleDialog.mostrar(this, p, session.puedeRegistrarMovimientos());
        }
    }

    /** Vuelve a consultar el catálogo al volver a la pantalla (por ejemplo, tras editar un producto). */
    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            cargarProductos(UiUtils.texto(etBuscar));
        }
    }

    /** RF-04 / RF-05: lista los productos activos que coinciden con el texto buscado. */
    private void cargarProductos(String texto) {
        List<ProductoItem> lista = dbHelper.buscarProductos(texto);
        adapter.setProductos(lista);
        boolean vacio = lista.isEmpty();
        tvVacio.setVisibility(vacio ? View.VISIBLE : View.GONE);
        rvProductos.setVisibility(vacio ? View.GONE : View.VISIBLE);
    }

    /** RF-08: baja lógica (activo = 0). */
    private void confirmarDeshabilitar(ProductoItem producto) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Deshabilitar producto")
                .setMessage("¿Desea deshabilitar \"" + producto.nombre + "\"? "
                        + "El historial se conserva y dejará de aparecer en el catálogo.")
                .setPositiveButton("Deshabilitar", (dialog, which) -> {
                    dbHelper.deshabilitarProducto(producto.idProducto);
                    cargarProductos(UiUtils.texto(etBuscar));
                    UiUtils.mensaje(findViewById(R.id.main), "Producto deshabilitado");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------------------- Menú ----------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        if (!session.esAdministrador()) {
            menu.findItem(R.id.action_proveedores).setVisible(false);
            menu.findItem(R.id.action_usuarios).setVisible(false);
        }
        // El rol Consulta no registra entradas ni salidas
        if (!session.puedeRegistrarMovimientos()) {
            menu.findItem(R.id.action_entrada).setVisible(false);
            menu.findItem(R.id.action_salida).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_escanear) {
            escaner.escanear();
            return true;
        } else if (id == R.id.action_entrada) {
            abrirMovimiento(Movimiento.ENTRADA);
            return true;
        } else if (id == R.id.action_salida) {
            abrirMovimiento(Movimiento.SALIDA);
            return true;
        } else if (id == R.id.action_historial) {
            startActivity(new Intent(this, HistorialActivity.class));
            return true;
        } else if (id == R.id.action_informe) {
            startActivity(new Intent(this, InformeStockActivity.class));
            return true;
        } else if (id == R.id.action_ubicacion) {
            startActivity(new Intent(this, UbicacionActivity.class));
            return true;
        } else if (id == R.id.action_categorias) {
            startActivity(new Intent(this, CategoriasActivity.class));
            return true;
        } else if (id == R.id.action_proveedores) {
            startActivity(new Intent(this, ProveedoresActivity.class));
            return true;
        } else if (id == R.id.action_usuarios) {
            startActivity(new Intent(this, UsuariosActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            confirmarCierreSesion();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void abrirMovimiento(String tipo) {
        Intent i = new Intent(this, MovimientoActivity.class);
        i.putExtra(MovimientoActivity.EXTRA_TIPO, tipo);
        startActivity(i);
    }

    // ---------------------- CU-02: Cerrar sesión ----------------------

    private void confirmarCierreSesion() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Desea cerrar su sesión?")
                .setPositiveButton("Cerrar sesión", (dialog, which) -> {
                    session.cerrarSesion();
                    volverAlLogin();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Limpia la pila de navegación para que el botón Atrás no abra pantallas protegidas. */
    private void volverAlLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}
