package com.example.comercializadoraangel.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.comercializadoraangel.data.Rol;

/** Guarda localmente la sesión activa (RF-01 / RF-02) en SharedPreferences. */
public class SessionManager {

    private static final String PREFS = "sesion_angel";
    private static final String KEY_ID_USUARIO = "id_usuario";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_NOMBRE = "nombre_completo";
    private static final String KEY_ID_ROL = "id_rol";
    private static final String KEY_NOMBRE_ROL = "nombre_rol";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void guardarSesion(int idUsuario, String username, String nombreCompleto,
                              int idRol, String nombreRol) {
        prefs.edit()
                .putInt(KEY_ID_USUARIO, idUsuario)
                .putString(KEY_USERNAME, username)
                .putString(KEY_NOMBRE, nombreCompleto)
                .putInt(KEY_ID_ROL, idRol)
                .putString(KEY_NOMBRE_ROL, nombreRol)
                .apply();
    }

    public boolean haySesion() {
        return prefs.getInt(KEY_ID_USUARIO, -1) != -1;
    }

    public int getIdUsuario() {
        return prefs.getInt(KEY_ID_USUARIO, -1);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public String getNombreCompleto() {
        return prefs.getString(KEY_NOMBRE, "");
    }

    public int getIdRol() {
        return prefs.getInt(KEY_ID_ROL, -1);
    }

    public String getNombreRol() {
        return prefs.getString(KEY_NOMBRE_ROL, "");
    }

    public boolean esAdministrador() {
        return Rol.ADMINISTRADOR.equals(getNombreRol());
    }

    /** CU-04 / CU-05: entradas y salidas las registran el Operador/Bodeguero y el Administrador. */
    public boolean puedeRegistrarMovimientos() {
        return esAdministrador() || Rol.OPERADOR.equals(getNombreRol());
    }

    /** RF-02: limpia credenciales, identificadores y estado de sesión. */
    public void cerrarSesion() {
        prefs.edit().clear().apply();
    }
}
