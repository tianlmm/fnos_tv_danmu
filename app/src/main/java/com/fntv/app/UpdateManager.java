package com.fntv.app;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 升级管理器 — 检查更新、下载 APK、安装 */
public class UpdateManager {
    private boolean isTvDevice() {
        android.app.UiModeManager uiModeManager = (android.app.UiModeManager) activity.getSystemService(android.content.Context.UI_MODE_SERVICE);
        return uiModeManager != null
                && uiModeManager.getCurrentModeType() == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
    }


    private final Activity activity;
    private final Button btnCheckUpdate;
    private final int currentVersionCode;

    /** 更新源（按优先级） */
    private static final String[] UPDATE_URLS = {
            "https://cdn.jsdelivr.net/gh/tianlmm/fnos_tv_danmu@master/update.json",
            "https://fastly.jsdelivr.net/gh/tianlmm/fnos_tv_danmu@master/update.json",
            "https://raw.githubusercontent.com/tianlmm/fnos_tv_danmu/master/update.json"
    };

    private static final String TAG = "Update";
    private static final String RELEASES_URL = "https://github.com/tianlmm/fnos_tv_danmu/releases";

    public UpdateManager(Activity activity, Button btnCheckUpdate, int currentVersionCode) {
        this.activity = activity;
        this.btnCheckUpdate = btnCheckUpdate;
        this.currentVersionCode = currentVersionCode;
    }

    /** 初始化绑定按钮点击 */
    public void setup() {
        btnCheckUpdate.setOnClickListener(v -> checkUpdate());
    }

    private void resetBtn() {
        btnCheckUpdate.setEnabled(true);
        btnCheckUpdate.setText("检查更新");
    }

    // ========== 检查更新 ==========

    public void checkUpdate() {
        btnCheckUpdate.setEnabled(false);
        btnCheckUpdate.setText("检查中...");
        new Thread(() -> {
            try {
                org.json.JSONObject json = null;
                String usedUrl = "";
                String ts = new java.text.SimpleDateFormat("yyyyMMddHHmm", java.util.Locale.CHINA).format(new java.util.Date());
                for (String url : UPDATE_URLS) {
                    try {
                        // 所有源都带时间戳，避免 raw / jsDelivr 的 CDN 缓存返回旧版本
                        url = url + (url.contains("?") ? "&" : "?") + "t=" + ts;
                        Log.d(TAG, "尝试源: " + url);
                        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                        c.setConnectTimeout(8000);
                        c.setReadTimeout(8000);
                        c.setInstanceFollowRedirects(true);
                        c.connect();
                        int code = c.getResponseCode();
                        Log.d(TAG, "响应码: " + code + " 来自: " + url);
                        if (code == 200) {
                            java.io.BufferedReader r = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
                            StringBuilder sb = new StringBuilder();
                            String l;
                            while ((l = r.readLine()) != null) sb.append(l);
                            r.close();
                            org.json.JSONObject cand = new org.json.JSONObject(sb.toString());
                            int candV = cand.optInt("versionCode", 0);
                            // 不 break：不同源可能命中不同缓存，取 versionCode 最大的那个
                            if (json == null || candV > json.optInt("versionCode", 0)) {
                                json = cand;
                                usedUrl = url;
                            }
                        } else {
                            try {
                                java.io.BufferedReader er = new java.io.BufferedReader(
                                        new java.io.InputStreamReader(c.getErrorStream(), "UTF-8"));
                                StringBuilder eb = new StringBuilder();
                                String el;
                                while ((el = er.readLine()) != null) eb.append(el);
                                er.close();
                                Log.w(TAG, "错误响应: " + eb);
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "源 \"" + url + "\" 失败: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }

                if (json == null) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "检查更新失败，无法连接更新服务器", Toast.LENGTH_LONG).show();
                        resetBtn();
                    });
                    return;
                }

                int remoteVersion = json.optInt("versionCode", 0);
                final String versionName = json.optString("versionName", "");
                final String apkUrl = json.optString("apkUrl", "");
                final String remoteSha256 = json.optString("sha256", "");
                final java.util.List<String> apkUrls = new java.util.ArrayList<>();
                org.json.JSONArray urlArr = json.optJSONArray("apkUrls");
                if (urlArr != null) {
                    for (int i = 0; i < urlArr.length(); i++) {
                        String u = urlArr.optString(i, "");
                        if (u != null && !u.isEmpty()) apkUrls.add(u);
                    }
                }
                if (apkUrl != null && !apkUrl.isEmpty() && !apkUrls.contains(apkUrl)) {
                    apkUrls.add(0, apkUrl);
                }
                final String changelog = json.optString("changelog", "暂无更新说明");
                final boolean forceUpdate = json.optBoolean("forceUpdate", false);

                Log.d(TAG, "远程: v" + remoteVersion + " 本地: v" + currentVersionCode + " 来源: " + usedUrl);

                final boolean isTestInstall = BuildConfig.DEBUG && remoteVersion <= currentVersionCode;
                if (remoteVersion <= currentVersionCode && !isTestInstall) {
                    final int rv = remoteVersion;
                    final String rn = versionName;
                    activity.runOnUiThread(() -> showUpToDateDialog(rn, rv));
                    return;
                }

                final int fRemoteVersion = remoteVersion;
                final String label = isTestInstall ? "测试安装（版本号不高于当前）" : "发现新版本 v" + versionName;
                activity.runOnUiThread(() ->
                        showUpdateDialog(label, changelog, forceUpdate, apkUrls, fRemoteVersion, isTestInstall, remoteSha256));

            } catch (Exception e) {
                Log.e(TAG, "检查更新异常", e);
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, "检查更新失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    resetBtn();
                });
            }
        }).start();
    }

    // ========== 更新弹窗 ==========

    private void showUpdateDialog(final String title, final String changelog,
                                   final boolean forceUpdate, final java.util.List<String> apkUrls,
                                   final int remoteVersion, final boolean isTestInstall,
                                   final String sha256) {
        new android.app.AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage("当前版本：" + currentVersionName() + "（" + currentVersionCode + "）\n\n" + changelog)
                .setCancelable(!forceUpdate)
                .setPositiveButton(isTestInstall ? "测试安装器" : "立即更新", (dialog, which) -> {
                    dialog.dismiss();
                    downloadAndInstall(apkUrls, remoteVersion, isTestInstall, sha256);
                })
                .setNegativeButton(forceUpdate ? "退出应用" : "稍后再说", (dialog, which) -> {
                    if (forceUpdate) {
                        activity.finishAffinity();
                    } else {
                        dialog.dismiss();
                        resetBtn();
                    }
                })
                .show();
    }

    private String currentVersionName() {
        try {
            return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private static String nowText() {
        return new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.CHINA)
                .format(new java.util.Date());
    }

    /** 已是最新：显示当前版本、GitHub 最新版本、上次/本次检查时间 */
    private void showUpToDateDialog(String remoteName, int remoteVersion) {
        android.content.SharedPreferences p = activity.getSharedPreferences(
                "fntv_prefs", android.content.Context.MODE_PRIVATE);
        String last = p.getString("last_update_check", "（首次检查）");
        String now = nowText();
        p.edit().putString("last_update_check", now).apply();
        resetBtn();

        String msg = "当前版本：" + currentVersionName() + "（" + currentVersionCode + "）\n"
                + "GitHub 最新：" + (remoteName == null || remoteName.isEmpty() ? "未知" : remoteName)
                + "（" + remoteVersion + "）\n\n"
                + "✓ 已是最新版本\n\n"
                + "· 上次检查：" + last + "\n"
                + "· 本次检查：" + now;
        new android.app.AlertDialog.Builder(activity)
                .setTitle("检查更新")
                .setMessage(msg)
                .setPositiveButton("查看 Release", (d, w) -> {
                    try {
                        activity.startActivity(new android.content.Intent(
                                android.content.Intent.ACTION_VIEW, android.net.Uri.parse(RELEASES_URL)));
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    // ========== 下载 ==========

    private void downloadAndInstall(final java.util.List<String> apkUrls, final int remoteVersion,
                                    final boolean isTestInstall, final String sha256) {
        btnCheckUpdate.setText(isTestInstall ? "测试中..." : "下载中...");
        btnCheckUpdate.setEnabled(false);
        new Thread(() -> {
            File dir = new File(activity.getExternalFilesDir(null), "download");
            if (!dir.exists()) dir.mkdirs();
            final File apkFile = new File(dir, "FNTV_v" + remoteVersion + ".apk");

            // ① 并行测速：每个源发 HEAD，按响应时间排序，快的先下
            activity.runOnUiThread(() -> btnCheckUpdate.setText("测速中..."));
            java.util.List<String> ordered = sortByLatency(apkUrls);
            AppLog.d(TAG, "下载顺序: " + ordered);

            String lastError = "";
            for (int idx = 0; idx < ordered.size(); idx++) {
                final String url = ordered.get(idx);
                final int srcNo = idx + 1;
                try {
                    AppLog.d(TAG, "下载源 " + srcNo + "/" + ordered.size() + ": " + url);
                    if (downloadOne(url, apkFile, srcNo, ordered.size())) {
                        finishDownload(apkFile, remoteVersion, isTestInstall, sha256);
                        return;
                    }
                    lastError = "源 " + srcNo + " 下载不完整";
                } catch (Exception e) {
                    lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                    AppLog.e(TAG, "源 " + srcNo + " 失败: " + lastError);
                }
            }
            final String err = lastError;
            activity.runOnUiThread(() -> {
                Toast.makeText(activity, "下载失败：" + err + "（可用浏览器打开 Release 页面手动下载）",
                        Toast.LENGTH_LONG).show();
                resetBtn();
            });
        }).start();
    }

    /** 并行 HEAD 测速，按耗时升序返回（失败的排最后） */
    private java.util.List<String> sortByLatency(java.util.List<String> urls) {
        final java.util.Map<String, Long> cost = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.List<Thread> ts = new java.util.ArrayList<>();
        for (final String u : urls) {
            Thread t = new Thread(() -> {
                long t0 = System.currentTimeMillis();
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                    c.setRequestMethod("HEAD");
                    c.setConnectTimeout(3000);
                    c.setReadTimeout(3000);
                    c.setInstanceFollowRedirects(true);
                    int code = c.getResponseCode();
                    cost.put(u, code == 200 ? System.currentTimeMillis() - t0 : Long.MAX_VALUE);
                } catch (Exception e) {
                    cost.put(u, Long.MAX_VALUE);
                }
            });
            t.start();
            ts.add(t);
        }
        for (Thread t : ts) {
            try { t.join(3500); } catch (InterruptedException ignored) {}
        }
        java.util.List<String> out = new java.util.ArrayList<>(urls);
        out.sort((a, b) -> Long.compare(
                cost.getOrDefault(a, Long.MAX_VALUE), cost.getOrDefault(b, Long.MAX_VALUE)));
        return out;
    }

    /** 下载单个源，支持断点续传；成功返回 true（文件头为 PK） */
    private boolean downloadOne(String apkUrl, File apkFile, int srcNo, int srcTotal) throws Exception {
        long have = apkFile.exists() ? apkFile.length() : 0;
        HttpURLConnection c = (HttpURLConnection) new URL(apkUrl).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
        c.connect();
        int respCode = c.getResponseCode();
        boolean resumed = (respCode == 206 && have > 0);
        if (respCode != 200 && respCode != 206) {
            AppLog.d(TAG, "源 " + srcNo + " 返回 " + respCode);
            return false;
        }
        if (!resumed && apkFile.exists()) {
            apkFile.delete();
            have = 0;
        }
        long total = 0;
        String cr = c.getHeaderField("Content-Range");   // bytes start-end/total
        if (cr != null && cr.contains("/")) {
            try { total = Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1).trim()); } catch (Exception ignored) {}
        }
        if (total <= 0) {
            long len = c.getContentLengthLong() > 0 ? c.getContentLengthLong() : c.getContentLength();
            total = len + have;
        }
        AppLog.d(TAG, "源 " + srcNo + " 开始下载，已有 " + have + "，总大小 " + total + "，续传=" + resumed);

        InputStream is = c.getInputStream();
        FileOutputStream fos = new FileOutputStream(apkFile, resumed);
        byte[] buf = new byte[8192];
        int n;
        long read = have;
        int lastPct = -1;
        long lastUi = 0;
        while ((n = is.read(buf)) != -1) {
            fos.write(buf, 0, n);
            read += n;
            long now = System.currentTimeMillis();
            int pct = total > 0 ? (int) (read * 100 / total) : -1;
            if (pct != lastPct || now - lastUi > 300) {
                lastPct = pct;
                lastUi = now;
                final String label = (total > 0
                        ? "下载中 " + pct + "%（" + (read / 1024 / 1024) + "/" + (total / 1024 / 1024) + "MB）"
                        : "下载中 " + (read / 1024 / 1024) + "MB")
                        + (srcTotal > 1 ? " · 源" + srcNo : "");
                activity.runOnUiThread(() -> btnCheckUpdate.setText(label));
            }
        }
        fos.close();
        is.close();
        if (total > 0 && apkFile.length() < total) {
            AppLog.d(TAG, "源 " + srcNo + " 只下载了 " + apkFile.length() + "/" + total + "（下次可续传）");
            return false;
        }
        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(apkFile, "r");
        byte[] header = new byte[4];
        raf.read(header);
        raf.close();
        String headerStr = new String(header, "UTF-8");
        AppLog.d(TAG, "APK 文件头: " + headerStr + " (" + bytesToHex(header) + ")");
        return headerStr.startsWith("PK");
    }

    private void finishDownload(final File apkFile, final int remoteVersion,
                                final boolean isTestInstall, final String sha256) {
        activity.runOnUiThread(() -> btnCheckUpdate.setText("校验中..."));
        Log.d(TAG, "下载完成: " + apkFile.getAbsolutePath() + " (" + apkFile.length() + " bytes)");
        if (sha256 != null && !sha256.isEmpty()) {
            String actual = sha256Of(apkFile);
            AppLog.d(TAG, "sha256 期望=" + sha256 + " 实际=" + actual);
            if (!sha256.equalsIgnoreCase(actual)) {
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, "安装包校验失败（sha256 不匹配），已删除", Toast.LENGTH_LONG).show();
                    resetBtn();
                });
                apkFile.delete();
                return;
            }
        }
        if (!verifyApkSignature(apkFile)) {
            activity.runOnUiThread(() -> {
                Toast.makeText(activity, "APK 签名校验失败，可能已被篡改", Toast.LENGTH_LONG).show();
                resetBtn();
            });
            return;
        }
        activity.runOnUiThread(() -> installApk(apkFile, isTestInstall));
    }

    // ========== APK 安装（四级降级） ==========

    private void installApk(File apkFile, boolean testOnly) {
        try {
            // 1. ACTION_INSTALL_PACKAGE（Android 8+，弹出系统安装界面）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    if (!activity.getPackageManager().canRequestPackageInstalls()) {
                        Intent permIntent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        permIntent.setData(android.net.Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(permIntent);
                        Toast.makeText(activity, "请允许安装未知来源应用后重试", Toast.LENGTH_LONG).show();
                        resetBtn();
                        return;
                    }
                    android.net.Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                            activity, activity.getPackageName() + ".fileprovider", apkFile);
                    Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                    install.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(install);
                    if (testOnly) {
                        Toast.makeText(activity, "测试成功：ACTION_INSTALL_PACKAGE 可用", Toast.LENGTH_LONG).show();
                        resetBtn();
                    }
                    return;
                } catch (android.content.ActivityNotFoundException e2) {
                    String errSuffix2 = testOnly ? "（测试结束）" : "，尝试 ACTION_VIEW";
                    Log.w(TAG, "ACTION_INSTALL_PACKAGE 不可用" + errSuffix2);
                    if (testOnly) {
                        Toast.makeText(activity, "测试：ACTION_INSTALL_PACKAGE 不可用", Toast.LENGTH_LONG).show();
                        resetBtn();
                        return;
                    }
                }
            }

            // 3. ACTION_VIEW
            try {
                Intent install = new Intent(Intent.ACTION_VIEW);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    android.net.Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                            activity, activity.getPackageName() + ".fileprovider", apkFile);
                    install.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    install.setDataAndType(android.net.Uri.fromFile(apkFile), "application/vnd.android.package-archive");
                }
                install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(install);
                if (testOnly) {
                    Toast.makeText(activity, "测试成功：ACTION_VIEW 可用", Toast.LENGTH_LONG).show();
                    resetBtn();
                }
                return;
            } catch (android.content.ActivityNotFoundException e3) {
                String errSuffix3 = testOnly ? "（测试结束）" : "";
                Log.w(TAG, "ACTION_VIEW 不可用" + errSuffix3);
                if (testOnly) {
                    Toast.makeText(activity, "测试：所有安装方式均不可用", Toast.LENGTH_LONG).show();
                    resetBtn();
                    return;
                }
            }

            // 4. 兜底：复制到下载目录（仅非测试模式）
            copyApkToDownloads(apkFile, null);
        } catch (Exception e) {
            Log.e(TAG, "安装失败", e);
            if (testOnly) {
                Toast.makeText(activity, "测试异常：" + e.getMessage(), Toast.LENGTH_LONG).show();
                resetBtn();
            } else {
                copyApkToDownloads(apkFile, e);
            }
        }
    }

    private void copyApkToDownloads(File apkFile, Exception originalError) {
        try {
            File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) downloadDir.mkdirs();
            File targetFile = new File(downloadDir, "FNTV_update_" + apkFile.getName());
            try (FileInputStream fis = new FileInputStream(apkFile);
                 FileOutputStream fos = new FileOutputStream(targetFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) != -1) fos.write(buf, 0, n);
            }
            final String msg = "安装器不可用，APK 已复制到：\n" + targetFile.getAbsolutePath()
                    + "\n请用文件管理器打开此文件进行安装。";
            Log.w(TAG, msg);
            activity.runOnUiThread(() -> new android.app.AlertDialog.Builder(activity)
                    .setTitle("安装失败")
                    .setMessage(msg)
                    .setPositiveButton("我知道了", (d, w) -> resetBtn())
                    .show());
        } catch (Exception copyErr) {
            Log.e(TAG, "复制失败", copyErr);
            activity.runOnUiThread(() -> {
                String errMsg = originalError != null ? "安装失败: " + originalError.getMessage() : "安装器不可用";
                new android.app.AlertDialog.Builder(activity)
                        .setTitle("安装失败")
                        .setMessage(errMsg + "\nAPK 位置：\n" + apkFile.getAbsolutePath())
                        .setPositiveButton("我知道了", (d, w) -> resetBtn())
                        .show();
            });
        }
    }

    // ========== 签名校验 ==========

    private boolean verifyApkSignature(File apkFile) {
        try {
            android.content.pm.PackageManager pm = activity.getPackageManager();
            android.content.pm.PackageInfo currentInfo;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                currentInfo = pm.getPackageInfo(activity.getPackageName(),
                        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                if (currentInfo.signingInfo == null) return false;
                android.content.pm.Signature[] sigs = currentInfo.signingInfo.getApkContentsSigners();
                if (sigs == null || sigs.length == 0) return false;
                String currentSig = android.util.Base64.encodeToString(sigs[0].toByteArray(), android.util.Base64.NO_WRAP);
                Log.d(TAG, "当前签名: " + currentSig.substring(0, Math.min(20, currentSig.length())) + "...");
                return true;
            } else {
                currentInfo = pm.getPackageInfo(activity.getPackageName(), android.content.pm.PackageManager.GET_SIGNATURES);
                return currentInfo.signatures != null && currentInfo.signatures.length > 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "签名校验异常", e);
            return false;
        }
    }

    /** 计算文件 sha256 */
    private static String sha256Of(File f) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ========== 工具 ==========

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X ", b & 0xFF));
        return sb.toString().trim();
    }
}
