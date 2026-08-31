package com.syg.sharedkitchen;

import android.content.Intent;
import android.net.Uri;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 应用自更新桥：查本机版本 → 从服务器下载新 APK（本地文件，走 /files/apk/**）→ 调起安装。
 */
@CapacitorPlugin(name = "AppBridge")
public class AppBridgePlugin extends Plugin {

    private final AtomicBoolean downloading = new AtomicBoolean(false);

    /** 本机版本信息。 */
    @PluginMethod
    public void checkLocal(PluginCall call) {
        try {
            var pm = getContext().getPackageManager();
            var info = pm.getPackageInfo(getContext().getPackageName(), 0);
            JSObject ret = new JSObject();
            ret.put("versionName", info.versionName);
            ret.put("versionCode", (int) info.getLongVersionCode());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("读取版本失败: " + e.getMessage());
        }
    }

    /** 下载 APK 到应用外部文件目录，返回绝对路径；progress 事件按字节百分比回抛。 */
    @PluginMethod
    public void downloadApk(PluginCall call) {
        String url = call.getString("url", "");
        if (url.isEmpty()) {
            call.reject("url 必填");
            return;
        }
        if (!downloading.compareAndSet(false, true)) {
            call.reject("已有下载在进行");
            return;
        }
        Thread t = new Thread(() -> {
            File dir = new File(getContext().getExternalFilesDir(null), "apk");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "update.apk");
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(30000);
                int code = conn.getResponseCode();
                if (code != 200) {
                    downloading.set(false);
                    call.reject("下载失败 HTTP " + code);
                    return;
                }
                long total = conn.getContentLength();
                try (InputStream in = conn.getInputStream();
                     FileOutputStream fout = new FileOutputStream(out)) {
                    byte[] buf = new byte[16384];
                    int n;
                    long done = 0;
                    int lastPct = -1;
                    while ((n = in.read(buf)) > 0) {
                        fout.write(buf, 0, n);
                        done += n;
                        if (total > 0) {
                            int pct = (int) (done * 100 / total);
                            if (pct != lastPct) {
                                lastPct = pct;
                                JSObject p = new JSObject();
                                p.put("percent", pct);
                                notifyListeners("progress", p);
                            }
                        }
                    }
                }
                downloading.set(false);
                JSObject ret = new JSObject();
                ret.put("path", out.getAbsolutePath());
                call.resolve(ret);
            } catch (Exception e) {
                downloading.set(false);
                out.delete();
                call.reject("下载失败: " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /** 调起系统安装器。 */
    @PluginMethod
    public void installApk(PluginCall call) {
        String path = call.getString("path", "");
        File file = new File(path);
        if (!file.exists()) {
            call.reject("安装包不存在");
            return;
        }
        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    getContext(), getContext().getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("无法启动安装: " + e.getMessage());
        }
    }
}
