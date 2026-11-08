package com.example.comercializadoraangel.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.example.comercializadoraangel.data.ProductoItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** RF-23: genera el informe de stock en PDF (sin librerías externas, usa android.graphics.pdf). */
public final class InformePdf {

    private static final int ANCHO = 595; // A4 a 72 dpi
    private static final int ALTO = 842;
    private static final int MARGEN = 36;
    private static final float ALTO_FILA = 20f;

    // Columnas: SKU, Producto, Categoría, Stock, Mínimo, Estado (suman 523 = ANCHO - 2 * MARGEN)
    private static final float[] ANCHOS = {62f, 165f, 105f, 62f, 40f, 89f};
    private static final String[] TITULOS = {"SKU", "Producto", "Categoría", "Stock", "Mín.", "Estado"};

    private final PdfDocument documento = new PdfDocument();
    private final Paint pTitulo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTexto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCabecera = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAlerta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLinea = new Paint();

    private PdfDocument.Page pagina;
    private Canvas canvas;
    private int numeroPagina = 0;
    private float y;

    private InformePdf() {
        pTitulo.setTextSize(18f);
        pTitulo.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        pTexto.setTextSize(9.5f);
        pTexto.setColor(Color.rgb(43, 33, 24));
        pCabecera.setTextSize(9.5f);
        pCabecera.setTypeface(Typeface.DEFAULT_BOLD);
        pCabecera.setColor(Color.rgb(122, 74, 33));
        pAlerta.setTextSize(9.5f);
        pAlerta.setTypeface(Typeface.DEFAULT_BOLD);
        pAlerta.setColor(Color.rgb(179, 38, 30));
        pLinea.setColor(Color.rgb(200, 190, 170));
        pLinea.setStrokeWidth(0.8f);
    }

    /**
     * Crea el PDF en la carpeta cache/informes y devuelve el archivo.
     *
     * @param filtros texto con los filtros aplicados (se imprime bajo el título)
     * @param usuario quien generó el informe
     */
    public static File generar(Context context, List<ProductoItem> productos, String filtros, String usuario)
            throws IOException {
        InformePdf pdf = new InformePdf();
        try {
            return pdf.escribir(context, productos, filtros, usuario);
        } finally {
            pdf.documento.close();
        }
    }

    private File escribir(Context context, List<ProductoItem> productos, String filtros, String usuario)
            throws IOException {
        nuevaPagina();
        canvas.drawText("Comercializadora Angel - Informe de stock", MARGEN, y + 14f, pTitulo);
        y += 30f;
        canvas.drawText("Generado: " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date())
                + "  |  Usuario: " + usuario, MARGEN, y, pTexto);
        y += 14f;
        canvas.drawText("Filtros: " + filtros, MARGEN, y, pTexto);
        y += 14f;

        int sinStock = 0;
        int criticos = 0;
        for (ProductoItem p : productos) {
            if (ProductoItem.ESTADO_SIN_STOCK.equals(p.estadoStock())) {
                sinStock++;
            } else if (ProductoItem.ESTADO_CRITICO.equals(p.estadoStock())) {
                criticos++;
            }
        }
        canvas.drawText("Productos: " + productos.size() + "  |  Sin stock: " + sinStock
                + "  |  Stock crítico: " + criticos, MARGEN, y, pTexto);
        y += 20f;

        dibujarCabeceraTabla();
        for (ProductoItem p : productos) {
            if (y + ALTO_FILA > ALTO - MARGEN - 10f) {
                cerrarPagina();
                nuevaPagina();
                dibujarCabeceraTabla();
            }
            String estado = p.estadoStock();
            Paint pEstado = ProductoItem.ESTADO_NORMAL.equals(estado) ? pTexto : pAlerta;
            String[] celdas = {p.skuCodigo, p.nombre, p.nombreCategoria,
                    p.stockActual + " " + p.unidadMedida, String.valueOf(p.stockMinimo), estado};
            float x = MARGEN;
            for (int i = 0; i < celdas.length; i++) {
                canvas.drawText(recortar(celdas[i], ANCHOS[i] - 6f, i == 5 ? pEstado : pTexto),
                        x, y + 13f, i == 5 ? pEstado : pTexto);
                x += ANCHOS[i];
            }
            y += ALTO_FILA;
            canvas.drawLine(MARGEN, y, ANCHO - MARGEN, y, pLinea);
        }
        cerrarPagina();

        File carpeta = new File(context.getCacheDir(), "informes");
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta de informes");
        }
        File archivo = new File(carpeta, "informe_stock_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date()) + ".pdf");
        try (FileOutputStream salida = new FileOutputStream(archivo)) {
            documento.writeTo(salida);
        }
        return archivo;
    }

    private void nuevaPagina() {
        numeroPagina++;
        pagina = documento.startPage(new PdfDocument.PageInfo.Builder(ANCHO, ALTO, numeroPagina).create());
        canvas = pagina.getCanvas();
        y = MARGEN;
    }

    private void cerrarPagina() {
        canvas.drawText("Página " + numeroPagina, ANCHO - MARGEN - 50f, ALTO - 20f, pTexto);
        documento.finishPage(pagina);
    }

    private void dibujarCabeceraTabla() {
        float x = MARGEN;
        for (int i = 0; i < TITULOS.length; i++) {
            canvas.drawText(TITULOS[i], x, y + 13f, pCabecera);
            x += ANCHOS[i];
        }
        y += ALTO_FILA;
        canvas.drawLine(MARGEN, y, ANCHO - MARGEN, y, pLinea);
    }

    /** Corta el texto para que no se salga de su columna. */
    private static String recortar(String texto, float anchoMax, Paint paint) {
        if (texto == null) {
            return "";
        }
        if (paint.measureText(texto) <= anchoMax) {
            return texto;
        }
        int n = paint.breakText(texto, true, anchoMax - paint.measureText("..."), null);
        return texto.substring(0, Math.max(0, n)) + "...";
    }
}
