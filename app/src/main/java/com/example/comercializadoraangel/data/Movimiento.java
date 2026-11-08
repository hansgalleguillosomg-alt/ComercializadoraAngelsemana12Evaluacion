package com.example.comercializadoraangel.data;

/** Fila de la tabla MOVIMIENTO (RF-15, RF-16). Un movimiento nunca se modifica ni se elimina. */
public class Movimiento {

    public static final String ENTRADA = "ENTRADA";
    public static final String SALIDA = "SALIDA";

    public int idMovimiento;
    public int idProducto;
    public int idUsuario;

    /** ENTRADA o SALIDA */
    public String tipo = ENTRADA;

    /** Entero > 0 */
    public int cantidad;

    /** Formato yyyy-MM-dd HH:mm:ss (hora local) */
    public String fecha;

    /** 0 = sin proveedor (solo entradas, opcional) */
    public int idProveedor;
    public String documento;
    public String motivo;
    public String destinatario;
    public String observacion;

    /** Stock del producto después del movimiento (lo completa DBHelper). */
    public int stockResultante;
}
