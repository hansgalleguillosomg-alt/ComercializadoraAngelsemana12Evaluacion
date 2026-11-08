package com.example.comercializadoraangel.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.comercializadoraangel.R;
import com.example.comercializadoraangel.data.ProductoItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lista del catálogo de productos (RF-04 / RF-05 / RF-17). Los botones de acción solo se muestran al administrador. */
public class ProductoAdapter extends RecyclerView.Adapter<ProductoAdapter.ProductoViewHolder> {

    public interface Acciones {
        void onEditar(ProductoItem producto);

        void onDeshabilitar(ProductoItem producto);

        /** Toque sobre la tarjeta: muestra el detalle del producto (stock, ubicación, entrada/salida). */
        void onDetalle(ProductoItem producto);
    }

    private final List<ProductoItem> productos = new ArrayList<>();
    private final boolean mostrarAcciones;
    private final Acciones acciones;

    public ProductoAdapter(boolean mostrarAcciones, Acciones acciones) {
        this.mostrarAcciones = mostrarAcciones;
        this.acciones = acciones;
    }

    public void setProductos(List<ProductoItem> nuevos) {
        productos.clear();
        if (nuevos != null) {
            productos.addAll(nuevos);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProductoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_producto, parent, false);
        return new ProductoViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductoViewHolder holder, int position) {
        final ProductoItem p = productos.get(position);

        holder.itemView.setOnClickListener(v -> acciones.onDetalle(p));
        holder.tvNombre.setText(p.nombre);
        holder.tvSkuCategoria.setText("SKU: " + p.skuCodigo + "  ·  " + p.nombreCategoria);

        if (p.descripcion == null || p.descripcion.trim().isEmpty()) {
            holder.tvDescripcion.setVisibility(View.GONE);
        } else {
            holder.tvDescripcion.setVisibility(View.VISIBLE);
            holder.tvDescripcion.setText(p.descripcion);
        }

        boolean esQueso = p.nombreCategoria != null
                && p.nombreCategoria.toLowerCase(Locale.ROOT).contains("queso");
        holder.ivIcono.setImageResource(esQueso ? R.drawable.ic_cheese : R.drawable.ic_nut);
        holder.ivIcono.setBackgroundResource(esQueso ? R.drawable.bg_circle_cheese : R.drawable.bg_circle_nut);

        // Stock bajo el mínimo: la etiqueta se pinta de aviso
        boolean stockBajo = !ProductoItem.ESTADO_NORMAL.equals(p.estadoStock());
        int fondo = MaterialColors.getColor(holder.tvStock,
                stockBajo ? com.google.android.material.R.attr.colorErrorContainer
                        : com.google.android.material.R.attr.colorSecondaryContainer);
        int texto = MaterialColors.getColor(holder.tvStock,
                stockBajo ? com.google.android.material.R.attr.colorOnErrorContainer
                        : com.google.android.material.R.attr.colorOnSecondaryContainer);
        holder.tvStock.setBackgroundTintList(ColorStateList.valueOf(fondo));
        holder.tvStock.setTextColor(texto);
        String cantidad = p.stockActual + " " + p.unidadMedida + " (mín. " + p.stockMinimo + ")";
        holder.tvStock.setText(ProductoItem.ESTADO_SIN_STOCK.equals(p.estadoStock())
                ? "Sin stock (mín. " + p.stockMinimo + ")"
                : stockBajo ? "Stock bajo: " + cantidad : "Stock: " + cantidad);

        if (mostrarAcciones) {
            holder.llAcciones.setVisibility(View.VISIBLE);
            holder.btnEditar.setOnClickListener(v -> acciones.onEditar(p));
            holder.btnDeshabilitar.setOnClickListener(v -> acciones.onDeshabilitar(p));
        } else {
            holder.llAcciones.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return productos.size();
    }

    static class ProductoViewHolder extends RecyclerView.ViewHolder {
        final TextView tvNombre, tvSkuCategoria, tvDescripcion, tvStock;
        final ImageView ivIcono;
        final View llAcciones;
        final MaterialButton btnEditar, btnDeshabilitar;

        ProductoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcono = itemView.findViewById(R.id.ivIcono);
            tvNombre = itemView.findViewById(R.id.tvNombre);
            tvSkuCategoria = itemView.findViewById(R.id.tvSkuCategoria);
            tvDescripcion = itemView.findViewById(R.id.tvDescripcion);
            tvStock = itemView.findViewById(R.id.tvStock);
            llAcciones = itemView.findViewById(R.id.llAcciones);
            btnEditar = itemView.findViewById(R.id.btnEditar);
            btnDeshabilitar = itemView.findViewById(R.id.btnDeshabilitar);
        }
    }
}
