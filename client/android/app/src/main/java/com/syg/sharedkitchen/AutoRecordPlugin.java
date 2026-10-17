package com.syg.sharedkitchen;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
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
import android.widget.Toast;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import java.io.ByteArrayOutputStream;
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
    // 授权结果不再缓存复用：Android 14+ 同一 token 只能消费一次，
    // 拿到后立刻交给 ScreenCaptureService 建立长驻会话（FGS 保活）
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
            // 授权 token 一次性消费：交给服务建立常驻会话并保活，成功后截一帧确认
            startCaptureService(resultCode, data);
            Toast.makeText(getActivity().getApplicationContext(),
                    "授权成功：截屏会话将一直保持，点悬浮球直接截屏", Toast.LENGTH_SHORT).show();
            main.postDelayed(ScreenCaptureService::requestShot, 900);
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

    // ----- 截屏（MediaProjection 前台服务，Android 14+ 必需） -----

    private void onFloatingTapped() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        // 注册结果回调（静态，服务直连）
        ScreenCaptureService.setCallback(new ScreenCaptureService.ShotCallback() {
            @Override
            public void onShot(String base64Jpeg) {
                JSObject ret = new JSObject();
                ret.put("base64", base64Jpeg);
                notifyListeners("screenshot", ret);
            }

            @Override
            public void onError(String message) {
                notifyError(message);
            }
        });
        // 服务里已有长驻会话 → 直接复用截屏，绝不重复弹授权
        if (ScreenCaptureService.hasSession()) {
            ScreenCaptureService.requestShot();
            return;
        }
        if (projectionManager == null) {
            projectionManager = (MediaProjectionManager)
                    activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }
        // 无会话（首次 / 进程重启 / 用户停止过）：系统授权弹窗，
        // 选"共享整个屏幕"后会话保持，之后不再打扰
        startActivityForResult(null,
                projectionManager.createScreenCaptureIntent(), REQ_PROJECTION);
    }

    private void startCaptureService(int resultCode, Intent data) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startForegroundService(
                        ScreenCaptureService.createIntent(activity, resultCode, data));
            } else {
                activity.startService(
                        ScreenCaptureService.createIntent(activity, resultCode, data));
            }
        } catch (Exception e) {
            notifyError("截屏服务启动失败: " + e.getMessage());
        }
    }

    private void notifyError(String msg) {
        JSObject err = new JSObject();
        err.put("error", msg);
        notifyListeners("screenshotError", err);
    }

    @Override
    protected void handleOnDestroy() {
        // 只收悬浮球；截屏会话保留（静态），下次点击免授权直接用
        detachFloatingButton();
    }
}
