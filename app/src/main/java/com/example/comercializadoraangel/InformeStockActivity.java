package com.example.comercializadoraangel;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.comercializadoraangel.adapter.FilaAdapter;
import com.example.comercializadoraangel.data.Categoria;
import com.example.comercializadoraangel.data.DBHelper;
import com.example.comercializadoraangel.data.ProductoItem;
import com.example.comercializadoraangel.util.InformePdf;
import com.example.comercializadoraangel.util.SessionManager;
import com.example.comercializadoraangel.util.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * CU-06: informe de stock (RF-19, RF-22) con exportación a PDF (RF-23).
 * Filtros: categoría, producto y estado del stock. Lo pueden usar todos los roles (solo consulta).
 */
public class InformeStockActivity extends AppCompatActivity {

    private static final String[] ESTADOS = {
            "Todos los estados",
            ProductoItem.ESTADO_SIN_STOCK,
            ProductoItem.ESTADO_CRITICO,
            ProductoItem.ESTADO_NORMAL
    };

    private DBHelper dbHelper;
    private SessionManager session;
    private FilaAdapter adapter;
    private RecyclerView rvInforme;
    private TextView tvVacio, tvResumen;
    private Spinner spCategoria, spEstado;
    private TextInputEditText etProductoFiltro;
    private final List<Categoria> categorias = new ArrayList<>();

    /** Resultado del último informe generado (es lo que se exporta). */
    private List<ProductoItem> ultimoInforme = new ArrayList<>();
    private String ultimosFiltros = "Sin filtros";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        session = new SessionManager(this);
        if (!session.haySesion()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_informe);
        UiUtils.aplicarInsets(findViewById(R.id.main));

        dbHelper = new DBHelper(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Informe de stock");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tvVacio = findViewById(R.id.tvVacio);
        tvResumen = findViewById(R.id.tvResumen);
        rvInforme = findViewById(R.id.rvInforme);
        spCategoria = findViewById(R.id.spCategoria);
        spEstado = findViewById(R.id.spEstado);
        etProductoFiltro = findViewById(R.id.etProductoFiltro);
        MaterialButton btnGenerar = findViewById(R.id.btnGenerar);

        adapter = new FilaAdapter();
        rvInforme.setLayoutManager(new LinearLayoutManager(this));
        rvInforme.setAdapter(adapter);

        // Filtro de categoría (la primera opción es "todas")
        categorias.addAll(dbHelper.listarTodasLasCategorias());
        List<String> nombres = new ArrayList<>();
        nombres.add("Todas las categorías");
        for (Categoria c : categorias) {
            nombres.add(c.nombreCategoria);
        }
        ArrayAdapter<String> adapterCat = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, nombres);
        adapterCat.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategoria.setAdapter(adapterCat);

        ArrayAdapter<String> adapterEstado = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ESTADOS);
        adapterEstado.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spEstado.setAdapter(adapterEstado);

        btnGenerar.setOnClickListener(v -> generarInforme());
        etProductoFiltro.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                generarInforme();
                return true;
            }
            return false;
        });

        // El informe se actualiza solo al cambiar cualquier filtro (el botón "Generar" también funciona)
        AdapterView.OnItemSelectedListener alCambiarFiltro = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                generarInforme();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        spCategoria.setOnItemSelectedListener(alCambiarFiltro);
        spEstado.setOnItemSelectedListener(alCambiarFiltro);
        etProductoFiltro.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                generarInforme();
            }
        });

        // Sin filtros se muestran todos los productos activos
        generarInforme();
    }

    /** Pasos 36 a 38: consulta existencias, determina el estado de cada producto y presenta el informe. */
    private void generarInforme() {
        int posCat = spCategoria.getSelectedItemPosition();
        int idCategoria = posCat > 0 ? categorias.get(posCat - 1).idCategoria : 0;
        String texto = UiUtils.texto(etProductoFiltro);
        int posEstado = spEstado.getSelectedItemPosition();
        String estado = posEstado > 0 ? ESTADOS[posEstado] : null;

        ultimoInforme = dbHelper.generarInformeStock(idCategoria, texto, estado);

        List<String> partes = new ArrayList<>();
        if (idCategoria > 0) {
            partes.add("Categoría: " + categorias.get(posCat - 1).nombreCategoria);
        }
        if (!texto.isEmpty()) {
            partes.add("Producto: \"" + texto + "\"");
        }
        if (estado != null) {
            partes.add("Estado: " + estado);
        }
        ultimosFiltros = partes.isEmpty() ? "Sin filtros (todos los productos activos)" : String.join("  |  ", partes);

        int sinStock = 0;
        int criticos = 0;
        List<FilaAdapter.Fila> filas = new ArrayList<>();
        for (ProductoItem p : ultimoInforme) {
            String est = p.estadoStock();
            if (ProductoItem.ESTADO_SIN_STOCK.equals(est)) {
                sinStock++;
            } else if (ProductoItem.ESTADO_CRITICO.equals(est)) {
                criticos++;
            }
            filas.add(new FilaAdapter.Fila(
                    p.nombre,
                    "SKU: " + p.skuCodigo + "  ·  " + p.nombreCategoria
                            + "\nStock: " + p.stockActual + " " + p.unidadMedida + "  (mín. " + p.stockMinimo + ")",
                    est));
        }
        adapter.setFilas(filas);

        boolean vacio = filas.isEmpty();
        tvVacio.setVisibility(vacio ? View.VISIBLE : View.GONE);
        rvInforme.setVisibility(vacio ? View.GONE : View.VISIBLE);
        tvResumen.setVisibility(vacio ? View.GONE : View.VISIBLE);
        tvResumen.setText(ultimoInforme.size() + " productos  ·  " + sinStock + " sin stock  ·  "
                + criticos + " con stock crítico");
    }

    // ----------------------- RF-23: exportar a PDF -----------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_informe, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_exportar_pdf) {
            exportarPdf();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportarPdf() {
        if (ultimoInforme.isEmpty()) {
            UiUtils.mensaje(findViewById(R.id.main), "No hay datos para exportar");
            return;
        }
        try {
            File archivo = InformePdf.generar(this, ultimoInforme, ultimosFiltros, session.getNombreCompleto());
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", archivo);
            Intent enviar = new Intent(Intent.ACTION_SEND);
            enviar.setType("application/pdf");
            enviar.putExtra(Intent.EXTRA_STREAM, uri);
            enviar.putExtra(Intent.EXTRA_SUBJECT, "Informe de stock");
            enviar.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(enviar, "Compartir informe de stock"));
        } catch (Exception e) {
            UiUtils.mensaje(findViewById(R.id.main), "No se pudo generar el PDF");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
