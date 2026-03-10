package com.cuinstaller.app;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;
import android.os.Environment;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
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
import com.cuinstaller.app.api.CurseForgeApi;
import com.cuinstaller.app.model.ModResult;
import com.cuinstaller.app.model.ModVersion;
import com.cuinstaller.app.model.SearchResponse;
import com.cuinstaller.app.ui.InstalledModsAdapter;
import com.cuinstaller.app.ui.ModAdapter;
import com.cuinstaller.app.ui.InstanceAdapter;
import com.cuinstaller.app.ui.SavedPathsAdapter;
import java.util.ArrayList;
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
    private final List<Object> installedMods = new ArrayList<>();
    private ModAdapter modAdapter;
    private InstalledModsAdapter installedAdapter;

    private final ModrinthApi api = new ModrinthApi();
    private final CurseForgeApi curseForgeApi = new CurseForgeApi();
    private boolean useCurseForge = false;
    private String currentProjectType = "mod";
    private Button btnModrinth, btnCurseForge;
    private Button btnTypeMods, btnTypeResourcepack, btnTypeShader;
    private RecyclerView instancesRecycler;
    private Button btnScanInstances;
    private InstanceAdapter instanceAdapter;
    private final java.util.List<java.io.File> instanceList = new ArrayList<>();
    private RecyclerView savedPathsRecycler;
    private ModDownloader downloader;
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
        downloader = new ModDownloader(this);
        requestStoragePermissionIfNeeded();
        initViews();
        setupBottomNav();
        setupFilters();
        setupSearch();
        setupBrowseRecycler();
        setupSourceToggle();
        setupTypeToggle();
        setupInstalledRecycler();
        setupSettings();
        requestManageStoragePermission();
        setupSavedPaths();
        setupInstances();

        showTab("browse");

        // Prompt to set folder if not set
        if (!prefs.hasModsFolder()) {
            showFolderPickerPrompt();
        } else {
            updateFolderLabel();
            refreshSavedPaths();
            // Wait for versions to load before searching
        }
    }

    private void requestStoragePermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ uses SAF, no runtime permission needed
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 100);
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
        btnModrinth = findViewById(R.id.btn_modrinth);
        instancesRecycler = findViewById(R.id.instances_recycler);
        savedPathsRecycler = findViewById(R.id.saved_paths_recycler);
        btnScanInstances = findViewById(R.id.btn_scan_instances);
        btnCurseForge = findViewById(R.id.btn_curseforge);
        btnTypeMods = findViewById(R.id.btn_type_mods);
        btnTypeResourcepack = findViewById(R.id.btn_type_resourcepack);
        btnTypeShader = findViewById(R.id.btn_type_shader);
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
                if (prefs.hasModsFolder()) {
                    searchMods(true);
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
            boolean ready = false;
            public void onItemSelected(AdapterView<?> a, View v, int p, long id) {
                if (!ready) { ready = true; return; }
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

    private void setupTypeToggle() {
        android.content.res.ColorStateList active = android.content.res.ColorStateList.valueOf(0xFFB87333);
        android.content.res.ColorStateList inactive = android.content.res.ColorStateList.valueOf(0xFF241810);
        btnTypeMods.setOnClickListener(v -> {
            currentProjectType = "mod";
            btnTypeMods.setBackgroundTintList(active); btnTypeMods.setTextColor(0xFFFFFFFF);
            btnTypeResourcepack.setBackgroundTintList(inactive); btnTypeResourcepack.setTextColor(0xFFAAAAAA);
            btnTypeShader.setBackgroundTintList(inactive); btnTypeShader.setTextColor(0xFFAAAAAA);
            searchMods(true);
        });
        btnTypeResourcepack.setOnClickListener(v -> {
            currentProjectType = "resourcepack";
            btnTypeResourcepack.setBackgroundTintList(active); btnTypeResourcepack.setTextColor(0xFFFFFFFF);
            btnTypeMods.setBackgroundTintList(inactive); btnTypeMods.setTextColor(0xFFAAAAAA);
            btnTypeShader.setBackgroundTintList(inactive); btnTypeShader.setTextColor(0xFFAAAAAA);
            searchMods(true);
        });
        btnTypeShader.setOnClickListener(v -> {
            currentProjectType = "shader";
            btnTypeShader.setBackgroundTintList(active); btnTypeShader.setTextColor(0xFFFFFFFF);
            btnTypeMods.setBackgroundTintList(inactive); btnTypeMods.setTextColor(0xFFAAAAAA);
            btnTypeResourcepack.setBackgroundTintList(inactive); btnTypeResourcepack.setTextColor(0xFFAAAAAA);
            searchMods(true);
        });
    }

    private void setupSavedPaths() {
        refreshSavedPaths();
    }

    private void refreshSavedPaths() {
        java.util.List<android.net.Uri> saved = prefs.getSavedPaths();
        android.net.Uri active = prefs.getModsUri();
        if (saved.isEmpty()) {
            savedPathsRecycler.setVisibility(View.GONE);
            return;
        }
        savedPathsRecycler.setVisibility(View.VISIBLE);
        savedPathsRecycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        savedPathsRecycler.setAdapter(new SavedPathsAdapter(this, saved, active, new SavedPathsAdapter.Listener() {
            @Override public void onUse(android.net.Uri uri) {
                prefs.saveModsUri(uri);
                updateFolderLabel();
            refreshSavedPaths();
                refreshSavedPaths();
                Toast.makeText(MainActivity.this, "Switched to saved path", Toast.LENGTH_SHORT).show();
            }
            @Override public void onRemove(android.net.Uri uri) {
                prefs.removeSavedPath(uri);
                updateFolderLabel();
            refreshSavedPaths();
                refreshSavedPaths();
            }
        }));
    }

    private void requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void setupInstances() {
        instanceAdapter = new InstanceAdapter(this, instanceList, (modsFolder, name) -> {
            // Convert File to Uri and save
            android.net.Uri uri = android.net.Uri.fromFile(modsFolder);
            prefs.saveModsUri(uri);
            updateFolderLabel();
            refreshSavedPaths();
            Toast.makeText(this, "Instance set: " + name, Toast.LENGTH_SHORT).show();
        });
        instancesRecycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        instancesRecycler.setAdapter(instanceAdapter);
        btnScanInstances.setOnClickListener(v -> scanForInstances());
        // Auto scan on open
        scanForInstances();
    }

    private void scanForInstances() {
        instanceList.clear();
        // Common launcher paths
        String[] basePaths = {
            android.os.Environment.getExternalStorageDirectory() + "/games/PojavLauncher/custom_instances",
            android.os.Environment.getExternalStorageDirectory() + "/games/CopperLauncher/custom_instances",
            android.os.Environment.getExternalStorageDirectory() + "/games/Amethyst/custom_instances",
            android.os.Environment.getExternalStorageDirectory() + "/Android/data/git.artdeell.mojo/files/instances",
            android.os.Environment.getExternalStorageDirectory() + "/Android/data/PojavLauncher/custom_instances",
            android.os.Environment.getExternalStorageDirectory() + "/Android/data/com.maxjubayeryt.copper.debug/files/custom_instances",
            android.os.Environment.getExternalStorageDirectory() + "/Android/data/net.kdt.pojavlaunch/files/custom_instances",
        };
        for (String path : basePaths) {
            java.io.File dir = new java.io.File(path);
            if (dir.exists() && dir.isDirectory()) {
                java.io.File[] instances = dir.listFiles();
                if (instances != null) {
                    for (java.io.File f : instances) {
                        if (f.isDirectory()) instanceList.add(f);
                    }
                }
            }
        }
        instanceAdapter.notifyDataSetChanged();
        if (instanceList.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("No instances found")
                    .setMessage("On Android 11+, launcher files in Android/data/ can't be accessed automatically.\n\nTap 'Browse' to manually navigate to your launcher's custom_instances folder.")
                    .setPositiveButton("Browse Android/data", (d, w) -> {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        intent.putExtra("android.provider.extra.INITIAL_URI",
                            android.provider.DocumentsContract.buildDocumentUri(
                                "com.android.externalstorage.documents", "primary:Android/data"));
                        startActivityForResult(intent, 42);
                    })
                    .setNegativeButton("Use Manual Picker", (d, w) -> btnChooseFolder.performClick())
                    .show();
            } else {
                Toast.makeText(this, "No instances found. Choose folder manually.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupSourceToggle() {
        btnModrinth.setOnClickListener(v -> {
            useCurseForge = false;
            btnModrinth.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFB87333));
            btnModrinth.setTextColor(0xFFFFFFFF);
            btnCurseForge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF241810));
            btnCurseForge.setTextColor(0xFFAAAAAA);
            searchMods(true);
        });
        btnCurseForge.setOnClickListener(v -> {
            useCurseForge = true;
            btnCurseForge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFB87333));
            btnCurseForge.setTextColor(0xFFFFFFFF);
            btnModrinth.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF241810));
            btnModrinth.setTextColor(0xFFAAAAAA);
            searchMods(true);
        });
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
            String modName = (mod instanceof androidx.documentfile.provider.DocumentFile)
                ? ((androidx.documentfile.provider.DocumentFile) mod).getName()
                : ((java.io.File) mod).getName();
            new AlertDialog.Builder(this)
                .setTitle("Delete mod?")
                .setMessage("Remove \"" + modName + "\" from your mods folder?")
                .setPositiveButton("Delete", (d, w) -> {
                    boolean deleted = (mod instanceof androidx.documentfile.provider.DocumentFile)
                        ? ((androidx.documentfile.provider.DocumentFile) mod).delete()
                        : ((java.io.File) mod).delete();
                    if (deleted) {
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
            refreshSavedPaths();
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

        if (useCurseForge) {
            curseForgeApi.searchMods(currentQuery, version, loader, currentOffset, currentProjectType, results -> {
                runOnUiThread(() -> {
                    browseProgress.setVisibility(android.view.View.GONE);
                    isLoading = false;
                    if (reset) { modAdapter.getMods().clear(); modAdapter.notifyDataSetChanged(); }
                    if (results.isEmpty()) {
                        if (modAdapter.getItemCount() == 0) emptyBrowse.setVisibility(android.view.View.VISIBLE);
                        btnLoadMore.setVisibility(android.view.View.GONE);
                    } else {
                        emptyBrowse.setVisibility(android.view.View.GONE);
                        modAdapter.getMods().addAll(results); modAdapter.notifyDataSetChanged();
                        currentOffset += results.size();
                        btnLoadMore.setVisibility(android.view.View.VISIBLE);
                    }
                });
            }, error -> runOnUiThread(() -> {
                browseProgress.setVisibility(android.view.View.GONE);
                isLoading = false;
                Toast.makeText(this, "CurseForge error: " + error, Toast.LENGTH_SHORT).show();
            }));
            return;
        }
        api.searchMods(currentQuery, version, loader, currentOffset, currentProjectType, new ModrinthApi.Callback<SearchResponse>() {
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

        if ("curseforge".equals(mod.source)) {
            curseForgeApi.getLatestFile(mod.projectId, version, loader, fileObj -> {
                handler.post(() -> {
                    loading.dismiss();
                    String fileId = fileObj.get("id").getAsString();
                    String fileName = fileObj.get("fileName").getAsString();
                    curseForgeApi.getDownloadUrl(mod.projectId, fileId, url -> {
                        handler.post(() -> {
                            new AlertDialog.Builder(this)
                                .setTitle("Install: " + mod.title)
                                .setMessage(fileName)
                                .setPositiveButton("Install", (d, w) -> {
                                    ModVersion.VersionFile file = new ModVersion.VersionFile();
                                    file.url = url;
                                    file.filename = fileName;
                                    ModVersion fakeVersion = new ModVersion();
                                    fakeVersion.versionNumber = fileName;
                                    fakeVersion.dependencies = new java.util.ArrayList<>();
                                    startDownload(mod, fakeVersion, file);
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        });
                    }, error2 -> handler.post(() ->
                        Toast.makeText(this, "CF Error: " + error2, Toast.LENGTH_SHORT).show()
                    ));
                });
            }, error -> handler.post(() -> {
                loading.dismiss();
                Toast.makeText(this, "CF Error: " + error, Toast.LENGTH_SHORT).show();
            }));
            return;
        }
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
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Installing " + mod.title);
        progress.setMessage("Downloading…");
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
                    Toast.makeText(MainActivity.this, mod.title + " installed!", Toast.LENGTH_SHORT).show();
                    mod.isInstalled = true;
                    modAdapter.notifyDataSetChanged();
                });
            }
            public void onError(String error) {
                handler.post(() -> {
                    progress.dismiss();
                    Toast.makeText(MainActivity.this, "Install failed: " + error, Toast.LENGTH_LONG).show();
                });
            }
        };

        Uri modsDirUri = prefs.getModsUri();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && modsDirUri != null) {
            androidx.documentfile.provider.DocumentFile targetDir = getTargetDocumentDir();
            if (targetDir == null) { progress.dismiss(); showFolderPickerPrompt(); return; }
            downloader.downloadMod(file, targetDir.getUri(), version.dependencies,
                getSelectedVersion(), getSelectedLoader(), callback);
        } else {
            java.io.File modsDir = getTargetDir();
            if (modsDir == null) { progress.dismiss(); showFolderPickerPrompt(); return; }
            downloader.downloadMod(file, modsDir, version.dependencies,
                getSelectedVersion(), getSelectedLoader(), callback);
        }
    }
    private void refreshInstalled() {
        installedMods.clear();
        Uri modsUri = prefs.getModsUri();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && modsUri != null) {
            androidx.documentfile.provider.DocumentFile dir =
                androidx.documentfile.provider.DocumentFile.fromTreeUri(this, modsUri);
            if (dir != null && dir.exists()) {
                for (androidx.documentfile.provider.DocumentFile f : dir.listFiles()) {
                    String name = f.getName();
                    if (name != null && (name.endsWith(".jar") || name.endsWith(".zip"))) {
                        installedMods.add(f);
                    }
                }
            }
            installedAdapter.notifyDataSetChanged();
            emptyInstalled.setVisibility(installedMods.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }
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

    private java.io.File getTargetDir() {
        java.io.File base = getModsDir();
        if (base == null) return null;
        java.io.File target;
        if ("resourcepack".equals(currentProjectType)) {
            target = new java.io.File(base.getParent(), "resourcepacks");
        } else if ("shader".equals(currentProjectType)) {
            target = new java.io.File(base.getParent(), "shaderpacks");
        } else {
            return base;
        }
        if (!target.exists()) target.mkdirs();
        return target;
    }

    // SAF version of getTargetDir - returns DocumentFile for the correct subfolder
    private androidx.documentfile.provider.DocumentFile getTargetDocumentDir() {
        Uri modsUri = prefs.getModsUri();
        if (modsUri == null) return null;
        androidx.documentfile.provider.DocumentFile modsDir =
            androidx.documentfile.provider.DocumentFile.fromTreeUri(this, modsUri);
        if (modsDir == null || !modsDir.exists()) return null;
        if (!"resourcepack".equals(currentProjectType) && !"shader".equals(currentProjectType)) {
            return modsDir;
        }
        String targetFolder = "resourcepack".equals(currentProjectType) ? "resourcepacks" : "shaderpacks";
        try {
            String treeId = android.provider.DocumentsContract.getTreeDocumentId(modsUri);
            // treeId example: "primary:games/CopperLauncher/instances/1.21/.minecraft/mods"
            // Replace the last path segment (mods) with the target folder
            String parentId;
            if (treeId.contains("/")) {
                parentId = treeId.substring(0, treeId.lastIndexOf('/'));
            } else if (treeId.contains(":")) {
                parentId = treeId.substring(0, treeId.lastIndexOf(':') + 1);
            } else {
                return modsDir;
            }
            String targetId = parentId + "/" + targetFolder;
            // Try to find existing folder as a document
            Uri parentDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(modsUri, parentId);
            Uri targetDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(modsUri, targetId);
            // Check if it exists
            try (android.database.Cursor c = getContentResolver().query(targetDocUri,
                    new String[]{android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                    null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    // Exists - build a tree URI for it
                    Uri targetTreeUri = android.provider.DocumentsContract.buildTreeDocumentUri(
                        modsUri.getAuthority(), targetId);
                    return androidx.documentfile.provider.DocumentFile.fromTreeUri(this, targetTreeUri);
                }
            } catch (Exception ignored) {}
            // Doesn't exist - create it
            Uri created = android.provider.DocumentsContract.createDocument(
                getContentResolver(), parentDocUri,
                android.provider.DocumentsContract.Document.MIME_TYPE_DIR, targetFolder);
            if (created != null) {
                Uri createdTreeUri = android.provider.DocumentsContract.buildTreeDocumentUri(
                    modsUri.getAuthority(), targetId);
                return androidx.documentfile.provider.DocumentFile.fromTreeUri(this, createdTreeUri);
            }
        } catch (Exception e) {
            android.util.Log.e("ModVault", "getTargetDocumentDir failed: " + e.getMessage());
        }
        // Fallback: use mods folder
        return modsDir;
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
            refreshSavedPaths();
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
