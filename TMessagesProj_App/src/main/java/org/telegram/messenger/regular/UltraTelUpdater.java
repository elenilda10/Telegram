package org.telegram.messenger.regular;

import android.app.Activity;
import android.app.Application;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Independent UltraTel updater. Release assets:
 * ultratel-update.json: {"version_code":73049,"version":"13.2.3","notes":"...","sha256":"64 hex","apk_url":"https://github.com/elenilda10/Telegram/releases/download/.../ultratel.apk"}
 * ultratel.apk: signed with the same UltraTel signing key.
 */
public final class UltraTelUpdater {
    private static final String RELEASE_URL =
        "https://api.github.com/repos/elenilda10/Telegram/releases/latest";
    private static final String MANIFEST_NAME = "ultratel-update.json";
    private static final long CHECK_INTERVAL = 6L * 60 * 60 * 1000;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static long lastCheck;
    private static boolean checking;
    private static long downloadId = -1;
    private static BroadcastReceiver receiver;

    private UltraTelUpdater() {}

    public static void check(Activity activity, boolean manual) {
        if (activity == null || activity.isFinishing()) return;
        long now = System.currentTimeMillis();
        if (checking || (!manual && now - lastCheck < CHECK_INTERVAL)) return;
        checking = true;
        lastCheck = now;
        IO.execute(() -> {
            try {
                JSONObject release = readJson(RELEASE_URL);
                JSONArray assets = release.getJSONArray("assets");
                String manifestUrl = null;
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if (MANIFEST_NAME.equals(asset.optString("name"))) {
                        manifestUrl = asset.getString("browser_download_url");
                        break;
                    }
                }
                if (manifestUrl == null) throw new Exception("Release sem manifesto UltraTel");
                JSONObject info = readJson(manifestUrl);
                int newCode = info.getInt("version_code");
                String apkUrl = info.getString("apk_url");
                String sha = info.getString("sha256").toLowerCase(Locale.US);
                if (!isTrustedApkUrl(apkUrl) || !sha.matches("[0-9a-f]{64}")) {
                    throw new SecurityException("Dados de atualização inválidos");
                }
                PackageInfo installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                long installedCode = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
                MAIN.post(() -> {
                    checking = false;
                    if (activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
                    if (newCode <= installedCode) {
                        if (manual) Toast.makeText(activity, "UltraTel já está atualizado", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new AlertDialog.Builder(activity)
                        .setTitle("Atualização do UltraTel")
                        .setMessage("Versão " + info.optString("version", String.valueOf(newCode)) +
                            " disponível.\\n\\n" + info.optString("notes", "Melhorias e correções."))
                        .setNegativeButton("Depois", null)
                        .setPositiveButton("Baixar", (d, which) -> download(activity, apkUrl, sha))
                        .show();
                });
            } catch (Exception e) {
                MAIN.post(() -> {
                    checking = false;
                    if (manual && !activity.isFinishing()) {
                        Toast.makeText(activity, "Não foi possível verificar atualizações", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private static boolean isTrustedApkUrl(String url) {
        try {
            URL parsed = new URL(url);
            return "https".equals(parsed.getProtocol())
                && "github.com".equalsIgnoreCase(parsed.getHost())
                && parsed.getPath().startsWith("/elenilda10/Telegram/releases/download/")
                && parsed.getPath().endsWith("/ultratel.apk");
        } catch (Exception e) { return false; }
    }

    private static JSONObject readJson(String address) throws Exception {
        URL url = new URL(address);
        String host = url.getHost();
        if (!"https".equals(url.getProtocol()) ||
            !("api.github.com".equalsIgnoreCase(host) || "github.com".equalsIgnoreCase(host))) {
            throw new SecurityException("Host de atualização não autorizado");
        }
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "UltraTel-Android-Updater");
        try {
            if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    if (sb.length() > 131072) throw new Exception("Manifesto muito grande");
                    sb.append(line);
                }
                return new JSONObject(sb.toString());
            }
        } finally { conn.disconnect(); }
    }

    private static void download(Activity activity, String apkUrl, String expectedSha) {
        if (downloadId != -1) {
            Toast.makeText(activity, "Download já iniciado", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            String name = "ultratel-" + System.currentTimeMillis() + ".apk";
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("Atualização do UltraTel");
            request.setDescription("Baixando atualização assinada");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, name);
            long id = dm.enqueue(request);
            downloadId = id;
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                    try { context.unregisterReceiver(this); } catch (Exception ignored) {}
                    receiver = null;
                    downloadId = -1;
                    IO.execute(() -> verifyAndInstall(activity, dm, id, expectedSha));
                }
            };
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                activity.registerReceiver(receiver, filter);
            }
            Toast.makeText(activity, "Baixando atualização do UltraTel", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            downloadId = -1;
            Toast.makeText(activity, "Falha ao iniciar download", Toast.LENGTH_LONG).show();
        }
    }

    private static void verifyAndInstall(Activity activity, DownloadManager dm, long id, String sha) {
        try (android.database.Cursor cursor = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst() ||
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) {
                throw new Exception("Download incompleto");
            }
            String path = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
            Uri local = Uri.parse(path);
            File apk = new File(local.getPath());
            if (!apk.isFile() || !sha.equals(hashFile(apk))) {
                if (apk.isFile()) apk.delete();
                throw new SecurityException("SHA-256 da atualização não confere");
            }
            MAIN.post(() -> {
                try {
                    Uri content = FileProvider.getUriForFile(activity,
                        activity.getPackageName() + ".ultratel.updates", apk);
                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setDataAndType(content, "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(install);
                } catch (ActivityNotFoundException | IllegalArgumentException e) {
                    Toast.makeText(activity, "Não foi possível abrir o instalador", Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            MAIN.post(() -> Toast.makeText(activity, "Atualização rejeitada: download ou integridade inválidos", Toast.LENGTH_LONG).show());
        }
    }

    private static String hashFile(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = stream.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format(Locale.US, "%02x", b & 0xff));
        return result.toString();
    }
}
