package com.example.comercializadoraangel.util;

import android.view.View;
import android.widget.EditText;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.comercializadoraangel.R;
import com.google.android.material.snackbar.Snackbar;

public final class UiUtils {

    private UiUtils() {
    }

    /**
     * Ajusta la pantalla a edge-to-edge. Si la pantalla tiene toolbar o cabecera, estas se
     * extienden bajo la barra de estado; el resto del contenido respeta los bordes del sistema.
     */
    public static void aplicarInsets(View raiz) {
        final View toolbar = raiz.findViewById(R.id.toolbar);
        final View cabecera = raiz.findViewById(R.id.cabecera);
        final View superior = toolbar != null ? toolbar : cabecera;

        final int alturaOriginal = superior != null ? superior.getLayoutParams().height : 0;
        final int paddingOriginal = superior != null ? superior.getPaddingTop() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(raiz, (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (superior == null) {
                v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
                return insets;
            }
            v.setPadding(barras.left, 0, barras.right, barras.bottom);
            superior.setPadding(superior.getPaddingLeft(), paddingOriginal + barras.top,
                    superior.getPaddingRight(), superior.getPaddingBottom());
            if (alturaOriginal > 0) {
                superior.getLayoutParams().height = alturaOriginal + barras.top;
                superior.requestLayout();
            }
            return insets;
        });
    }

    public static void mensaje(View raiz, String texto) {
        Snackbar.make(raiz, texto, Snackbar.LENGTH_LONG).show();
    }

    /** Texto de un EditText sin espacios sobrantes (nunca null). */
    public static String texto(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    /** Convierte "2026-11-04 15:30:00" (formato guardado en la BD) a "04/11/2026 15:30". */
    public static String fechaLegible(String fechaBd) {
        if (fechaBd == null || fechaBd.length() < 16) {
            return fechaBd == null ? "" : fechaBd;
        }
        return fechaBd.substring(8, 10) + "/" + fechaBd.substring(5, 7) + "/" + fechaBd.substring(0, 4)
                + " " + fechaBd.substring(11, 16);
    }

    /** Fecha y hora actual en el formato que guarda la BD (hora local del dispositivo). */
    public static String ahora() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)
                .format(new java.util.Date());
    }
}
