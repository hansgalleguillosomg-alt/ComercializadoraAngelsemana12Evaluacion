package com.example.comercializadoraangel;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.comercializadoraangel.data.DBHelper;
import com.example.comercializadoraangel.data.Rol;
import com.example.comercializadoraangel.data.Usuario;
import com.example.comercializadoraangel.util.PasswordUtils;
import com.example.comercializadoraangel.util.SessionManager;
import com.example.comercializadoraangel.util.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** CU-01: Iniciar sesión (RF-01). */
public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilUsuario, tilPassword;
    private TextInputEditText etUsuario, etPassword;
    private MaterialButton btnLogin;

    private SessionManager session;
    private DBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        session = new SessionManager(this);
        if (session.haySesion()) {
            irAlCatalogo();
            return;
        }

        setContentView(R.layout.activity_login);
        UiUtils.aplicarInsets(findViewById(R.id.main));

        dbHelper = new DBHelper(this);

        tilUsuario = findViewById(R.id.tilUsuario);
        tilPassword = findViewById(R.id.tilPassword);
        etUsuario = findViewById(R.id.etUsuario);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> intentarLogin());
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                intentarLogin();
                return true;
            }
            return false;
        });
    }

    private void intentarLogin() {
        tilUsuario.setError(null);
        tilPassword.setError(null);

        final String username = UiUtils.texto(etUsuario);
        final String password = etPassword.getText() == null ? "" : etPassword.getText().toString();

        boolean valido = true;
        if (username.isEmpty()) {
            tilUsuario.setError("Ingrese su usuario");
            valido = false;
        }
        if (password.isEmpty()) {
            tilPassword.setError("Ingrese su contraseña");
            valido = false;
        }
        if (!valido) {
            return;
        }

        // Pasos 4 y 5: consulta por username y compara password_hash
        final Usuario usuario = dbHelper.buscarUsuarioPorUsername(username);
        final boolean credencialesOk = usuario != null
                && usuario.passwordHash.equals(PasswordUtils.hash(password));

        // Flujo alternativo 4a
        if (!credencialesOk) {
            UiUtils.mensaje(findViewById(R.id.main), "Credenciales inválidas");
            etPassword.setText("");
            etUsuario.requestFocus();
            return;
        }

        // Flujo alternativo 6a: usuario inactivo
        if (usuario.activo != 1) {
            UiUtils.mensaje(findViewById(R.id.main),
                    "Usuario deshabilitado. Contacte al administrador");
            return;
        }

        // Paso 7: obtiene el rol y persiste la sesión
        Rol rol = dbHelper.buscarRolPorId(usuario.idRol);
        session.guardarSesion(usuario.idUsuario, usuario.username, usuario.nombreCompleto,
                usuario.idRol, rol != null ? rol.nombreRol : "");
        irAlCatalogo();
    }

    private void irAlCatalogo() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
