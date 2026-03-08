package com.cuinstaller.app.api;

import com.cuinstaller.app.model.ModResult;
import com.cuinstaller.app.model.ModVersion;
import com.cuinstaller.app.model.SearchResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ModrinthApi {

    private static final String BASE = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "CuInstaller/1.0 (github.com/copperlauncher)";

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    /** Search mods on Modrinth */
    public void searchMods(String query, String gameVersion, String loader,
                           int offset, Callback<SearchResponse> callback) {
        new Thread(() -> {
            try {
                StringBuilder url = new StringBuilder(BASE + "/search?facets=[[\"project_type:mod\"]]");
                url.append("&query=").append(query.isEmpty() ? "" : encode(query));
                url.append("&limit=20&offset=").append(offset);

                if (!gameVersion.isEmpty()) {
                    url = new StringBuilder(BASE + "/search");
                    url.append("?facets=[[\"project_type:mod\"],[\"versions:").append(gameVersion).append("\"]");
                    if (!loader.isEmpty()) {
                        url.append(",[\"categories:").append(loader).append("\"]");
                    }
                    url.append("]");
                    url.append("&query=").append(query.isEmpty() ? "" : encode(query));
                    url.append("&limit=20&offset=").append(offset);
                } else if (!loader.isEmpty()) {
                    url = new StringBuilder(BASE + "/search");
                    url.append("?facets=[[\"project_type:mod\"],[\"categories:").append(loader).append("\"]]");
                    url.append("&query=").append(query.isEmpty() ? "" : encode(query));
                    url.append("&limit=20&offset=").append(offset);
                }

                Request request = new Request.Builder()
                        .url(url.toString())
                        .header("User-Agent", USER_AGENT)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        callback.onError("Server error: " + response.code());
                        return;
                    }
                    String body = response.body().string();
                    SearchResponse result = gson.fromJson(body, SearchResponse.class);
                    callback.onSuccess(result);
                }
            } catch (IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }
        }).start();
    }

    /** Get versions for a project, filtered by game version and loader */
    public void getVersions(String projectId, String gameVersion, String loader,
                            Callback<List<ModVersion>> callback) {
        new Thread(() -> {
            try {
                StringBuilder url = new StringBuilder(BASE + "/project/" + projectId + "/version");
                boolean hasParam = false;
                if (!gameVersion.isEmpty()) {
                    url.append("?game_versions=%5B%22").append(gameVersion).append("%22%5D");
                    hasParam = true;
                }
                if (!loader.isEmpty()) {
                    url.append(hasParam ? "&" : "?");
                    url.append("loaders=%5B%22").append(loader).append("%22%5D");
                }

                Request request = new Request.Builder()
                        .url(url.toString())
                        .header("User-Agent", USER_AGENT)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        callback.onError("Server error: " + response.code());
                        return;
                    }
                    String body = response.body().string();
                    List<ModVersion> versions = gson.fromJson(body,
                            new TypeToken<List<ModVersion>>(){}.getType());
                    callback.onSuccess(versions);
                }
            } catch (IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }
        }).start();
    }

    /** Get a single project by ID to get its details */
    public void getProject(String projectId, Callback<ModResult> callback) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(BASE + "/project/" + projectId)
                        .header("User-Agent", USER_AGENT)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        callback.onError("Server error: " + response.code());
                        return;
                    }
                    String body = response.body().string();
                    ModResult result = gson.fromJson(body, ModResult.class);
                    callback.onSuccess(result);
                }
            } catch (IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }
        }).start();
    }

    private String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
