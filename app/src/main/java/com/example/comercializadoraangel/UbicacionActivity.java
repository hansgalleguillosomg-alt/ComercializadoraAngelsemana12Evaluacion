package com.example.comercializadoraangel;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.comercializadoraangel.data.DBHelper;
import com.example.comercializadoraangel.data.Empresa;
import com.example.comercializadoraangel.util.SessionManager;
import com.example.comercializadoraangel.util.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

/**
 * CU-08: ubicación de la empresa en Google Maps (RF-24) con ubicación del dispositivo (RF-25)
 * y ruta hacia la empresa (RF-26).
 *
 * Se abre la app Google Maps mediante un Intent (no requiere API key). Si no está instalada,
 * se abre la URL equivalente en el navegador. La ubicación fija de la empresa siempre está
 * disponible; el GPS solo se usa (con permiso) al pedir "Cómo llegar".
 */
public class UbicacionActivity extends AppCompatActivity {

    private static final String PAQUETE_MAPS = "com.google.android.apps.maps";

    private DBHelper dbHelper;
    private SessionManager session;
    private Empresa empresa;

    private TextView tvNombre, tvDireccion, tvCoordenadas, tvAviso, tvUbicacionActual;

    // RF-25: el permiso se solicita solo cuando el usuario pide "Cómo llegar"
    private final ActivityResultLauncher<String[]> permisoUbicacion = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), resultado -> {
                if (tienePermisoUbicacion()) {
                    obtenerUbicacionYAbrirRuta();
                } else {
                    // Flujo alterno: permiso denegado, se mantiene visible la ubicación fija de la empresa
                    UiUtils.mensaje(findViewById(R.id.main),
                            "Permiso de ubicación denegado. Puede ver la ubicación de la empresa sin ruta.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        session = new SessionManager(this);
        if (!session.haySesion()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_ubicacion);
        UiUtils.aplicarInsets(findViewById(R.id.main));

        dbHelper = new DBHelper(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Ubicación de la empresa");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tvNombre = findViewById(R.id.tvEmpresaNombre);
        tvDireccion = findViewById(R.id.tvEmpresaDireccion);
        tvCoordenadas = findViewById(R.id.tvEmpresaCoordenadas);
        tvAviso = findViewById(R.id.tvAvisoUbicacion);
        tvUbicacionActual = findViewById(R.id.tvUbicacionActual);

        findViewById(R.id.btnVerMapa).setOnClickListener(v -> verEnMapa());
        findViewById(R.id.btnComoLlegar).setOnClickListener(v -> comoLlegar());

        View btnConfigurar = findViewById(R.id.btnConfigurar);
        if (session.esAdministrador()) {
            btnConfigurar.setVisibility(View.VISIBLE);
            btnConfigurar.setOnClickListener(v -> mostrarDialogoConfigurar());
        }

        mostrarEmpresa();
    }

    /** Pasos 50 a 52: obtiene las coordenadas configuradas de la empresa. */
    private void mostrarEmpresa() {
        empresa = dbHelper.obtenerEmpresa();
        tvNombre.setText(empresa.nombre);
        tvDireccion.setText(empresa.direccion == null || empresa.direccion.isEmpty()
                ? "Dirección no registrada" : empresa.direccion);
        if (empresa.tieneUbicacion()) {
            tvCoordenadas.setText("Coordenadas: " + coord(empresa.latitud) + ", " + coord(empresa.longitud));
            tvAviso.setVisibility(View.GONE);
        } else {
            tvCoordenadas.setText("Coordenadas: sin configurar");
            tvAviso.setVisibility(View.VISIBLE);
            tvAviso.setText(session.esAdministrador()
                    ? "La ubicación de la empresa no está configurada. Use \"Configurar ubicación\"."
                    : "La ubicación de la empresa aún no está configurada. Consulte al administrador.");
        }
    }

    private static String coord(double valor) {
        return String.format(Locale.US, "%.6f", valor);
    }

    // ------------------------------ Ver en Google Maps ------------------------------

    private void verEnMapa() {
        if (!hayUbicacionConfigurada()) {
            return;
        }
        String punto = coord(empresa.latitud) + "," + coord(empresa.longitud);
        // El marcador se dibuja en las coordenadas configuradas, con el nombre de la empresa como etiqueta
        Uri geo = Uri.parse("geo:" + punto + "?q=" + punto + "(" + Uri.encode(empresa.nombre) + ")");
        Uri web = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + punto);
        abrirEnGoogleMaps(geo, web);
    }

    // ------------------------------ Cómo llegar ------------------------------

    private void comoLlegar() {
        if (!hayUbicacionConfigurada()) {
            return;
        }
        if (tienePermisoUbicacion()) {
            obtenerUbicacionYAbrirRuta();
        } else {
            permisoUbicacion.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private boolean tienePermisoUbicacion() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Pasos 54 a 56: obtiene la ubicación actual (solo con autorización) y abre la ruta. */
    private void obtenerUbicacionYAbrirRuta() {
        LocationManager lm = getSystemService(LocationManager.class);
        boolean gps = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean red = lm != null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!gps && !red) {
            // Flujo alterno: GPS desactivado
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Ubicación desactivada")
                    .setMessage("Active los servicios de ubicación (GPS) para calcular la ruta hasta la empresa.")
                    .setPositiveButton("Abrir ajustes",
                            (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }

        boolean precisa = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        String proveedor = (gps && precisa) ? LocationManager.GPS_PROVIDER
                : (red ? LocationManager.NETWORK_PROVIDER : null);
        if (proveedor == null) {
            // Solo hay permiso aproximado y la red está apagada: Google Maps usará su propia ubicación
            abrirRuta(null);
            return;
        }

        tvUbicacionActual.setVisibility(View.VISIBLE);
        tvUbicacionActual.setText("Obteniendo su ubicación actual...");
        try {
            lm.getCurrentLocation(proveedor, null, ContextCompat.getMainExecutor(this), this::alObtenerUbicacion);
        } catch (SecurityException e) {
            tvUbicacionActual.setVisibility(View.GONE);
            UiUtils.mensaje(findViewById(R.id.main), "No se pudo acceder a la ubicación del dispositivo.");
        }
    }

    private void alObtenerUbicacion(Location ubicacion) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (ubicacion == null) {
            tvUbicacionActual.setText("No se pudo determinar su ubicación. Google Maps intentará usar la suya.");
            abrirRuta(null);
            return;
        }
        tvUbicacionActual.setText("Su ubicación actual: " + coord(ubicacion.getLatitude())
                + ", " + coord(ubicacion.getLongitude()));
        abrirRuta(ubicacion);
    }

    /** Abre Google Maps con la ruta desde el origen (si se conoce) hasta la empresa. */
    private void abrirRuta(Location origen) {
        String destino = coord(empresa.latitud) + "," + coord(empresa.longitud);
        StringBuilder url = new StringBuilder("https://www.google.com/maps/dir/?api=1&destination=")
                .append(destino).append("&travelmode=driving");
        if (origen != null) {
            url.append("&origin=").append(coord(origen.getLatitude())).append(",").append(coord(origen.getLongitude()));
        }
        Uri uri = Uri.parse(url.toString());
        abrirEnGoogleMaps(uri, uri);
    }

    // ------------------------------ Utilidades ------------------------------

    private boolean hayUbicacionConfigurada() {
        if (!empresa.tieneUbicacion()) {
            UiUtils.mensaje(findViewById(R.id.main), "La ubicación de la empresa aún no está configurada.");
            return false;
        }
        return true;
    }

    /**
     * Intenta abrir la app Google Maps; si no existe usa el navegador con la URL equivalente;
     * si tampoco se puede, informa el problema (flujo alterno "Google Maps no disponible").
     */
    private void abrirEnGoogleMaps(Uri uriMaps, Uri uriWeb) {
        try {
            Intent maps = new Intent(Intent.ACTION_VIEW, uriMaps);
            maps.setPackage(PAQUETE_MAPS);
            startActivity(maps);
            return;
        } catch (ActivityNotFoundException e) {
            // Google Maps no está instalada: se prueba con el navegador
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uriWeb));
        } catch (ActivityNotFoundException e) {
            UiUtils.mensaje(findViewById(R.id.main), "Google Maps no está disponible en este dispositivo.");
        }
    }

    // ------------------------------ Configurar (Administrador) ------------------------------

    private void mostrarDialogoConfigurar() {
        View vista = getLayoutInflater().inflate(R.layout.dialog_empresa, null);
        final TextInputLayout tilNombre = vista.findViewById(R.id.tilEmpresaNombre);
        final TextInputLayout tilLat = vista.findViewById(R.id.tilEmpresaLatitud);
        final TextInputLayout tilLng = vista.findViewById(R.id.tilEmpresaLongitud);
        final TextInputEditText etNombre = vista.findViewById(R.id.etEmpresaNombre);
        final TextInputEditText etDireccion = vista.findViewById(R.id.etEmpresaDireccion);
        final TextInputEditText etLat = vista.findViewById(R.id.etEmpresaLatitud);
        final TextInputEditText etLng = vista.findViewById(R.id.etEmpresaLongitud);

        etNombre.setText(empresa.nombre);
        etDireccion.setText(empresa.direccion);
        if (empresa.tieneUbicacion()) {
            etLat.setText(coord(empresa.latitud));
            etLng.setText(coord(empresa.longitud));
        }

        final AlertDialog dialogo = new MaterialAlertDialogBuilder(this)
                .setTitle("Ubicación de la empresa")
                .setView(vista)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialogo.setOnShowListener(d -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            tilNombre.setError(null);
            tilLat.setError(null);
            tilLng.setError(null);

            String nombre = UiUtils.texto(etNombre);
            String textoLat = UiUtils.texto(etLat);
            String textoLng = UiUtils.texto(etLng);
            boolean valido = true;

            if (nombre.isEmpty()) {
                tilNombre.setError("El nombre es obligatorio");
                valido = false;
            }

            Double lat = null;
            Double lng = null;
            if (!textoLat.isEmpty() || !textoLng.isEmpty()) {
                lat = parsear(textoLat, -90, 90);
                lng = parsear(textoLng, -180, 180);
                if (lat == null) {
                    tilLat.setError("Latitud entre -90 y 90");
                    valido = false;
                }
                if (lng == null) {
                    tilLng.setError("Longitud entre -180 y 180");
                    valido = false;
                }
            }
            if (!valido) {
                return;
            }

            Empresa nueva = new Empresa();
            nueva.nombre = nombre;
            nueva.direccion = UiUtils.texto(etDireccion);
            nueva.latitud = lat;
            nueva.longitud = lng;
            dbHelper.guardarEmpresa(nueva);

            dialogo.dismiss();
            mostrarEmpresa();
            UiUtils.mensaje(findViewById(R.id.main), "Ubicación guardada");
        }));
        dialogo.show();
    }

    /** Devuelve el número si está dentro del rango, o null si es inválido. */
    private static Double parsear(String texto, double min, double max) {
        try {
            double valor = Double.parseDouble(texto.replace(',', '.'));
            return valor >= min && valor <= max ? valor : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
