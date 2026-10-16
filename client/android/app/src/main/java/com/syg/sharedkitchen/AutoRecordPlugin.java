package com.syg.sharedkitchen;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 半自动记账：全局悬浮球 + 一键截屏。
 * - showFloatingButton(): 检查并申请 SYSTEM_ALERT_WINDOW，右上角显示可拖动悬浮球
 * - 点击悬浮球: 若未授权截屏则触发 MediaProjection 授权（onActivityResult 拿码后截当前屏幕），
 *   已授权直接截屏 → JPEG base64 → notifyListeners("screenshot", {base64})
 * - JS 侧监听 screenshot 事件 → 上传服务器 GLM 识别 → 弹窗核对 → 确认入账
 * - hideFloatingButton(): 收起悬浮球
 */
@CapacitorPlugin(name = "AutoRecord")
public class AutoRecordPlugin extends Plugin {

    private static final int REQ_OVERLAY = 4310;
    private static final int REQ_PROJECTION = 4311;

    private WindowManager windowManager;
    private ImageView floatingButton;
    private MediaProjectionManager projectionManager;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicReference<PluginCall> pendingCall = new AtomicReference<>();

    @PluginMethod
    public void showFloatingButton(PluginCall call) {
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("activity 未就绪");
            return;
        }
        windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        if (!Settings.canDrawOverlays(activity)) {
            pendingCall.set(call);
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            startActivityForResult(call, intent, REQ_OVERLAY);
            return;
        }
        attachFloatingButton();
        call.resolve();
    }

    @PluginMethod
    public void hideFloatingButton(PluginCall call) {
        detachFloatingButton();
        call.resolve();
    }

    @Override
    protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_OVERLAY) {
            PluginCall call = pendingCall.getAndSet(null);
            if (Settings.canDrawOverlays(getActivity())) {
                attachFloatingButton();
                if (call != null) call.resolve();
            } else if (call != null) {
                call.reject("未授予悬浮窗权限");
            }
            return;
        }
        if (requestCode == REQ_PROJECTION && resultCode == Activity.RESULT_OK && data != null) {
            captureWithPermission(resultCode, data);
        }
    }

    // ----- 悬浮球 -----

    @SuppressLint("ClickableViewAccessibility")
    private void attachFloatingButton() {
        if (floatingButton != null || windowManager == null) {
            return;
        }
        Activity activity = getActivity();
        floatingButton = new ImageView(activity);
        floatingButton.setImageResource(R.drawable.ic_auto_record);
        floatingButton.setBackgroundResource(R.drawable.auto_record_bg);
        int dp = (int) (52 * activity.getResources().getDisplayMetrics().density);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp, dp,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = 24;
        lp.y = 260;

        floatingButton.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            long downTime;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        downTime = System.currentTimeMillis();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > 6 || Math.abs(dy) > 6) {
                            moved = true;
                            lp.x = startX - (int) dx;
                            lp.y = startY + (int) dy;
                            windowManager.updateViewLayout(floatingButton, lp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved && System.currentTimeMillis() - downTime < 400) {
                            onFloatingTapped();
                        }
                        return true;
                }
                return false;
            }
        });
        windowManager.addView(floatingButton, lp);
    }

    private void detachFloatingButton() {
        if (floatingButton != null && windowManager != null) {
            try {
                windowManager.removeView(floatingButton);
            } catch (Exception ignore) { /* 已移除 */ }
            floatingButton = null;
        }
    }

    // ----- 截屏（MediaProjection） -----

    private void onFloatingTapped() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        if (projectionManager == null) {
            projectionManager = (MediaProjectionManager)
                    activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }
        if (projection != null) {
            takeScreenshot();
            return;
        }
        // 首次需要用户授权（系统弹窗）
        startActivityForResult(null,
                projectionManager.createScreenCaptureIntent(), REQ_PROJECTION);
    }

    private void captureWithPermission(int resultCode, Intent data) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        try {
            projection = projectionManager.getMediaProjection(resultCode, data);
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int w = dm.widthPixels;
            int h = dm.heightPixels;
            imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
            virtualDisplay = projection.createVirtualDisplay(
                    "auto-record", w, h, dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);
            // 等一帧渲染
            main.postDelayed(this::takeScreenshot, 350);
        } catch (Exception e) {
            notifyError("截屏初始化失败: " + e.getMessage());
        }
    }

    private void takeScreenshot() {
        try {
            Image image = imageReader != null ? imageReader.acquireLatestImage() : null;
            if (image == null) {
                main.postDelayed(this::takeScreenshot, 120);
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

            // 压缩到 ≤1080 宽 + JPEG(80)，控制 base64 体积
            int targetW = Math.min(1080, full.getWidth());
            Bitmap scaled = Bitmap.createScaledBitmap(full, targetW,
                    (int) (full.getHeight() * (targetW / (float) full.getWidth())), true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out);
            byte[] bytes = out.toByteArray();
            String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);

            JSObject ret = new JSObject();
            ret.put("base64", base64);
            ret.put("width", scaled.getWidth());
            ret.put("height", scaled.getHeight());
            notifyListeners("screenshot", ret);

            full.recycle();
            scaled.recycle();
        } catch (Exception e) {
            notifyError("截屏失败: " + e.getMessage());
        }
    }

    private void notifyError(String msg) {
        JSObject err = new JSObject();
        err.put("error", msg);
        notifyListeners("screenshotError", err);
    }

    @Override
    protected void handleOnDestroy() {
        detachFloatingButton();
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        if (projection != null) {
            projection.stop();
        }
    }
}
