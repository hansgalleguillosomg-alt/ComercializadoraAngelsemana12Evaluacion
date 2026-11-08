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
import com.example.comercializadoraangel.data.Proveedor;
import com.example.comercializadoraangel.util.SessionManager;
import com.example.comercializadoraangel.util.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/** Gestión de proveedores (soporte a entradas): registrar y listar. Solo administrador. */
public class ProveedoresActivity extends AppCompatActivity {

    private DBHelper dbHelper;
    private FilaAdapter adapter;
    private TextView tvVacio;
    private RecyclerView rvProveedores;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        SessionManager session = new SessionManager(this);
        if (!session.haySesion() || !session.esAdministrador()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_proveedores);
        UiUtils.aplicarInsets(findViewById(R.id.main));

        dbHelper = new DBHelper(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Proveedores");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tvVacio = findViewById(R.id.tvVacio);
        rvProveedores = findViewById(R.id.rvProveedores);
        ExtendedFloatingActionButton fab = findViewById(R.id.fabNuevoProveedor);

        adapter = new FilaAdapter();
        rvProveedores.setLayoutManager(new LinearLayoutManager(this));
        rvProveedores.setAdapter(adapter);

        cargarProveedores();

        fab.setOnClickListener(v -> mostrarDialogoNuevoProveedor());
    }

    private void cargarProveedores() {
        List<FilaAdapter.Fila> filas = new ArrayList<>();
        for (Proveedor p : dbHelper.listarProveedores()) {
            filas.add(new FilaAdapter.Fila(p.razonSocial,
                    "RUT: " + p.rutEmpresa + "  ·  Tel: " + p.telefonoContacto, ""));
        }
        adapter.setFilas(filas);
        tvVacio.setVisibility(filas.isEmpty() ? View.VISIBLE : View.GONE);
        rvProveedores.setVisibility(filas.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void mostrarDialogoNuevoProveedor() {
        View vista = getLayoutInflater().inflate(R.layout.dialog_proveedor, null);
        final TextInputLayout tilRut = vista.findViewById(R.id.tilRut);
        final TextInputLayout tilRazon = vista.findViewById(R.id.tilRazonSocial);
        final TextInputLayout tilTelefono = vista.findViewById(R.id.tilTelefono);
        final TextInputEditText etRut = vista.findViewById(R.id.etRut);
        final TextInputEditText etRazon = vista.findViewById(R.id.etRazonSocial);
        final TextInputEditText etTelefono = vista.findViewById(R.id.etTelefono);

        final AlertDialog dialogo = new MaterialAlertDialogBuilder(this)
                .setTitle("Nuevo proveedor")
                .setView(vista)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialogo.setOnShowListener(d -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            tilRut.setError(null);
            tilRazon.setError(null);
            tilTelefono.setError(null);

            final String rut = UiUtils.texto(etRut);
            final String razon = UiUtils.texto(etRazon);
            final String telefono = UiUtils.texto(etTelefono);

            boolean valido = true;
            if (rut.isEmpty()) {
                tilRut.setError("El RUT es obligatorio");
                valido = false;
            }
            if (razon.isEmpty()) {
                tilRazon.setError("La razón social es obligatoria");
                valido = false;
            }
            if (telefono.isEmpty()) {
                tilTelefono.setError("El número de contacto es obligatorio");
                valido = false;
            }
            if (!valido) {
                return;
            }

            if (dbHelper.contarProveedoresPorRut(rut) > 0) {
                tilRut.setError("Ya existe un proveedor con ese RUT");
                return;
            }

            Proveedor p = new Proveedor();
            p.rutEmpresa = rut;
            p.razonSocial = razon;
            p.telefonoContacto = telefono;

            try {
                dbHelper.insertarProveedor(p);
            } catch (SQLiteConstraintException e) {
                tilRut.setError("Ya existe un proveedor con ese RUT");
                return;
            }

            dialogo.dismiss();
            cargarProveedores();
            UiUtils.mensaje(findViewById(R.id.main), "Proveedor registrado correctamente");
        }));

        dialogo.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
