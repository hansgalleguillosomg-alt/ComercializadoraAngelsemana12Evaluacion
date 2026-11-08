package com.example.comercializadoraangel.data;

/** Fila de la tabla PRODUCTO (RF-03, 04, 05, 07, 08, 20). sku_codigo y codigo_barras son únicos (UK). */
public class Producto {

    public int idProducto;
    public String skuCodigo = "";

    /** Opcional. Si se informa, debe ser único. null = sin código de barras. */
    public String codigoBarras;
    public String nombre = "";
    public String descripcion;
    public int idCategoria;

    /** Unidad de medida (Unidad, Kg, Caja, Bolsa...). */
    public String unidadMedida = "Unidad";

    /** Ubicación física dentro de la bodega (opcional). */
    public String ubicacionFisica;

    /** Entero >= 0. Solo cambia mediante entradas y salidas (excepto el stock inicial al crear). */
    public int stockActual;
    public int stockMinimo;

    /** 1 = activo, 0 = baja lógica */
    public int activo = 1;
}
