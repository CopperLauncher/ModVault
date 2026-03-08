package com.cuinstaller.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cuinstaller.app.R;

import java.io.File;
import java.util.List;

public class InstalledModsAdapter extends RecyclerView.Adapter<InstalledModsAdapter.ViewHolder> {

    public interface OnDeleteListener {
        void onDelete(File mod);
    }

    private final List<File> mods;
    private final OnDeleteListener listener;

    public InstalledModsAdapter(List<File> mods, OnDeleteListener listener) {
        this.mods = mods;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_installed_mod, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File mod = mods.get(position);
        holder.name.setText(mod.getName());
        holder.size.setText(formatSize(mod.length()));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(mod));
    }

    @Override
    public int getItemCount() { return mods.size(); }

    private String formatSize(long bytes) {
        if (bytes >= 1024 * 1024) return String.format("%.1f MB", bytes / (1024f * 1024f));
        return String.format("%.1f KB", bytes / 1024f);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, size;
        ImageButton btnDelete;

        ViewHolder(View v) {
            super(v);
            name      = v.findViewById(R.id.mod_filename);
            size      = v.findViewById(R.id.mod_size);
            btnDelete = v.findViewById(R.id.btn_delete_mod);
        }
    }
}
