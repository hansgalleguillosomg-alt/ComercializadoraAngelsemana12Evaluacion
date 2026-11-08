package com.example.comercializadoraangel.data;

/** Fila de la tabla ROL (RF-27). nombre_rol es único (UK). */
public class Rol {

    public static final String ADMINISTRADOR = "Administrador";
    public static final String OPERADOR = "Operador";
    public static final String CONSULTA = "Consulta";

    public int idRol;
    public String nombreRol = "";
    public String descripcion;
}
