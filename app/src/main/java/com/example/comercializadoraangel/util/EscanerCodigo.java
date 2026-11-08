package com.example.comercializadoraangel.util;

import android.Manifest;
import android.content.pm.PackageManager;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

/**
 * RF-20 / CU-07: lector de códigos de barras con la cámara (librería ZXing).
 * Pide el permiso de cámara si hace falta. Debe crearse en onCreate() de la Activity
 * (antes de que la pantalla esté visible), porque registra los "launchers" de resultados.
 */
public class EscanerCodigo {

    public interface Listener {
        /** Se leyó un código (ya sin espacios). */
        void onCodigoLeido(String codigo);

        /** El usuario no autorizó la cámara: la pantalla debe ofrecer búsqueda manual. */
        void onPermisoDenegado();
    }

    private final ComponentActivity activity;
    private final ActivityResultLauncher<String> permisoCamara;
    private final ActivityResultLauncher<ScanOptions> lector;

    public EscanerCodigo(ComponentActivity activity, Listener listener) {
        this.activity = activity;

        this.lector = activity.registerForActivityResult(new ScanContract(), resultado -> {
            // contents == null cuando el usuario cancela (botón Atrás) sin haber leído nada
            if (resultado != null && resultado.getContents() != null) {
                listener.onCodigoLeido(resultado.getContents().trim());
            }
        });

        this.permisoCamara = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), concedido -> {
                    if (concedido) {
                        abrirLector();
                    } else {
                        listener.onPermisoDenegado();
                    }
                });
    }

    /** Solicita el permiso (si falta) y abre la cámara. */
    public void escanear() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            abrirLector();
        } else {
            permisoCamara.launch(Manifest.permission.CAMERA);
        }
    }

    private void abrirLector() {
        ScanOptions opciones = new ScanOptions();
        opciones.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES);
        opciones.setPrompt("Enfoca el código de barras dentro del recuadro");
        opciones.setBeepEnabled(true);
        opciones.setOrientationLocked(false);
        lector.launch(opciones);
    }
}
