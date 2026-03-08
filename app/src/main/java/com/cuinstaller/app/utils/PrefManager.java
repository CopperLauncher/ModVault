package com.cuinstaller.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

public class PrefManager {
    private static final String PREFS = "cuinstaller_prefs";
    private static final String KEY_MODS_URI = "mods_folder_uri";
    private static final String KEY_GAME_VER = "game_version";
    private static final String KEY_LOADER   = "mod_loader";

    private final SharedPreferences prefs;

    public PrefManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveModsUri(Uri uri) {
        prefs.edit().putString(KEY_MODS_URI, uri != null ? uri.toString() : null).apply();
    }

    public Uri getModsUri() {
        String s = prefs.getString(KEY_MODS_URI, null);
        return s != null ? Uri.parse(s) : null;
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
