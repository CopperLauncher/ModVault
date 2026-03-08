package com.cuinstaller.app;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cuinstaller.app.api.ModrinthApi;
import com.cuinstaller.app.model.ModResult;
import com.cuinstaller.app.model.ModVersion;
import com.cuinstaller.app.model.SearchResponse;
import com.cuinstaller.app.ui.InstalledModsAdapter;
import com.cuinstaller.app.ui.ModAdapter;
import com.cuinstaller.app.utils.ModDownloader;
import com.cuinstaller.app.utils.PrefManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_FOLDER = 3001;

    // Views
    private View layoutBrowse, layoutInstalled, layoutSettings;
    private EditText searchInput;
    private Spinner spinnerVersion, spinnerLoader;
    private RecyclerView browseRecycler, installedRecycler;
    private TextView emptyBrowse, emptyInstalled, tvFolderPath;
    private ProgressBar browseProgress;
    private Button btnLoadMore, btnChooseFolder;

    // State
    private final List<ModResult> modResults = new ArrayList<>();
    private final List<java.io.File> installedMods = new ArrayList<>();
    private ModAdapter modAdapter;
    private InstalledModsAdapter installedAdapter;

    private final ModrinthApi api = new ModrinthApi();
    private final ModDownloader downloader = new ModDownloader();
    private PrefManager prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int currentOffset = 0;
    private String currentQuery = "";
    private boolean isLoading = false;

    private static final String[] GAME_VERSIONS = {
    };
    private static final String[] LOADERS = {
        "Any", "fabric", "forge", "neoforge", "quilt"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PrefManager(this);
        initViews();
        setupBottomNav();
        setupFilters();
        setupSearch();
        setupBrowseRecycler();
        setupInstalledRecycler();
        setupSettings();

        showTab("browse");

        // Prompt to set folder if not set
        if (!prefs.hasModsFolder()) {
            showFolderPickerPrompt();
        } else {
            updateFolderLabel();
            searchMods(true);
        }
    }

    private void initViews() {
        layoutBrowse    = findViewById(R.id.layout_browse);
        layoutInstalled = findViewById(R.id.layout_installed);
        layoutSettings  = findViewById(R.id.layout_settings);
        searchInput     = findViewById(R.id.search_input);
        spinnerVersion  = findViewById(R.id.spinner_version);
        spinnerLoader   = findViewById(R.id.spinner_loader);
        browseRecycler  = findViewById(R.id.browse_recycler);
        installedRecycler = findViewById(R.id.installed_recycler);
        emptyBrowse     = findViewById(R.id.empty_browse);
        emptyInstalled  = findViewById(R.id.empty_installed);
        tvFolderPath    = findViewById(R.id.tv_folder_path);
        browseProgress  = findViewById(R.id.browse_progress);
        btnLoadMore     = findViewById(R.id.btn_load_more);
        btnChooseFolder = findViewById(R.id.btn_choose_folder);
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_browse) {
                showTab("browse"); return true;
            } else if (id == R.id.nav_installed) {
                showTab("installed"); refreshInstalled(); return true;
            } else if (id == R.id.nav_settings) {
                showTab("settings"); return true;
            }
            return false;
        });
    }

    private void showTab(String tab) {
        layoutBrowse.setVisibility("browse".equals(tab) ? View.VISIBLE : View.GONE);
        layoutInstalled.setVisibility("installed".equals(tab) ? View.VISIBLE : View.GONE);
        layoutSettings.setVisibility("settings".equals(tab) ? View.VISIBLE : View.GONE);
    }

    private void setupFilters() {
        // Load versions dynamically from Modrinth
        api.getGameVersions(versions -> {
            String[] versionArray = versions.toArray(new String[0]);
            runOnUiThread(() -> {
                ArrayAdapter<String> vAdapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, versionArray);
                vAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerVersion.setAdapter(vAdapter);
                String savedVer = prefs.getGameVersion();
                if (!savedVer.isEmpty()) {
                    int idx = versions.indexOf(savedVer);
                    if (idx >= 0) spinnerVersion.setSelection(idx);
                }
            });
        }, error -> runOnUiThread(() ->
            Toast.makeText(this, "Failed to load versions", Toast.LENGTH_SHORT).show()
        ));

        ArrayAdapter<String> lAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, LOADERS);
        lAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLoader.setAdapter(lAdapter);

        // Restore saved filters
        String savedLoader = prefs.getLoader();
        if (!savedLoader.isEmpty()) {
            int idx = Arrays.asList(LOADERS).indexOf(savedLoader);
            if (idx >= 0) spinnerLoader.setSelection(idx);
        }

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int p, long id) {
                saveFilters();
                searchMods(true);
            }
            public void onNothingSelected(AdapterView<?> a) {}
        };
        spinnerVersion.setOnItemSelectedListener(filterListener);
        spinnerLoader.setOnItemSelectedListener(filterListener);

        btnLoadMore = findViewById(R.id.btn_load_more);
        btnLoadMore.setOnClickListener(v -> searchMods(false));
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            private final Handler h = new Handler(Looper.getMainLooper());
            private Runnable pending;
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (pending != null) h.removeCallbacks(pending);
                pending = () -> searchMods(true);
                h.postDelayed(pending, 500);
            }
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupBrowseRecycler() {
        modAdapter = new ModAdapter(this, modResults, mod -> {
            if (!prefs.hasModsFolder()) {
                showFolderPickerPrompt();
                return;
            }
            showInstallDialog(mod);
        });
        browseRecycler.setLayoutManager(new LinearLayoutManager(this));
        browseRecycler.setAdapter(modAdapter);
    }

    private void setupInstalledRecycler() {
        installedAdapter = new InstalledModsAdapter(installedMods, mod -> {
            new AlertDialog.Builder(this)
                .setTitle("Delete mod?")
                .setMessage("Remove \"" + mod.getName() + "\" from your mods folder?")
                .setPositiveButton("Delete", (d, w) -> {
                    if (mod.delete()) {
                        refreshInstalled();
                        Toast.makeText(this, "Mod removed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        installedRecycler.setLayoutManager(new LinearLayoutManager(this));
        installedRecycler.setAdapter(installedAdapter);
    }

    private void setupSettings() {
        btnChooseFolder.setOnClickListener(v -> openFolderPicker());
        updateFolderLabel();
    }

    private void searchMods(boolean reset) {
        if (isLoading) return;
        if (reset) {
            currentOffset = 0;
            modResults.clear();
            modAdapter.notifyDataSetChanged();
        }

        isLoading = true;
        browseProgress.setVisibility(View.VISIBLE);
        btnLoadMore.setVisibility(View.GONE);
        emptyBrowse.setVisibility(View.GONE);

        currentQuery = searchInput.getText().toString().trim();
        String version = getSelectedVersion();
        String loader  = getSelectedLoader();

        api.searchMods(currentQuery, version, loader, currentOffset, new ModrinthApi.Callback<SearchResponse>() {
            public void onSuccess(SearchResponse result) {
                handler.post(() -> {
                    isLoading = false;
                    browseProgress.setVisibility(View.GONE);

                    if (result.hits != null) {
                        // Mark already-installed mods
                        java.io.File[] installed = getInstalledFiles();
                        for (ModResult mod : result.hits) {
                            mod.isInstalled = isAlreadyInstalled(mod, installed);
                        }
                        modResults.addAll(result.hits);
                        modAdapter.notifyDataSetChanged();
                        currentOffset += result.hits.size();
                        btnLoadMore.setVisibility(
                            currentOffset < result.totalHits ? View.VISIBLE : View.GONE);
                    }

                    emptyBrowse.setVisibility(modResults.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
            public void onError(String error) {
                handler.post(() -> {
                    isLoading = false;
                    browseProgress.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    emptyBrowse.setVisibility(modResults.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
        });
    }

    private void showInstallDialog(ModResult mod) {
        String version = getSelectedVersion();
        String loader  = getSelectedLoader();

        ProgressDialog loading = new ProgressDialog(this);
        loading.setMessage("Fetching versions…");
        loading.show();

        api.getVersions(mod.projectId, version, loader, versions -> {
            handler.post(() -> {
                loading.dismiss();
                if (versions == null || versions.isEmpty()) {
                    Toast.makeText(this,
                        "No compatible versions found for selected filters.",
                        Toast.LENGTH_LONG).show();
                    return;
                }

                // Build version list for dialog
                String[] labels = new String[versions.size()];
                for (int i = 0; i < versions.size(); i++) {
                    ModVersion v = versions.get(i);
                    labels[i] = v.versionNumber + "  (" +
                        String.join(", ", v.gameVersions) + "  |  " +
                        String.join(", ", v.loaders) + ")";
                }

                new AlertDialog.Builder(this)
                    .setTitle("Install: " + mod.title)
                    .setItems(labels, (d, which) -> {
                        ModVersion selected = versions.get(which);
                        ModVersion.VersionFile file = ModDownloader.getPrimaryFile(selected);
                        if (file == null) {
                            Toast.makeText(this, "No download file found.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        startDownload(mod, selected, file);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }, error -> handler.post(() -> {
            loading.dismiss();
            Toast.makeText(this, "Error: " + error, Toast.LENGTH_SHORT).show();
        }));
    }

    private void startDownload(ModResult mod, ModVersion version, ModVersion.VersionFile file) {
        java.io.File modsDir = getModsDir();
        if (modsDir == null) { showFolderPickerPrompt(); return; }

        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Installing " + mod.title);
        progress.setMessage("Downloading…");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();

        downloader.downloadMod(file, modsDir, version.dependencies,
            getSelectedVersion(), getSelectedLoader(),
            new ModDownloader.DownloadCallback() {
                public void onProgress(String fileName, int percent) {
                    handler.post(() -> {
                        progress.setMessage(fileName);
                        progress.setProgress(percent);
                    });
                }
                public void onSuccess(String fileName) {
                    handler.post(() -> {
                        progress.dismiss();
                        Toast.makeText(MainActivity.this,
                            mod.title + " installed!", Toast.LENGTH_SHORT).show();
                        // Mark as installed in list
                        mod.isInstalled = true;
                        modAdapter.notifyDataSetChanged();
                    });
                }
                public void onError(String error) {
                    handler.post(() -> {
                        progress.dismiss();
                        Toast.makeText(MainActivity.this,
                            "Install failed: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
    }

    private void refreshInstalled() {
        installedMods.clear();
        java.io.File[] files = getInstalledFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.getName().endsWith(".jar") || f.getName().endsWith(".zip")) {
                    installedMods.add(f);
                }
            }
        }
        installedAdapter.notifyDataSetChanged();
        emptyInstalled.setVisibility(installedMods.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private java.io.File[] getInstalledFiles() {
        java.io.File dir = getModsDir();
        if (dir == null || !dir.exists()) return null;
        return dir.listFiles();
    }

    private boolean isAlreadyInstalled(ModResult mod, java.io.File[] files) {
        if (files == null) return false;
        for (java.io.File f : files) {
            String name = f.getName().toLowerCase();
            if (name.contains(mod.slug.toLowerCase()) ||
                name.contains(mod.projectId.toLowerCase())) return true;
        }
        return false;
    }

    private java.io.File getModsDir() {
        Uri uri = prefs.getModsUri();
        if (uri == null) return null;
        // Convert SAF URI to a real File path using DocumentFile
        if ("content".equals(uri.getScheme())) {
            android.provider.DocumentsContract.buildDocumentUriUsingTree(uri,
                    android.provider.DocumentsContract.getTreeDocumentId(uri));
            // Get actual path from URI
            String path = getRealPathFromUri(uri);
            if (path != null) return new java.io.File(path);
        }
        if ("file".equals(uri.getScheme())) {
            return new java.io.File(uri.getPath());
        }
        // Fallback to app-internal mods dir
        java.io.File fallback = new java.io.File(getExternalFilesDir(null), ".minecraft/mods");
        fallback.mkdirs();
        return fallback;
    }

    private String getRealPathFromUri(Uri uri) {
        try {
            String docId = android.provider.DocumentsContract.getTreeDocumentId(uri);
            String[] split = docId.split(":");
            if (split.length >= 2) {
                String type = split[0];
                String relativePath = split[1];
                if ("primary".equalsIgnoreCase(type)) {
                    return android.os.Environment.getExternalStorageDirectory() + "/" + relativePath;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("ModVault", "getRealPathFromUri failed: " + e.getMessage());
        }
        return null;
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            prefs.saveModsUri(uri);
            updateFolderLabel();
            Toast.makeText(this, "Mods folder set!", Toast.LENGTH_SHORT).show();
            searchMods(true);
        }
    }

    private void updateFolderLabel() {
        Uri uri = prefs.getModsUri();
        if (tvFolderPath != null) {
            tvFolderPath.setText(uri != null ? uri.getPath() : "No folder selected");
        }
    }

    private void showFolderPickerPrompt() {
        new AlertDialog.Builder(this)
            .setTitle("Choose Mods Folder")
            .setMessage("ModVault needs to know where your /mods folder is. Please select it now.")
            .setPositiveButton("Choose Folder", (d, w) -> openFolderPicker())
            .setNegativeButton("Later", null)
            .show();
    }

    private void saveFilters() {
        prefs.saveFilters(getSelectedVersion(), getSelectedLoader());
    }

    private String getSelectedVersion() {
        String v = (String) spinnerVersion.getSelectedItem();
        return "Any".equals(v) ? "" : v;
    }

    private String getSelectedLoader() {
        String l = (String) spinnerLoader.getSelectedItem();
        return "Any".equals(l) ? "" : l;
    }
}
