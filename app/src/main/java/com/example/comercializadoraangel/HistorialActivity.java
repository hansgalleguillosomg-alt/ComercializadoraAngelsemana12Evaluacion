package com.example.comercializadoraangel;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.comercializadoraangel.adapter.FilaAdapter;
import com.example.comercializadoraangel.data.DBHelper;
import com.example.comercializadoraangel.data.Movimiento;
import com.example.comercializadoraangel.data.MovimientoItem;
import com.example.comercializadoraangel.data.ProductoItem;
import com.example.comercializadoraangel.util.SessionManager;
import com.example.comercializadoraangel.util.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * RF-21: historial de entradas y salidas, con el producto, el usuario y la fecha.
 * Filtros: tipo (entradas / salidas) y texto (producto, SKU o usuario).
 * Si se abre con EXTRA_ID_PRODUCTO muestra solo los movimientos de ese producto.
 * Lo pueden ver todos los roles (es solo consulta).
 */
public class HistorialActivity extends AppCompatActivity {

    public static final String EXTRA_ID_PRODUCTO = "id_producto";

    private DBHelper dbHelper;
    private FilaAdapter adapter;
    private TextView tvVacio;
    private RecyclerView rvHistorial;
    private TextInputEditText etBuscar;
    private ChipGroup chipsTipo;
    private int idProducto = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        SessionManager session = new SessionManager(this);
        if (!session.haySesion()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_historial);
        UiUtils.aplicarInsets(findViewById(R.id.main));

        dbHelper = new DBHelper(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Historial de movimientos");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        idProducto = getIntent().getIntExtra(EXTRA_ID_PRODUCTO, 0);
        if (idProducto > 0) {
            ProductoItem p = dbHelper.buscarProductoItemPorId(idProducto);
            if (p != null) {
                getSupportActionBar().setSubtitle(p.nombre + " (" + p.skuCodigo + ")");
            }
        }

        tvVacio = findViewById(R.id.tvVacio);
        rvHistorial = findViewById(R.id.rvHistorial);
        etBuscar = findViewById(R.id.etBuscarHistorial);
        chipsTipo = findViewById(R.id.chipsTipo);

        adapter = new FilaAdapter();
        rvHistorial.setLayoutManager(new LinearLayoutManager(this));
        rvHistorial.setAdapter(adapter);

        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                cargar();
            }
        });
        chipsTipo.setOnCheckedStateChangeListener((group, checkedIds) -> cargar());

        cargar();
    }

    private void cargar() {
        String tipo = null;
        int chip = chipsTipo.getCheckedChipId();
        if (chip == R.id.chipEntradas) {
            tipo = Movimiento.ENTRADA;
        } else if (chip == R.id.chipSalidas) {
            tipo = Movimiento.SALIDA;
        }

        List<FilaAdapter.Fila> filas = new ArrayList<>();
        for (MovimientoItem m : dbHelper.listarMovimientos(idProducto, tipo, UiUtils.texto(etBuscar))) {
            boolean entrada = Movimiento.ENTRADA.equals(m.tipo);

            StringBuilder detalle = new StringBuilder();
            detalle.append(UiUtils.fechaLegible(m.fecha)).append("  ·  ").append(m.nombreUsuario);
            if (entrada) {
                agregar(detalle, "Proveedor", m.razonSocialProveedor);
                agregar(detalle, "Documento", m.documento);
            } else {
                agregar(detalle, "Motivo", m.motivo);
                agregar(detalle, "Destinatario", m.destinatario);
            }
            agregar(detalle, "Obs.", m.observacion);
            detalle.append("\nStock resultante: ").append(m.stockResultante).append(" ").append(m.unidadMedida);

            filas.add(new FilaAdapter.Fila(
                    (entrada ? "Entrada" : "Salida") + " · " + m.nombreProducto,
                    detalle.toString(),
                    (entrada ? "+" : "−") + m.cantidad));
        }
        adapter.setFilas(filas);
        tvVacio.setVisibility(filas.isEmpty() ? View.VISIBLE : View.GONE);
        rvHistorial.setVisibility(filas.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static void agregar(StringBuilder sb, String etiqueta, String valor) {
        if (valor != null && !valor.isEmpty()) {
            sb.append("\n").append(etiqueta).append(": ").append(valor);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
