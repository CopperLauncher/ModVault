package com.cuinstaller.app.utils;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PrefManager {
    private static final String PREFS = "cuinstaller_prefs";
    private static final String KEY_MODS_URI = "mods_folder_uri";
    private static final String KEY_GAME_VER = "game_version";
    private static final String KEY_LOADER   = "mod_loader";
    private static final String KEY_SAVED_PATHS = "saved_mod_paths";
    private static final int MAX_SAVED = 10;

    private final SharedPreferences prefs;

    public PrefManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveModsUri(Uri uri) {
        if (uri == null) return;
        String uriStr = uri.toString();
        prefs.edit().putString(KEY_MODS_URI, uriStr).apply();
        // Also add to saved paths list
        Set<String> saved = new LinkedHashSet<>(getSavedPathStrings());
        saved.add(uriStr);
        // Keep max 10, remove oldest if over limit
        List<String> list = new ArrayList<>(saved);
        while (list.size() > MAX_SAVED) list.remove(0);
        prefs.edit().putStringSet(KEY_SAVED_PATHS, new LinkedHashSet<>(list)).apply();
    }

    public Uri getModsUri() {
        String s = prefs.getString(KEY_MODS_URI, null);
        return s != null ? Uri.parse(s) : null;
    }

    public List<Uri> getSavedPaths() {
        List<Uri> uris = new ArrayList<>();
        for (String s : getSavedPathStrings()) uris.add(Uri.parse(s));
        return uris;
    }

    public void removeSavedPath(Uri uri) {
        Set<String> saved = new LinkedHashSet<>(getSavedPathStrings());
        saved.remove(uri.toString());
        prefs.edit().putStringSet(KEY_SAVED_PATHS, saved).apply();
        // If removed active path, clear it
        if (uri.equals(getModsUri())) {
            prefs.edit().remove(KEY_MODS_URI).apply();
        }
    }

    private Set<String> getSavedPathStrings() {
        return prefs.getStringSet(KEY_SAVED_PATHS, new LinkedHashSet<>());
    }

    public void saveFilters(String gameVersion, String loader) {
        prefs.edit()
             .putString(KEY_GAME_VER, gameVersion)
             .putString(KEY_LOADER, loader)
             .apply();
    }
    public String getGameVersion() { return prefs.getString(KEY_GAME_VER, ""); }
    public String getLoader()      { return prefs.getString(KEY_LOADER, ""); }
    public boolean hasModsFolder() { return getModsUri() != null; }
}
