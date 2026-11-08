package com.example.comercializadoraangel;

import android.database.sqlite.SQLiteConstraintException;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.comercializadoraangel.adapter.FilaAdapter;
import com.example.comercializadoraangel.data.DBHelper;
import com.example.comercializadoraangel.data.Categoria;
import com.example.comercializadoraangel.util.SessionManager;
import com.example.comercializadoraangel.util.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/** CU-09 (registrar categoría) y CU-10 (consultar categorías activas). Administrador y Operador. */
public class CategoriasActivity extends AppCompatActivity {

    private DBHelper dbHelper;
    private FilaAdapter adapter;
    private SessionManager session;
    private List<Categoria> categoriasMostradas = new ArrayList<>();
    private TextView tvVacio;
    private RecyclerView rvCategorias;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        session = new SessionManager(this);
        if (!session.haySesion()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_categorias);
        UiUtils.aplicarInsets(findViewById(R.id.main));

        dbHelper = new DBHelper(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Categorías");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tvVacio = findViewById(R.id.tvVacio);
        rvCategorias = findViewById(R.id.rvCategorias);
        ExtendedFloatingActionButton fab = findViewById(R.id.fabNuevaCategoria);

        adapter = new FilaAdapter();
        rvCategorias.setLayoutManager(new LinearLayoutManager(this));
        rvCategorias.setAdapter(adapter);

        // El administrador puede habilitar o deshabilitar categorías tocándolas
        if (session.esAdministrador()) {
            getSupportActionBar().setSubtitle("Toca una categoría para habilitarla o deshabilitarla");
            adapter.setOnFilaClick(posicion -> {
                if (posicion >= 0 && posicion < categoriasMostradas.size()) {
                    confirmarCambioEstado(categoriasMostradas.get(posicion));
                }
            });
        }

        cargarCategorias();

        fab.setOnClickListener(v -> mostrarDialogoNuevaCategoria());
    }

    /** Habilita o deshabilita una categoría (solo Administrador). */
    private void confirmarCambioEstado(final Categoria c) {
        final boolean deshabilitar = c.activo == 1;
        new MaterialAlertDialogBuilder(this)
                .setTitle(deshabilitar ? "Deshabilitar categoría" : "Habilitar categoría")
                .setMessage(deshabilitar
                        ? "¿Desea deshabilitar \"" + c.nombreCategoria + "\"? No aparecerá al registrar productos nuevos."
                        : "¿Desea habilitar \"" + c.nombreCategoria + "\"?")
                .setPositiveButton(deshabilitar ? "Deshabilitar" : "Habilitar", (dialog, which) -> {
                    dbHelper.cambiarEstadoCategoria(c.idCategoria, deshabilitar ? 0 : 1);
                    cargarCategorias();
                    UiUtils.mensaje(findViewById(R.id.main),
                            deshabilitar ? "Categoría deshabilitada" : "Categoría habilitada");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** CU-10: Operador ve solo las activas; el Administrador ve todas para poder habilitarlas. */
    private void cargarCategorias() {
        categoriasMostradas = session.esAdministrador()
                ? dbHelper.listarTodasLasCategorias()
                : dbHelper.listarCategoriasActivas();
        List<FilaAdapter.Fila> filas = new ArrayList<>();
        for (Categoria c : categoriasMostradas) {
            String desc = (c.descripcion == null || c.descripcion.isEmpty())
                    ? "Sin descripción" : c.descripcion;
            filas.add(new FilaAdapter.Fila(c.nombreCategoria, desc, c.activo == 1 ? "" : "Deshabilitada"));
        }
        adapter.setFilas(filas);
        tvVacio.setVisibility(filas.isEmpty() ? View.VISIBLE : View.GONE);
        rvCategorias.setVisibility(filas.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** CU-09: formulario de captura en un diálogo. */
    private void mostrarDialogoNuevaCategoria() {
        View vista = getLayoutInflater().inflate(R.layout.dialog_categoria, null);
        final TextInputLayout tilNombre = vista.findViewById(R.id.tilNombreCategoria);
        final TextInputEditText etNombre = vista.findViewById(R.id.etNombreCategoria);
        final TextInputEditText etDescripcion = vista.findViewById(R.id.etDescripcionCategoria);

        final AlertDialog dialogo = new MaterialAlertDialogBuilder(this)
                .setTitle("Nueva categoría")
                .setView(vista)
                .setPositiveButton("Registrar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        // Se asigna el click después de show() para que el diálogo no se cierre si hay errores
        dialogo.setOnShowListener(d -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            tilNombre.setError(null);
            final String nombre = UiUtils.texto(etNombre);
            final String descripcion = UiUtils.texto(etDescripcion);

            if (nombre.isEmpty()) {
                tilNombre.setError("El nombre de la categoría es obligatorio");
                return;
            }

            if (dbHelper.contarCategoriasPorNombre(nombre) > 0) {
                tilNombre.setError("Ya existe una categoría registrada con ese nombre");
                return;
            }

            Categoria c = new Categoria();
            c.nombreCategoria = nombre;
            c.descripcion = descripcion.isEmpty() ? null : descripcion;
            c.activo = 1;

            try {
                dbHelper.insertarCategoria(c);
            } catch (SQLiteConstraintException e) {
                tilNombre.setError("Ya existe una categoría registrada con ese nombre");
                return;
            }

            dialogo.dismiss();
            cargarCategorias();
            UiUtils.mensaje(findViewById(R.id.main), "Categoría registrada correctamente");
        }));

        dialogo.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
