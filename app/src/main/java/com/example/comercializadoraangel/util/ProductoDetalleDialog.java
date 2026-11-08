package com.example.comercializadoraangel.util;

import android.app.Activity;
import android.content.Intent;

import com.example.comercializadoraangel.HistorialActivity;
import com.example.comercializadoraangel.MovimientoActivity;
import com.example.comercializadoraangel.data.Movimiento;
import com.example.comercializadoraangel.data.ProductoItem;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Ficha del producto (CU-07 paso 46 y 47): SKU, nombre, categoría, stock actual, stock mínimo y
 * ubicación física, con acceso a Registrar entrada / salida según los permisos del usuario.
 * La usan el catálogo (al tocar una tarjeta) y el resultado del escáner de código de barras.
 */
public final class ProductoDetalleDialog {

    private ProductoDetalleDialog() {
    }

    public static void mostrar(Activity activity, ProductoItem p, boolean puedeMover) {
        String barras = p.codigoBarras == null || p.codigoBarras.isEmpty() ? "—" : p.codigoBarras;
        String ubicacion = p.ubicacionFisica == null || p.ubicacionFisica.isEmpty() ? "No registrada" : p.ubicacionFisica;
        String mensaje = "SKU: " + p.skuCodigo
                + "\nCódigo de barras: " + barras
                + "\nCategoría: " + p.nombreCategoria
                + "\nStock actual: " + p.stockActual + " " + p.unidadMedida
                + "\nStock mínimo: " + p.stockMinimo + " " + p.unidadMedida
                + "\nEstado: " + p.estadoStock()
                + "\nUbicación física: " + ubicacion;
        if (p.descripcion != null && !p.descripcion.isEmpty()) {
            mensaje += "\n\n" + p.descripcion;
        }

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(activity)
                .setTitle(p.nombre)
                .setMessage(mensaje)
                .setNegativeButton("Cerrar", null);

        if (puedeMover) {
            b.setPositiveButton("Registrar entrada", (d, w) -> abrirMovimiento(activity, p, Movimiento.ENTRADA));
            b.setNeutralButton("Registrar salida", (d, w) -> abrirMovimiento(activity, p, Movimiento.SALIDA));
        } else {
            b.setPositiveButton("Ver historial", (d, w) -> {
                Intent i = new Intent(activity, HistorialActivity.class);
                i.putExtra(HistorialActivity.EXTRA_ID_PRODUCTO, p.idProducto);
                activity.startActivity(i);
            });
        }
        b.show();
    }

    private static void abrirMovimiento(Activity activity, ProductoItem p, String tipo) {
        Intent i = new Intent(activity, MovimientoActivity.class);
        i.putExtra(MovimientoActivity.EXTRA_TIPO, tipo);
        i.putExtra(MovimientoActivity.EXTRA_ID_PRODUCTO, p.idProducto);
        activity.startActivity(i);
    }
}
