package com.example.comercializadoraangel.data;

/** Fila de la tabla CATEGORIA (RF-09, RF-10). nombre_categoria es único (UK). */
public class Categoria {

    public int idCategoria;
    public String nombreCategoria = "";
    public String descripcion;

    /** Por defecto 1 (activo). */
    public int activo = 1;
}
