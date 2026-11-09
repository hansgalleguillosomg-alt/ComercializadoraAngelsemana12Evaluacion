package com.example.comercializadoraangel;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
        MaterialButton btnExportar = findViewById(R.id.btnExportar);

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
        btnExportar.setOnClickListener(v -> exportarPdf());
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

    /** Genera el PDF, guarda una copia en Descargas y ofrece compartirlo. */
    private void exportarPdf() {
        if (ultimoInforme.isEmpty()) {
            UiUtils.mensaje(findViewById(R.id.main), "No hay datos para exportar");
            return;
        }
        try {
            File archivo = InformePdf.generar(this, ultimoInforme, ultimosFiltros, session.getNombreCompleto());
            boolean enDescargas = guardarEnDescargas(archivo);
            String texto = enDescargas
                    ? "PDF guardado en Descargas: " + archivo.getName()
                    : "PDF generado, pero no se pudo guardar en Descargas";
            Snackbar.make(findViewById(R.id.main), texto, Snackbar.LENGTH_LONG)
                    .setDuration(8000)
                    .setAction("Compartir", v -> compartirPdf(archivo))
                    .show();
        } catch (Exception e) {
            UiUtils.mensaje(findViewById(R.id.main), "No se pudo generar el PDF");
        }
    }

    /**
     * Copia el PDF a la carpeta Descargas del teléfono (MediaStore, sin permisos: minSdk 30).
     *
     * @return true si la copia quedó guardada
     */
    private boolean guardarEnDescargas(File origen) {
        ContentValues valores = new ContentValues();
        valores.put(MediaStore.Downloads.DISPLAY_NAME, origen.getName());
        valores.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
        valores.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        Uri destino = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores);
        if (destino == null) {
            return false;
        }
        try (InputStream entrada = new FileInputStream(origen);
             OutputStream salida = getContentResolver().openOutputStream(destino)) {
            if (salida == null) {
                return false;
            }
            byte[] buffer = new byte[8192];
            int leidos;
            while ((leidos = entrada.read(buffer)) > 0) {
                salida.write(buffer, 0, leidos);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Abre el selector de Android para enviar el PDF por correo, Bluetooth, Drive, etc. */
    private void compartirPdf(File archivo) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", archivo);
            Intent enviar = new Intent(Intent.ACTION_SEND);
            enviar.setType("application/pdf");
            enviar.putExtra(Intent.EXTRA_STREAM, uri);
            enviar.putExtra(Intent.EXTRA_SUBJECT, "Informe de stock");
            enviar.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(enviar, "Compartir informe de stock"));
        } catch (Exception e) {
            UiUtils.mensaje(findViewById(R.id.main), "No se pudo compartir el PDF");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
