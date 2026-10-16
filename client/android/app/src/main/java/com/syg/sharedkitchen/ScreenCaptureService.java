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
 * 截屏前台服务（Android 14+ 必须：getMediaProjection 前需先启动
 * foregroundServiceType=mediaProjection 的 FGS，否则抛 SecurityException，
 * 表现为"每次点悬浮球都重新弹系统授权框"）。
 * 服务常驻保持 MediaProjection 会话 → 授权一次，之后点悬浮球直接截屏。
 */
public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID = "screen_capture";
    private static final int NOTI_ID = 4320;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** 插件直连回调：截屏结果（base64 jpeg） */
    public interface ShotCallback {
        void onShot(String base64Jpeg);

        void onError(String message);
    }

    private static volatile ShotCallback pendingCallback;

    public static void setCallback(ShotCallback cb) {
        pendingCallback = cb;
    }

    /** 发送截屏请求（服务需已就绪） */
    public static void requestShot() {
        ScreenCaptureService svc = instance;
        if (svc != null) {
            svc.shot();
        } else if (pendingCallback != null) {
            pendingCallback.onError("截屏服务未就绪，请先点一次悬浮球完成授权");
        }
    }

    private static volatile ScreenCaptureService instance;

    public static Intent createIntent(Context ctx, int resultCode, Intent data) {
        Intent i = new Intent(ctx, ScreenCaptureService.class);
        i.putExtra("resultCode", resultCode);
        i.putExtra("data", data);
        return i;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "悬浮球记账截屏", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification noti;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder b = new Notification.Builder(this, CHANNEL_ID);
            b.setContentTitle("共享厨房 · 悬浮球记账")
                    .setContentText("截屏会话保持中（授权一次即可）")
                    .setSmallIcon(android.R.drawable.ic_menu_camera);
            noti = b.build();
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

        if (intent != null && intent.getIntExtra("resultCode", 0) != 0) {
            int code = intent.getIntExtra("resultCode", 0);
            Intent data = intent.getParcelableExtra("data");
            setupProjection(code, data);
        }
        return START_STICKY;
    }

    private void setupProjection(int resultCode, Intent data) {
        try {
            if (projection != null) {
                return; // 会话已在
            }
            MediaProjectionManager pm = (MediaProjectionManager)
                    getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = pm.getMediaProjection(resultCode, data);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int w = dm.widthPixels;
            int h = dm.heightPixels;
            imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
            virtualDisplay = projection.createVirtualDisplay(
                    "auto-record", w, h, dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, main);
        } catch (Exception e) {
            if (pendingCallback != null) {
                pendingCallback.onError("截屏会话建立失败: " + e.getMessage());
            }
        }
    }

    private void shot() {
        try {
            Image image = imageReader != null ? imageReader.acquireLatestImage() : null;
            if (image == null) {
                main.postDelayed(this::shot, 120);
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

    @Override
    public void onDestroy() {
        instance = null;
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
        super.onDestroy();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }
}
