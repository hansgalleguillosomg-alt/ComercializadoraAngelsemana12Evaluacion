package com.example.comercializadoraangel.data;

/** Datos de la empresa y su ubicación fija (RF-24). Siempre existe una sola fila (id 1). */
public class Empresa {

    public String nombre = "";
    public String direccion;

    /** null = ubicación no configurada */
    public Double latitud;
    public Double longitud;

    public boolean tieneUbicacion() {
        return latitud != null && longitud != null;
    }
}
