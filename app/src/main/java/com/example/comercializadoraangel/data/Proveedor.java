package com.example.comercializadoraangel.data;

/** Fila de la tabla PROVEEDOR. rut_empresa es único (UK). Requiere razón social y número de contacto. */
public class Proveedor {

    public int idProveedor;
    public String rutEmpresa = "";
    public String razonSocial = "";
    public String telefonoContacto = "";
}
