package com.cuinstaller.app.utils;

import android.content.Context;
import android.net.Uri;

import com.cuinstaller.app.api.ModrinthApi;
import com.cuinstaller.app.model.ModVersion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ModDownloader {

    public interface DownloadCallback {
        void onProgress(String fileName, int percent);
        void onSuccess(String fileName);
        void onError(String error);
    }

    private final OkHttpClient client = new OkHttpClient();
    private final ModrinthApi api = new ModrinthApi();

    /**
     * Download a mod file from a URL into the given mods directory.
     * Also resolves required dependencies.
     */
    public void downloadMod(ModVersion.VersionFile file, File modsDir,
                            List<ModVersion.Dependency> dependencies,
                            String gameVersion, String loader,
                            DownloadCallback callback) {
        new Thread(() -> {
            try {
                // Download the main mod file
                downloadFile(file.url, file.filename, modsDir, callback);

                // Handle required dependencies
                if (dependencies != null) {
                    for (ModVersion.Dependency dep : dependencies) {
                        if ("required".equals(dep.dependencyType) && dep.projectId != null) {
                            // Check if dependency is already installed
                            File depCheck = findInstalledMod(modsDir, dep.projectId);
                            if (depCheck != null) continue;

                            // Fetch and download dependency
                            api.getVersions(dep.projectId, gameVersion, loader, versions -> {
                                if (versions != null && !versions.isEmpty()) {
                                    ModVersion depVersion = versions.get(0);
                                    if (depVersion.files != null && !depVersion.files.isEmpty()) {
                                        ModVersion.VersionFile depFile = getPrimaryFile(depVersion.files);
                                        try {
                                            downloadFile(depFile.url, depFile.filename, modsDir,
                                                    new DownloadCallback() {
                                                        public void onProgress(String f, int p) {}
                                                        public void onSuccess(String f) {
                                                            callback.onProgress("Dependency: " + f, 100);
                                                        }
                                                        public void onError(String e) {}
                                                    });
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }, error -> {});
                        }
                    }
                }

            } catch (Exception e) {
                callback.onError("Download failed: " + e.getMessage());
            }
        }).start();
    }

    private void downloadFile(String url, String fileName, File dir, DownloadCallback callback)
            throws Exception {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new Exception("HTTP " + response.code());

            long total = response.body().contentLength();
            File outFile = new File(dir, fileName);

            try (InputStream in = response.body().byteStream();
                 FileOutputStream out = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                long downloaded = 0;
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                    downloaded += read;
                    if (total > 0) {
                        int percent = (int) (downloaded * 100 / total);
                        callback.onProgress(fileName, percent);
                    }
                }
            }
            callback.onSuccess(fileName);
        }
    }

    private ModVersion.VersionFile getPrimaryFile(List<ModVersion.VersionFile> files) {
        for (ModVersion.VersionFile f : files) {
            if (f.primary) return f;
        }
        return files.get(0);
    }

    /** Check if a mod with matching project ID is already in mods folder */
    private File findInstalledMod(File modsDir, String projectId) {
        File[] files = modsDir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.getName().contains(projectId)) return f;
        }
        return null;
    }

    public static ModVersion.VersionFile getPrimaryFile(ModVersion version) {
        if (version.files == null || version.files.isEmpty()) return null;
        for (ModVersion.VersionFile f : version.files) {
            if (f.primary) return f;
        }
        return version.files.get(0);
    }
}
