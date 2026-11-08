package com.example.comercializadoraangel.data;

/** Fila de la tabla USUARIO (RF-01, RF-27). username es único (UK). */
public class Usuario {

    public int idUsuario;
    public String username = "";
    public String passwordHash = "";
    public String nombreCompleto = "";
    public int idRol;

    /** 1 = activo, 0 = deshabilitado */
    public int activo = 1;
}
