package com.example.comercializadoraangel;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

/** Pantalla de bienvenida: muestra el logo ~3 segundos y pasa al Login. */
public class SplashActivity extends AppCompatActivity {

    private static final long DURACION_MS = 3000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable irAlLogin = () -> {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        // Animación de entrada: el logo aparece con zoom suave y el texto con fundido
        View logo = findViewById(R.id.ivLogo);
        logo.setScaleX(0.8f);
        logo.setScaleY(0.8f);
        logo.setAlpha(0f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start();

        int[] textos = {R.id.tvEmpresa, R.id.tvNombre, R.id.tvLema, R.id.progreso};
        long retardo = 400;
        for (int id : textos) {
            View v = findViewById(id);
            v.setAlpha(0f);
            v.animate().alpha(1f).setStartDelay(retardo).setDuration(600).start();
            retardo += 150;
        }

        handler.postDelayed(irAlLogin, DURACION_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(irAlLogin);
        super.onDestroy();
    }
}
