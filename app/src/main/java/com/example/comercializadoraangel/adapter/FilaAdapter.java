package com.example.comercializadoraangel.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.comercializadoraangel.R;

import java.util.ArrayList;
import java.util.List;

/** Adapter genérico de filas de 2 líneas (categorías, proveedores, usuarios y roles). */
public class FilaAdapter extends RecyclerView.Adapter<FilaAdapter.FilaViewHolder> {

    public static class Fila {
        public final String titulo;
        public final String subtitulo;
        public final String derecha;

        public Fila(String titulo, String subtitulo, String derecha) {
            this.titulo = titulo;
            this.subtitulo = subtitulo;
            this.derecha = derecha;
        }
    }

    public interface OnFilaClick {
        void onClick(int posicion);
    }

    private final List<Fila> filas = new ArrayList<>();
    private OnFilaClick onFilaClick;

    public void setOnFilaClick(OnFilaClick listener) {
        this.onFilaClick = listener;
    }

    public void setFilas(List<Fila> nuevas) {
        filas.clear();
        filas.addAll(nuevas);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FilaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fila, parent, false);
        return new FilaViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull FilaViewHolder holder, int position) {
        Fila f = filas.get(position);
        holder.tvTitulo.setText(f.titulo);
        holder.tvSubtitulo.setText(f.subtitulo);
        holder.itemView.setOnClickListener(onFilaClick == null ? null : v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                onFilaClick.onClick(pos);
            }
        });
        if (f.derecha == null || f.derecha.isEmpty()) {
            holder.tvDerecha.setVisibility(View.GONE);
        } else {
            holder.tvDerecha.setVisibility(View.VISIBLE);
            holder.tvDerecha.setText(f.derecha);
        }
    }

    @Override
    public int getItemCount() {
        return filas.size();
    }

    static class FilaViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitulo, tvSubtitulo, tvDerecha;

        FilaViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitulo = itemView.findViewById(R.id.tvTitulo);
            tvSubtitulo = itemView.findViewById(R.id.tvSubtitulo);
            tvDerecha = itemView.findViewById(R.id.tvDerecha);
        }
    }
}
