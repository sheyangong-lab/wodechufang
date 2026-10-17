package com.syg.sharedkitchen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * 截屏前台服务。
 *
 * Android 14（targetSdk 34）的关键约束：
 * 1. getMediaProjection 前必须先 startForeground(mediaProjection 类型)；
 * 2. 同一份授权结果（resultCode/data）只能消费一次，服务/进程重建后拿旧数据
 *    再调 getMediaProjection 会抛 SecurityException —— 这是「每次点悬浮球都
 *    重新弹系统授权框」的根因。
 *
 * 因此：授权数据只在拿到的那一刻交给本服务消费一次；MediaProjection 连同
 * VirtualDisplay/ImageReader 以进程级静态会话常驻，START_STICKY 前台服务
 * 保活（进程拿到 FGS 优先级，不易被杀）。之后每次点悬浮球直接复用会话截屏，
 * 直到用户从系统通知/状态栏主动停止共享或系统收回。
 */
public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID = "screen_capture";
    private static final int NOTI_ID = 4320;
    /** 取帧重试上限（约 3.6s）：虚拟屏首帧未出或画面静止时 acquireLatestImage 可能为空 */
    private static final int SHOT_MAX_RETRY = 30;
    private static final long SHOT_RETRY_MS = 120L;

    /** 进程级截屏会话：跨服务实例存续，旧授权 token 绝不二次消费 */
    private static MediaProjection projection;
    private static VirtualDisplay virtualDisplay;
    private static ImageReader imageReader;

    private static volatile ScreenCaptureService instance;
    private static volatile ShotCallback pendingCallback;
    /** 授权后立即确认截帧时，服务可能还在启动，用它做等待重试 */
    private static final Handler retryHandler = new Handler(Looper.getMainLooper());

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 插件直连回调：截屏结果（base64 jpeg） */
    public interface ShotCallback {
        void onShot(String base64Jpeg);

        void onError(String message);
    }

    public static void setCallback(ShotCallback cb) {
        pendingCallback = cb;
    }

    /** 授权会话是否可用（悬浮球点击时判断；不可用才走系统授权弹窗） */
    public static boolean hasSession() {
        return projection != null;
    }

    public static Intent createIntent(Context ctx, int resultCode, Intent data) {
        Intent i = new Intent(ctx, ScreenCaptureService.class);
        i.putExtra("resultCode", resultCode);
        i.putExtra("data", data);
        return i;
    }

    /** 发送截屏请求；服务尚在启动则等待重试 */
    public static void requestShot() {
        requestShotWhenReady(0);
    }

    private static void requestShotWhenReady(int attempt) {
        ScreenCaptureService svc = instance;
        if (svc != null) {
            svc.shot(0);
            return;
        }
        if (attempt >= SHOT_MAX_RETRY) {
            if (pendingCallback != null) {
                pendingCallback.onError("截屏服务未就绪，请再点一次悬浮球");
            }
            return;
        }
        retryHandler.postDelayed(() -> requestShotWhenReady(attempt + 1), SHOT_RETRY_MS);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "悬浮球记账截屏", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int resultCode = intent != null ? intent.getIntExtra("resultCode", 0) : 0;
        Intent resultData = intent != null ? getParcelableData(intent) : null;
        if (projection == null && resultData == null) {
            // 服务重启（START_STICKY）且没有新授权：旧 token 在 Android 14 上
            // 已不可复用，无法静默恢复，退场等用户下次点击重新授权
            stopSelf();
            return START_NOT_STICKY;
        }
        // Android 14+：必须先进 mediaProjection 类型前台，才能 getMediaProjection
        startForegroundWithType();
        if (projection == null) {
            setupProjection(resultCode, resultData);
            if (projection == null) {
                stopForegroundCompat();
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        return START_STICKY;
    }

    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    private static Intent getParcelableData(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra("data", Intent.class);
        }
        return intent.getParcelableExtra("data");
    }

    private void startForegroundWithType() {
        Notification noti;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            noti = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("共享厨房 · 悬浮球记账")
                    .setContentText("截屏会话保持中（点悬浮球直接截屏）")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .build();
        } else {
            noti = new Notification.Builder(this)
                    .setContentTitle("共享厨房 · 悬浮球记账")
                    .setContentText("截屏会话保持中")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .build();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTI_ID, noti, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTI_ID, noti);
        }
    }

    /** 用刚拿到的一次性授权建立长驻会话 */
    private void setupProjection(int resultCode, Intent data) {
        try {
            MediaProjectionManager pm = (MediaProjectionManager)
                    getSystemService(MEDIA_PROJECTION_SERVICE);
            MediaProjection p = pm.getMediaProjection(resultCode, data);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int w = dm.widthPixels;
            int h = dm.heightPixels;
            ImageReader reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
            VirtualDisplay display = p.createVirtualDisplay(
                    "auto-record", w, h, dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, mainHandler);
            // 用户点系统「停止共享」/系统收回 → 会话作废，服务退场，
            // 下次点悬浮球重新走授权
            p.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    releaseSession();
                    stopForegroundCompat();
                    stopSelf();
                    if (pendingCallback != null) {
                        pendingCallback.onError("屏幕共享已被关闭，下次点悬浮球需重新授权");
                    }
                }
            }, mainHandler);
            // 全部就绪后才暴露静态引用，失败路径不留半成品
            projection = p;
            imageReader = reader;
            virtualDisplay = display;
        } catch (Exception e) {
            releaseSession();
            if (pendingCallback != null) {
                pendingCallback.onError("截屏会话建立失败，请重新点击悬浮球授权: " + e.getMessage());
            }
        }
    }

    private void shot(int attempt) {
        try {
            Image image = imageReader != null ? imageReader.acquireLatestImage() : null;
            if (image == null) {
                if (attempt >= SHOT_MAX_RETRY) {
                    if (pendingCallback != null) {
                        pendingCallback.onError("截屏超时，请再点一次");
                    }
                    return;
                }
                mainHandler.postDelayed(() -> shot(attempt + 1), SHOT_RETRY_MS);
                return;
            }
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();

            Bitmap full = Bitmap.createBitmap(
                    image.getWidth() + rowPadding / pixelStride,
                    image.getHeight(), Bitmap.Config.ARGB_8888);
            full.copyPixelsFromBuffer(buffer);
            image.close();

            int targetW = Math.min(1080, full.getWidth());
            Bitmap scaled = Bitmap.createScaledBitmap(full, targetW,
                    (int) (full.getHeight() * (targetW / (float) full.getWidth())), true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out);
            String b64 = android.util.Base64.encodeToString(
                    out.toByteArray(), android.util.Base64.NO_WRAP);

            if (pendingCallback != null) {
                pendingCallback.onShot(b64);
            }
            full.recycle();
            scaled.recycle();
        } catch (Exception e) {
            if (pendingCallback != null) {
                pendingCallback.onError("截屏失败: " + e.getMessage());
            }
        }
    }

    /** 清空会话（先摘引用再 stop，避免 onStop 回调里重入） */
    private static void releaseSession() {
        VirtualDisplay vd = virtualDisplay;
        virtualDisplay = null;
        if (vd != null) {
            try { vd.release(); } catch (Exception ignore) { }
        }
        ImageReader reader = imageReader;
        imageReader = null;
        if (reader != null) {
            try { reader.close(); } catch (Exception ignore) { }
        }
        MediaProjection p = projection;
        projection = null;
        if (p != null) {
            try { p.stop(); } catch (Exception ignore) { }
        }
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    @Override
    public void onDestroy() {
        instance = null;
        // 前台服务被销毁后系统不会让投影继续存活，主动清理避免悬挂引用
        releaseSession();
        super.onDestroy();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }
}
