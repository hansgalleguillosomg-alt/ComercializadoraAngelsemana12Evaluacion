package com.example.comercializadoraangel.data;

/** Resultado del JOIN PRODUCTO + CATEGORIA para mostrar en el catálogo, el informe y el escáner. */
public class ProductoItem {

    public static final String ESTADO_SIN_STOCK = "Sin stock";
    public static final String ESTADO_CRITICO = "Stock crítico";
    public static final String ESTADO_NORMAL = "Disponible";

    public int idProducto;
    public String skuCodigo;
    public String codigoBarras;
    public String nombre;
    public String descripcion;
    public String unidadMedida;
    public String ubicacionFisica;
    public int stockActual;
    public int stockMinimo;
    public int activo;
    public int idCategoria;
    public String nombreCategoria;

    /** RF-19: sin stock (0), crítico (<= mínimo) o disponible. */
    public String estadoStock() {
        if (stockActual <= 0) {
            return ESTADO_SIN_STOCK;
        }
        return stockActual <= stockMinimo ? ESTADO_CRITICO : ESTADO_NORMAL;
    }
}
