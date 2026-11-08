package com.example.comercializadoraangel;

import android.database.sqlite.SQLiteConstraintException;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.comercializadoraangel.adapter.FilaAdapter;
import com.example.comercializadoraangel.data.DBHelper;
import com.example.comercializadoraangel.data.Rol;
import com.example.comercializadoraangel.data.Usuario;
import com.example.comercializadoraangel.data.UsuarioItem;
import com.example.comercializadoraangel.util.PasswordUtils;
import com.example.comercializadoraangel.util.SessionManager;
import com.example.comercializadoraangel.util.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/** CU-27 / RF-27: administrar (registrar y listar) usuarios y roles. Solo administrador. */
public class UsuariosActivity extends AppCompatActivity {

    private DBHelper dbHelper;
    private FilaAdapter adapter;
    private ExtendedFloatingActionButton fab;
    private SessionManager session;

    private boolean modoUsuarios = true;
    private List<UsuarioItem> usuarios = new ArrayList<>();
    private List<Rol> roles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        session = new SessionManager(this);
        if (!session.haySesion() || !session.esAdministrador()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_usuarios);
        UiUtils.aplicarInsets(findViewById(R.id.main));

        dbHelper = new DBHelper(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Gestión de usuarios");
        getSupportActionBar().setSubtitle("Toca un usuario para activarlo o desactivarlo");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        RecyclerView rvLista = findViewById(R.id.rvLista);
        fab = findViewById(R.id.fabNuevo);

        adapter = new FilaAdapter();
        rvLista.setLayoutManager(new LinearLayoutManager(this));
        rvLista.setAdapter(adapter);

        // Activar / desactivar usuario al tocar su fila (solo en la pestaña Usuarios)
        adapter.setOnFilaClick(posicion -> {
            if (modoUsuarios && posicion >= 0 && posicion < usuarios.size()) {
                confirmarCambioEstado(usuarios.get(posicion));
            }
        });

        // CU-27 paso 2: listado de usuarios (con el nombre del rol) y de roles
        recargarDatos();

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                modoUsuarios = tab.getPosition() == 0;
                fab.setText(modoUsuarios ? "Nuevo usuario" : "Nuevo rol");
                refrescarLista();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        fab.setOnClickListener(v -> {
            if (modoUsuarios) {
                mostrarDialogoNuevoUsuario();
            } else {
                mostrarDialogoNuevoRol();
            }
        });
    }

    /** Vuelve a consultar usuarios y roles en la base de datos y refresca la lista. */
    private void recargarDatos() {
        usuarios = dbHelper.listarUsuariosConRol();
        roles = dbHelper.listarRoles();
        refrescarLista();
    }

    private void refrescarLista() {
        List<FilaAdapter.Fila> filas = new ArrayList<>();
        if (modoUsuarios) {
            for (UsuarioItem u : usuarios) {
                String estado = u.activo == 1 ? "" : "  ·  deshabilitado";
                filas.add(new FilaAdapter.Fila(u.nombreCompleto, "@" + u.username + estado, u.nombreRol));
            }
        } else {
            for (Rol r : roles) {
                String desc = (r.descripcion == null || r.descripcion.isEmpty())
                        ? "Sin descripción" : r.descripcion;
                filas.add(new FilaAdapter.Fila(r.nombreRol, desc, ""));
            }
        }
        adapter.setFilas(filas);
    }

    // ---------------------- Activar / desactivar usuario ----------------------

    private void confirmarCambioEstado(final UsuarioItem u) {
        // Un administrador no puede deshabilitarse a sí mismo
        if (u.idUsuario == session.getIdUsuario()) {
            UiUtils.mensaje(findViewById(R.id.main), "No puedes deshabilitar tu propio usuario");
            return;
        }

        final boolean deshabilitar = u.activo == 1;
        new MaterialAlertDialogBuilder(this)
                .setTitle(deshabilitar ? "Deshabilitar usuario" : "Habilitar usuario")
                .setMessage(deshabilitar
                        ? "¿Desea deshabilitar a " + u.nombreCompleto + "? No podrá iniciar sesión."
                        : "¿Desea habilitar a " + u.nombreCompleto + "?")
                .setPositiveButton(deshabilitar ? "Deshabilitar" : "Habilitar", (dialog, which) -> {
                    dbHelper.cambiarEstadoUsuario(u.idUsuario, deshabilitar ? 0 : 1);
                    recargarDatos();
                    UiUtils.mensaje(findViewById(R.id.main),
                            deshabilitar ? "Usuario deshabilitado" : "Usuario habilitado");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------------------- Nuevo usuario ----------------------

    private void mostrarDialogoNuevoUsuario() {
        if (roles.isEmpty()) {
            UiUtils.mensaje(findViewById(R.id.main), "Primero debe existir al menos un rol");
            return;
        }

        View vista = getLayoutInflater().inflate(R.layout.dialog_usuario, null);
        final TextInputLayout tilUsername = vista.findViewById(R.id.tilUsername);
        final TextInputLayout tilPassword = vista.findViewById(R.id.tilPasswordNuevo);
        final TextInputLayout tilNombre = vista.findViewById(R.id.tilNombreCompleto);
        final TextInputEditText etUsername = vista.findViewById(R.id.etUsername);
        final TextInputEditText etPassword = vista.findViewById(R.id.etPasswordNuevo);
        final TextInputEditText etNombre = vista.findViewById(R.id.etNombreCompleto);
        final Spinner spRol = vista.findViewById(R.id.spRol);

        // Selector con los roles disponibles
        List<String> nombresRoles = new ArrayList<>();
        for (Rol r : roles) {
            nombresRoles.add(r.nombreRol);
        }
        ArrayAdapter<String> spAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, nombresRoles);
        spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRol.setAdapter(spAdapter);

        final List<Rol> rolesDisponibles = new ArrayList<>(roles);

        final AlertDialog dialogo = new MaterialAlertDialogBuilder(this)
                .setTitle("Nuevo usuario")
                .setView(vista)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialogo.setOnShowListener(d -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            tilUsername.setError(null);
            tilPassword.setError(null);
            tilNombre.setError(null);

            final String username = UiUtils.texto(etUsername);
            final String password = etPassword.getText() == null ? "" : etPassword.getText().toString();
            final String nombre = UiUtils.texto(etNombre);

            boolean valido = true;
            if (username.isEmpty()) {
                tilUsername.setError("El nombre de usuario es obligatorio");
                valido = false;
            }
            if (password.isEmpty()) {
                tilPassword.setError("La contraseña es obligatoria");
                valido = false;
            }
            if (nombre.isEmpty()) {
                tilNombre.setError("El nombre completo es obligatorio");
                valido = false;
            }
            if (!valido) {
                return;
            }

            final Rol rolElegido = rolesDisponibles.get(spRol.getSelectedItemPosition());

            // Flujo alternativo 6a: validación previa de username duplicado
            if (dbHelper.contarUsuariosPorUsername(username) > 0) {
                tilUsername.setError("El nombre de usuario ya se encuentra registrado");
                return;
            }

            Usuario u = new Usuario();
            u.username = username;
            u.passwordHash = PasswordUtils.hash(password);
            u.nombreCompleto = nombre;
            u.idRol = rolElegido.idRol;
            u.activo = 1;

            try {
                dbHelper.insertarUsuario(u);
            } catch (SQLiteConstraintException e) {
                // Respaldo: excepción de clave única
                tilUsername.setError("El nombre de usuario ya se encuentra registrado");
                return;
            }

            dialogo.dismiss();
            recargarDatos();
            UiUtils.mensaje(findViewById(R.id.main), "Usuario registrado exitosamente");
        }));

        dialogo.show();
    }

    // ---------------------- Nuevo rol ----------------------

    private void mostrarDialogoNuevoRol() {
        View vista = getLayoutInflater().inflate(R.layout.dialog_rol, null);
        final TextInputLayout tilNombre = vista.findViewById(R.id.tilNombreRol);
        final TextInputEditText etNombre = vista.findViewById(R.id.etNombreRol);
        final TextInputEditText etDescripcion = vista.findViewById(R.id.etDescripcionRol);

        final AlertDialog dialogo = new MaterialAlertDialogBuilder(this)
                .setTitle("Nuevo rol")
                .setView(vista)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialogo.setOnShowListener(d -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            tilNombre.setError(null);
            final String nombre = UiUtils.texto(etNombre);
            final String descripcion = UiUtils.texto(etDescripcion);

            if (nombre.isEmpty()) {
                tilNombre.setError("El nombre del rol es obligatorio");
                return;
            }

            if (dbHelper.contarRolesPorNombre(nombre) > 0) {
                tilNombre.setError("Ya existe un rol con ese nombre");
                return;
            }

            Rol r = new Rol();
            r.nombreRol = nombre;
            r.descripcion = descripcion.isEmpty() ? null : descripcion;

            try {
                dbHelper.insertarRol(r);
            } catch (SQLiteConstraintException e) {
                tilNombre.setError("Ya existe un rol con ese nombre");
                return;
            }

            dialogo.dismiss();
            recargarDatos();
            UiUtils.mensaje(findViewById(R.id.main), "Rol registrado exitosamente");
        }));

        dialogo.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
