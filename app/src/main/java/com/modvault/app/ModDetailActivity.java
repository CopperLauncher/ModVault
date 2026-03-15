package com.modvault.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.modvault.app.api.CurseForgeApi;
import com.modvault.app.api.ModrinthApi;
import com.modvault.app.model.ModResult;
import com.modvault.app.model.ModVersion;
import com.modvault.app.ui.VersionAdapter;
import com.modvault.app.utils.ModDownloader;
import com.modvault.app.utils.PrefManager;
import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Looper;
import java.util.List;

public class ModDetailActivity extends AppCompatActivity {

    public static final String EXTRA_MOD = "mod_json";
    public static final String EXTRA_PROJECT_TYPE = "project_type";

    private ModResult mod;
    private String projectType;
    private ModDownloader downloader;
    private PrefManager prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ModrinthApi api = new ModrinthApi();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mod_detail);

        downloader = new ModDownloader(this);
        prefs = new PrefManager(this);

        // Get mod from intent
        String modJson = getIntent().getStringExtra(EXTRA_MOD);
        projectType = getIntent().getStringExtra(EXTRA_PROJECT_TYPE);
        if (modJson == null) { finish(); return; }
        mod = new com.google.gson.Gson().fromJson(modJson, ModResult.class);

        // Back button
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Toolbar title
        TextView tvTitle = findViewById(R.id.tv_detail_title);
        tvTitle.setText(mod.title);

        // Icon
        ImageView icon = findViewById(R.id.detail_icon);
        if (mod.iconUrl != null && !mod.iconUrl.isEmpty()) {
            Glide.with(this).load(mod.iconUrl).placeholder(R.drawable.ic_mod_default).into(icon);
        } else {
            icon.setImageResource(R.drawable.ic_mod_default);
        }

        // Title + type badge
        ((TextView) findViewById(R.id.detail_title)).setText(mod.title);
        String typeLabel = "resourcepack".equals(projectType) ? "Resource Pack"
                         : "shader".equals(projectType) ? "Shader" : "Mod";
        ((TextView) findViewById(R.id.detail_type_badge)).setText(typeLabel);

        // Description
        ((TextView) findViewById(R.id.detail_description)).setText(mod.description);

        // Stats
        ((TextView) findViewById(R.id.detail_downloads)).setText(formatNumber(mod.downloads));
        ((TextView) findViewById(R.id.detail_followers)).setText(formatNumber(mod.followers));

        // Categories chips
        ChipGroup chipGroup = findViewById(R.id.detail_categories);
        if (mod.categories != null) {
            for (String cat : mod.categories) {
                Chip chip = new Chip(this);
                chip.setText(cat);
                chip.setChipBackgroundColorResource(android.R.color.transparent);
                chip.setTextColor(0xFFB87333);
                chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(0xFFB87333));
                chip.setChipStrokeWidth(1f);
                chip.setClickable(false);
                chipGroup.addView(chip);
            }
        }

        // Load versions
        ProgressBar progress = findViewById(R.id.detail_versions_progress);
        RecyclerView versionsRecycler = findViewById(R.id.detail_versions_recycler);
        versionsRecycler.setLayoutManager(new LinearLayoutManager(this));
        progress.setVisibility(View.VISIBLE);

        api.getVersions(mod.projectId, "", "", versions -> {
            handler.post(() -> {
                progress.setVisibility(View.GONE);
                if (versions == null || versions.isEmpty()) {
                    Toast.makeText(this, "No versions found", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(ModDetailActivity.this, versions.size() + " versions found", Toast.LENGTH_SHORT).show();
                VersionAdapter adapter = new VersionAdapter(versions, (version, file) ->
                    startDownload(version, file));
                versionsRecycler.setAdapter(adapter);
            });
        }, error -> handler.post(() -> {
            progress.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load versions", Toast.LENGTH_SHORT).show();
        }));
    }

    private void startDownload(ModVersion version, ModVersion.VersionFile file) {
        String subFolder = "resourcepack".equals(projectType) ? "resourcepacks"
                         : "shader".equals(projectType) ? "shaderpacks" : "mods";

        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Installing " + mod.title);
        progress.setMessage("Downloading\u2026");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();

        ModDownloader.DownloadCallback callback = new ModDownloader.DownloadCallback() {
            public void onProgress(String fileName, int percent) {
                handler.post(() -> { progress.setMessage(fileName); progress.setProgress(percent); });
            }
            public void onSuccess(String fileName) {
                handler.post(() -> {
                    progress.dismiss();
                    Toast.makeText(ModDetailActivity.this, mod.title + " installed!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
            public void onError(String error) {
                handler.post(() -> {
                    progress.dismiss();
                    Toast.makeText(ModDetailActivity.this, "Install failed: " + error, Toast.LENGTH_LONG).show();
                });
            }
        };

        Uri instanceUri = prefs.getInstanceUri();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                && instanceUri != null && "content".equals(instanceUri.getScheme())) {
            downloader.downloadMod(file, instanceUri, subFolder,
                version.dependencies, "", "", callback);
        } else {
            java.io.File instanceDir = prefs.getInstanceUri() != null
                ? new java.io.File(prefs.getInstanceUri().getPath()) : null;
            if (instanceDir == null) { progress.dismiss(); return; }
            java.io.File targetDir = new java.io.File(instanceDir, subFolder);
            if (!targetDir.exists()) targetDir.mkdirs();
            downloader.downloadMod(file, targetDir, version.dependencies, "", "", callback);
        }
    }

    private String formatNumber(int n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000f);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000f);
        return String.valueOf(n);
    }
}
