package org.telegram.shieldgram;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Size;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.os.CancellationSignal;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.support.fingerprint.FingerprintManagerCompat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Custom in-app ShieldGram Biometric Special Interface (Cyber HUD) replacing Android's stock
 * system BiometricPrompt dialog for both live Face Recognition and hardware Fingerprint sensors.
 */
@SuppressLint("ViewConstructor")
public class ShieldBiometricHudOverlay extends FrameLayout {

    public static final int MODE_UNLOCK = 0;
    public static final int MODE_ENROLL_FACE = 1;

    private static final int CAMERA_PERMISSION_REQ_CODE = 9471;
    private static final int ENROLL_REQUIRED_SAMPLES = 6;
    private static final int FEATURE_DIM = 12;

    public interface Callback {
        void onAuthenticated(boolean viaFace);
        void onEnrollmentCompleted();
        void onDismissToManualPasscode();
    }

    private final int mode;
    private final boolean enableFaceScanner;
    private final boolean enableFingerprintScanner;
    private final Callback callback;

    private ScannerReticleView reticleView;
    private TextureView cameraTextureView;
    private TextView statusPillView;
    private TextView headlineView;
    private TextView telemetrySubView;
    private FingerprintPulsePod fingerprintPodView;
    private TextView actionButtonView;

    private CancellationSignal fingerprintSignal;
    private boolean finished;
    private boolean permissionRequestedOnce;

    // Camera2 & background vision worker
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Size previewSize = new Size(640, 480);
    private FaceDetector gmsFaceDetector;
    private boolean frameProcessing;
    private long lastFrameAnalyzeMs;

    // Enrollment / Verification state
    private final ArrayList<float[]> collectedSamples = new ArrayList<>();
    private int consecutiveVerifyMatches = 0;
    private float scanProgress = 0f;
    private float targetScanProgress = 0f;

    // Live HUD state: 0 = scanning, 1 = success, 2 = error
    private int hudState = 0;
    private long hudStateChangedMs = 0L;
    private volatile float[] liveLandmarksNormalized = null;
    private volatile RectF liveFaceBoxNormalized = null;

    public ShieldBiometricHudOverlay(
            @NonNull Context context,
            int mode,
            boolean enableFaceScanner,
            boolean enableFingerprintScanner,
            @NonNull Callback callback
    ) {
        super(context);
        this.mode = mode;
        this.enableFaceScanner = enableFaceScanner;
        this.enableFingerprintScanner = enableFingerprintScanner && mode == MODE_UNLOCK;
        this.callback = callback;

        setClickable(true);
        setFocusable(true);
        ShieldGramConfig.ensureLoaded();

        final boolean gold = ShieldGramConfig.isRoyalGoldActive();
        final int accent1 = ShieldGramConfig.getSignatureAccentColor(true);
        final int accent2 = ShieldGramConfig.getSignatureSecondaryColor(true);

        GradientDrawable backdrop = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                gold ? new int[]{0xF2120E07, 0xF5090704, 0xF8050402}
                     : new int[]{0xF2081022, 0xF5050A16, 0xF803060E}
        );
        setBackground(backdrop);

        LinearLayout rootCol = new LinearLayout(context);
        rootCol.setOrientation(LinearLayout.VERTICAL);
        rootCol.setGravity(Gravity.CENTER_HORIZONTAL);
        rootCol.setPadding(dp(24), dp(36), dp(24), dp(28));
        addView(rootCol, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        // 1. Top Brand Crest & Security Core Pill
        ImageView crestView = new ImageView(context);
        crestView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        crestView.setImageResource(ShieldGramConfig.getBrandCrestResId());
        rootCol.addView(crestView, LayoutHelper.createLinear(54, 54, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

        statusPillView = new TextView(context);
        statusPillView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f);
        statusPillView.setTypeface(AndroidUtilities.bold());
        statusPillView.setLetterSpacing(0.11f);
        statusPillView.setTextColor(gold ? 0xFFF7D774 : 0xFF93C5FD);
        statusPillView.setPadding(dp(12), dp(4), dp(12), dp(4));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dp(12));
        pillBg.setColor(ColorUtils.setAlphaComponent(accent1, 34));
        pillBg.setStroke(dp(1), ColorUtils.setAlphaComponent(accent1, 120));
        statusPillView.setBackground(pillBg);
        statusPillView.setText(mode == MODE_ENROLL_FACE
                ? LocaleController.getString(R.string.ShieldHudFaceCalibrationBadge)
                : LocaleController.getString(R.string.ShieldHudBiometricCoreBadge));
        rootCol.addView(statusPillView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        headlineView = new TextView(context);
        headlineView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f);
        headlineView.setTypeface(AndroidUtilities.bold());
        headlineView.setTextColor(0xFFFAF8F5);
        headlineView.setGravity(Gravity.CENTER);
        headlineView.setText(resolveInitialHeadline());
        rootCol.addView(headlineView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 0, 8, 6));

        telemetrySubView = new TextView(context);
        telemetrySubView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        telemetrySubView.setTextColor(gold ? 0xFFD6C7A9 : 0xFFA5B4FC);
        telemetrySubView.setGravity(Gravity.CENTER);
        telemetrySubView.setText(resolveInitialSubtitle());
        rootCol.addView(telemetrySubView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 0, 12, 18));

        // 2. Central Cyber Scanner Reticle + Circular Live Camera Viewport
        int scannerSizeDp = enableFaceScanner ? 232 : 188;
        reticleView = new ScannerReticleView(context);
        if (enableFaceScanner) {
            cameraTextureView = new TextureView(context);
            cameraTextureView.setOpaque(false);
            reticleView.addView(cameraTextureView, LayoutHelper.createFrame(scannerSizeDp - 36, scannerSizeDp - 36, Gravity.CENTER));
            reticleView.setClipChildren(false);
        }
        rootCol.addView(reticleView, LayoutHelper.createLinear(scannerSizeDp, scannerSizeDp, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 18));

        // 3. Dedicated Hardware Fingerprint Sensor Pod (when fingerprint unlock is active)
        if (this.enableFingerprintScanner) {
            fingerprintPodView = new FingerprintPulsePod(context);
            int podSize = enableFaceScanner ? 68 : 84;
            rootCol.addView(fingerprintPodView, LayoutHelper.createLinear(podSize, podSize, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

            TextView fpHint = new TextView(context);
            fpHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
            fpHint.setTextColor(0xB8E2E8F0);
            fpHint.setGravity(Gravity.CENTER);
            fpHint.setText(LocaleController.getString(R.string.ShieldHudTouchSensorHint));
            rootCol.addView(fpHint, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));
        }

        View spacer = new View(context);
        rootCol.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        // 4. Bottom Action Pill (Fallback to PIN/Pattern or Cancel Enrollment)
        actionButtonView = new TextView(context);
        actionButtonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        actionButtonView.setTypeface(AndroidUtilities.bold());
        actionButtonView.setTextColor(0xFFF8FAFC);
        actionButtonView.setGravity(Gravity.CENTER);
        actionButtonView.setPadding(dp(26), dp(12), dp(26), dp(12));
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setCornerRadius(dp(22));
        btnBg.setColor(0x26FFFFFF);
        btnBg.setStroke(dp(1), ColorUtils.setAlphaComponent(accent2, 115));
        actionButtonView.setBackground(btnBg);
        actionButtonView.setText(mode == MODE_ENROLL_FACE
                ? LocaleController.getString(R.string.Cancel)
                : LocaleController.getString(R.string.ShieldUsePasscode));
        actionButtonView.setOnClickListener(v -> {
            if (finished) return;
            finished = true;
            stopAllSensors();
            animateDismiss(() -> callback.onDismissToManualPasscode());
        });
        rootCol.addView(actionButtonView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 6));
    }

    private String resolveInitialHeadline() {
        if (mode == MODE_ENROLL_FACE) {
            return LocaleController.getString(R.string.ShieldHudEnrollHeadline);
        }
        if (enableFaceScanner && enableFingerprintScanner) {
            return LocaleController.getString(R.string.ShieldHudDualHeadline);
        }
        if (enableFaceScanner) {
            return LocaleController.getString(R.string.ShieldHudFaceHeadline);
        }
        return LocaleController.getString(R.string.ShieldHudFingerprintHeadline);
    }

    private String resolveInitialSubtitle() {
        if (mode == MODE_ENROLL_FACE) {
            return LocaleController.getString(R.string.ShieldHudEnrollSub);
        }
        if (enableFaceScanner && !ShieldGramConfig.hasEnrolledFaceSignature()) {
            return enableFingerprintScanner
                    ? LocaleController.getString(R.string.ShieldHudNoFaceEnrolledUseFpSub)
                    : LocaleController.getString(R.string.ShieldHudNoFaceEnrolledSub);
        }
        if (enableFaceScanner && enableFingerprintScanner) {
            return LocaleController.getString(R.string.ShieldHudDualSub);
        }
        if (enableFaceScanner) {
            return LocaleController.getString(R.string.ShieldHudFaceSub);
        }
        return LocaleController.getString(R.string.ShieldHudFingerprintSub);
    }

    public void showAnimated(@NonNull ViewGroup container) {
        if (getParent() != null) {
            ((ViewGroup) getParent()).removeView(this);
        }
        setAlpha(0f);
        setScaleX(0.96f);
        setScaleY(0.96f);
        container.addView(this, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .start();
        startAllSensors();
    }

    public void dismissImmediate() {
        finished = true;
        stopAllSensors();
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }

    private void animateDismiss(@Nullable Runnable after) {
        animate()
                .alpha(0f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(180)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (getParent() instanceof ViewGroup) {
                            ((ViewGroup) getParent()).removeView(ShieldBiometricHudOverlay.this);
                        }
                        if (after != null) {
                            after.run();
                        }
                    }
                })
                .start();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && !finished && enableFaceScanner && cameraDevice == null) {
            if (hasCameraPermission()) {
                startFaceCameraIfReady();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAllSensors();
        super.onDetachedFromWindow();
    }

    private boolean hasCameraPermission() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startAllSensors() {
        if (finished) {
            return;
        }
        if (enableFingerprintScanner) {
            startSilentHardwareFingerprint();
        }
        if (enableFaceScanner && (mode == MODE_ENROLL_FACE || ShieldGramConfig.hasEnrolledFaceSignature())) {
            if (hasCameraPermission()) {
                startFaceCameraIfReady();
            } else if (!permissionRequestedOnce && Build.VERSION.SDK_INT >= 23) {
                permissionRequestedOnce = true;
                Activity act = AndroidUtilities.findActivity(getContext());
                if (act != null) {
                    telemetrySubView.setText(LocaleController.getString(R.string.ShieldHudGrantCameraHint));
                    try {
                        act.requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQ_CODE);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            }
        }
    }

    public void stopAllSensors() {
        if (fingerprintSignal != null) {
            try {
                fingerprintSignal.cancel();
            } catch (Throwable ignore) {
            }
            fingerprintSignal = null;
        }
        stopFaceCamera();
    }

    private void startSilentHardwareFingerprint() {
        if (Build.VERSION.SDK_INT < 23) {
            return;
        }
        try {
            FingerprintManagerCompat fp = FingerprintManagerCompat.from(getContext());
            if (!fp.isHardwareDetected() || !fp.hasEnrolledFingerprints()) {
                return;
            }
            fingerprintSignal = new CancellationSignal();
            fp.authenticate(null, 0, fingerprintSignal, new FingerprintManagerCompat.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errMsgId, CharSequence errString) {
                    if (finished) return;
                    // Error 5 = cancelled; Error 7/9 = lockout
                    if (errMsgId == 7 || errMsgId == 9) {
                        triggerErrorFlash(errString != null ? errString.toString() : LocaleController.getString(R.string.ShieldHudSensorLocked));
                        if (!enableFaceScanner) {
                            postDelayed(() -> {
                                if (!finished) {
                                    finished = true;
                                    stopAllSensors();
                                    animateDismiss(callback::onDismissToManualPasscode);
                                }
                            }, 900);
                        }
                    }
                }

                @Override
                public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
                    if (finished) return;
                    if (helpString != null && helpString.length() > 0) {
                        triggerErrorFlash(helpString.toString());
                    }
                }

                @Override
                public void onAuthenticationFailed() {
                    if (finished) return;
                    triggerErrorFlash(LocaleController.getString(R.string.ShieldHudFingerprintMismatch));
                }

                @Override
                public void onAuthenticationSucceeded(FingerprintManagerCompat.AuthenticationResult result) {
                    if (finished) return;
                    targetScanProgress = 1f;
                    completeWithSuccess(false);
                }
            }, null);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void startFaceCameraIfReady() {
        if (cameraTextureView == null || finished) {
            return;
        }
        if (cameraThread == null) {
            cameraThread = new HandlerThread("ShieldBioCam");
            cameraThread.start();
            cameraHandler = new Handler(cameraThread.getLooper());
            cameraHandler.post(() -> {
                try {
                    gmsFaceDetector = new FaceDetector.Builder(getContext().getApplicationContext())
                            .setMode(FaceDetector.FAST_MODE)
                            .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                            .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                            .setProminentFaceOnly(true)
                            .setTrackingEnabled(true)
                            .build();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            });
        }
        if (cameraTextureView.isAvailable()) {
            openFrontCamera(cameraTextureView.getWidth(), cameraTextureView.getHeight());
        } else {
            cameraTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                    openFrontCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                    configureTextureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
                    onCameraFrameUpdated();
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    private void openFrontCamera(int viewW, int viewH) {
        if (finished || !hasCameraPermission() || cameraHandler == null) {
            return;
        }
        cameraHandler.post(() -> {
            if (finished || cameraDevice != null) {
                return;
            }
            try {
                CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
                if (manager == null) return;
                String chosenId = null;
                for (String cid : manager.getCameraIdList()) {
                    CameraCharacteristics cc = manager.getCameraCharacteristics(cid);
                    Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        chosenId = cid;
                        StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                        if (map != null) {
                            Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
                            previewSize = chooseOptimalSize(sizes);
                        }
                        break;
                    }
                }
                if (chosenId == null) {
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> configureTextureTransform(viewW, viewH));
                manager.openCamera(chosenId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        if (finished) {
                            camera.close();
                            return;
                        }
                        cameraDevice = camera;
                        startCameraPreviewSession();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        camera.close();
                        cameraDevice = null;
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        camera.close();
                        cameraDevice = null;
                    }
                }, cameraHandler);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    private Size chooseOptimalSize(@Nullable Size[] sizes) {
        if (sizes == null || sizes.length == 0) {
            return new Size(640, 480);
        }
        Size best = sizes[0];
        int bestScore = Integer.MAX_VALUE;
        for (Size s : sizes) {
            int areaDiff = Math.abs(s.getWidth() * s.getHeight() - 640 * 480);
            if (s.getWidth() >= 320 && s.getHeight() >= 240 && areaDiff < bestScore) {
                bestScore = areaDiff;
                best = s;
            }
        }
        return best;
    }

    private void configureTextureTransform(int viewWidth, int viewHeight) {
        if (cameraTextureView == null || viewWidth <= 0 || viewHeight <= 0 || previewSize == null) {
            return;
        }
        // In portrait, front camera sensor buffer is rotated 90/270 deg so effective aspect is height:width
        float bufferW = Math.min(previewSize.getWidth(), previewSize.getHeight());
        float bufferH = Math.max(previewSize.getWidth(), previewSize.getHeight());
        float scaleX = 1f;
        float scaleY = 1f;
        float viewRatio = (float) viewWidth / (float) viewHeight;
        float bufRatio = bufferW / bufferH;
        if (bufRatio > viewRatio) {
            scaleX = bufRatio / viewRatio;
        } else {
            scaleY = viewRatio / bufRatio;
        }
        Matrix matrix = new Matrix();
        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f);
        cameraTextureView.setTransform(matrix);
    }

    private void startCameraPreviewSession() {
        try {
            if (cameraDevice == null || cameraTextureView == null || !cameraTextureView.isAvailable()) {
                return;
            }
            SurfaceTexture texture = cameraTextureView.getSurfaceTexture();
            if (texture == null) return;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);

            final CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (finished || cameraDevice == null) {
                        return;
                    }
                    captureSession = session;
                    try {
                        session.setRepeatingRequest(builder.build(), null, cameraHandler);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }, cameraHandler);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void stopFaceCamera() {
        Handler h = cameraHandler;
        HandlerThread ht = cameraThread;
        cameraHandler = null;
        cameraThread = null;
        if (h != null) {
            h.post(() -> {
                try {
                    if (captureSession != null) {
                        captureSession.close();
                        captureSession = null;
                    }
                } catch (Throwable ignore) {
                }
                try {
                    if (cameraDevice != null) {
                        cameraDevice.close();
                        cameraDevice = null;
                    }
                } catch (Throwable ignore) {
                }
                try {
                    if (gmsFaceDetector != null) {
                        gmsFaceDetector.release();
                        gmsFaceDetector = null;
                    }
                } catch (Throwable ignore) {
                }
                if (ht != null) {
                    ht.quitSafely();
                }
            });
        }
    }

    private void onCameraFrameUpdated() {
        if (finished || frameProcessing || cameraHandler == null || cameraTextureView == null) {
            return;
        }
        if (mode == MODE_UNLOCK && !ShieldGramConfig.hasEnrolledFaceSignature()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastFrameAnalyzeMs < 135L) {
            return;
        }
        lastFrameAnalyzeMs = now;
        Bitmap bmp = null;
        try {
            bmp = cameraTextureView.getBitmap(260, 260);
        } catch (Throwable ignore) {
        }
        if (bmp == null) {
            return;
        }
        frameProcessing = true;
        final Bitmap frameBmp = bmp;
        cameraHandler.post(() -> {
            try {
                analyzeFaceBitmap(frameBmp);
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                frameBmp.recycle();
                frameProcessing = false;
            }
        });
    }

    private void analyzeFaceBitmap(@NonNull Bitmap bmp) {
        final int w = bmp.getWidth();
        final int h = bmp.getHeight();
        float[] featureVec = null;
        float[] uiLandmarks = null;
        RectF uiBox = null;
        boolean eyesClosed = false;

        // 1. Primary Vision Engine: GMS FaceDetector with full landmarks + liveness eye-open check
        if (gmsFaceDetector != null && gmsFaceDetector.isOperational()) {
            Frame frame = new Frame.Builder().setBitmap(bmp).build();
            SparseArray<Face> faces = gmsFaceDetector.detect(frame);
            if (faces != null && faces.size() == 1) {
                Face face = faces.valueAt(0);
                float fw = face.getWidth();
                float fh = face.getHeight();
                PointF pos = face.getPosition();
                if (fw >= w * 0.24f && Math.abs(face.getEulerY()) < 26f && Math.abs(face.getEulerZ()) < 24f) {
                    float leftOpen = face.getIsLeftEyeOpenProbability();
                    float rightOpen = face.getIsRightEyeOpenProbability();
                    if (leftOpen >= 0f && rightOpen >= 0f && leftOpen < 0.20f && rightOpen < 0.20f) {
                        eyesClosed = true;
                    } else {
                        uiBox = new RectF(
                                UtilitiesClamp01(pos.x / w),
                                UtilitiesClamp01(pos.y / h),
                                UtilitiesClamp01((pos.x + fw) / w),
                                UtilitiesClamp01((pos.y + fh) / h)
                        );
                        List<Landmark> lms = face.getLandmarks();
                        if (lms != null && !lms.isEmpty()) {
                            uiLandmarks = new float[lms.size() * 2];
                            for (int i = 0; i < lms.size(); i++) {
                                PointF lp = lms.get(i).getPosition();
                                uiLandmarks[i * 2] = UtilitiesClamp01(lp.x / w);
                                uiLandmarks[i * 2 + 1] = UtilitiesClamp01(lp.y / h);
                            }
                        }
                        featureVec = extractGmsOrHybridFeatureVector(bmp, face);
                    }
                }
            }
        } else {
            // 2. Built-in Android SDK FaceDetector fallback (zero external dependencies)
            Bitmap rgb565 = bmp.copy(Bitmap.Config.RGB_565, false);
            android.media.FaceDetector sdkDetector = new android.media.FaceDetector(w, h, 1);
            android.media.FaceDetector.Face[] found = new android.media.FaceDetector.Face[1];
            int count = sdkDetector.findFaces(rgb565, found);
            rgb565.recycle();
            if (count == 1 && found[0] != null && found[0].confidence() >= 0.35f) {
                PointF mid = new PointF();
                found[0].getMidPoint(mid);
                float eyeDist = found[0].eyesDistance();
                if (eyeDist >= w * 0.10f) {
                    uiBox = new RectF(
                            UtilitiesClamp01((mid.x - eyeDist * 1.15f) / w),
                            UtilitiesClamp01((mid.y - eyeDist * 1.05f) / h),
                            UtilitiesClamp01((mid.x + eyeDist * 1.15f) / w),
                            UtilitiesClamp01((mid.y + eyeDist * 1.55f) / h)
                    );
                    uiLandmarks = new float[]{
                            UtilitiesClamp01((mid.x - eyeDist * 0.5f) / w), UtilitiesClamp01(mid.y / h),
                            UtilitiesClamp01((mid.x + eyeDist * 0.5f) / w), UtilitiesClamp01(mid.y / h),
                            UtilitiesClamp01(mid.x / w), UtilitiesClamp01((mid.y + eyeDist * 0.58f) / h)
                    };
                    featureVec = extractCanonicalPatchVector(bmp, mid.x, mid.y, eyeDist, null);
                }
            }
        }

        liveFaceBoxNormalized = uiBox;
        liveLandmarksNormalized = uiLandmarks;

        final float[] finalVec = featureVec;
        final boolean finalEyesClosed = eyesClosed;
        AndroidUtilities.runOnUIThread(() -> {
            if (finished) return;
            reticleView.invalidate();
            if (finalEyesClosed) {
                telemetrySubView.setText(LocaleController.getString(R.string.ShieldHudOpenEyesHint));
                return;
            }
            if (finalVec == null) {
                if (mode == MODE_ENROLL_FACE) {
                    telemetrySubView.setText(LocaleController.getString(R.string.ShieldHudCenterFaceHint));
                }
                return;
            }
            handleValidFaceVector(finalVec);
        });
    }

    private float[] extractGmsOrHybridFeatureVector(@NonNull Bitmap bmp, @NonNull Face face) {
        PointF leftEye = null, rightEye = null, nose = null, leftMouth = null, rightMouth = null, bottomMouth = null;
        for (Landmark lm : face.getLandmarks()) {
            switch (lm.getType()) {
                case Landmark.LEFT_EYE: leftEye = lm.getPosition(); break;
                case Landmark.RIGHT_EYE: rightEye = lm.getPosition(); break;
                case Landmark.NOSE_BASE: nose = lm.getPosition(); break;
                case Landmark.LEFT_MOUTH: leftMouth = lm.getPosition(); break;
                case Landmark.RIGHT_MOUTH: rightMouth = lm.getPosition(); break;
                case Landmark.BOTTOM_MOUTH: bottomMouth = lm.getPosition(); break;
            }
        }
        float midX;
        float midY;
        float eyeDist;
        float[] geomRatios = new float[6];
        if (leftEye != null && rightEye != null) {
            midX = (leftEye.x + rightEye.x) * 0.5f;
            midY = (leftEye.y + rightEye.y) * 0.5f;
            eyeDist = Math.max(12f, (float) Math.hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y));
            geomRatios[0] = UtilitiesClamp01(eyeDist / Math.max(1f, face.getWidth()));
            geomRatios[1] = UtilitiesClamp01(face.getWidth() / Math.max(1f, face.getHeight()) * 0.75f);
            geomRatios[2] = nose != null ? UtilitiesClamp01((float) Math.hypot(nose.x - midX, nose.y - midY) / (eyeDist * 1.4f)) : 0.46f;
            geomRatios[3] = (leftMouth != null && rightMouth != null)
                    ? UtilitiesClamp01((float) Math.hypot(rightMouth.x - leftMouth.x, rightMouth.y - leftMouth.y) / (eyeDist * 1.5f)) : 0.55f;
            geomRatios[4] = bottomMouth != null ? UtilitiesClamp01((float) Math.hypot(bottomMouth.x - midX, bottomMouth.y - midY) / (eyeDist * 2.0f)) : 0.62f;
            geomRatios[5] = (nose != null && bottomMouth != null)
                    ? UtilitiesClamp01((float) Math.hypot(bottomMouth.x - nose.x, bottomMouth.y - nose.y) / (eyeDist * 1.2f)) : 0.38f;
        } else {
            PointF p = face.getPosition();
            midX = p.x + face.getWidth() * 0.5f;
            midY = p.y + face.getHeight() * 0.38f;
            eyeDist = Math.max(14f, face.getWidth() * 0.42f);
            geomRatios[0] = 0.42f;
            geomRatios[1] = UtilitiesClamp01(face.getWidth() / Math.max(1f, face.getHeight()) * 0.75f);
            geomRatios[2] = 0.46f;
            geomRatios[3] = 0.55f;
            geomRatios[4] = 0.62f;
            geomRatios[5] = 0.38f;
        }
        return extractCanonicalPatchVector(bmp, midX, midY, eyeDist, geomRatios);
    }

    private float[] extractCanonicalPatchVector(@NonNull Bitmap bmp, float eyeMidX, float eyeMidY, float eyeDist, @Nullable float[] geom) {
        float[] out = new float[FEATURE_DIM];
        if (geom != null && geom.length == 6) {
            System.arraycopy(geom, 0, out, 0, 6);
        } else {
            out[0] = 0.42f; out[1] = 0.58f; out[2] = 0.46f;
            out[3] = 0.55f; out[4] = 0.62f; out[5] = 0.38f;
        }
        // Sample 6 illumination-normalized structural T-zone luminance ratios around eyeMid
        float[][] offsets = new float[][]{
                {-0.48f, 0.0f},  // left eye socket
                {0.48f, 0.0f},   // right eye socket
                {0.0f, -0.32f},  // glabella / brow bridge
                {0.0f, 0.42f},   // nasal bridge
                {-0.42f, 0.55f}, // left cheekbone
                {0.42f, 0.55f}   // right cheekbone
        };
        float[] lum = new float[6];
        float mean = 0f;
        int radius = Math.max(2, Math.round(eyeDist * 0.14f));
        for (int k = 0; k < 6; k++) {
            int cx = Math.round(eyeMidX + offsets[k][0] * eyeDist);
            int cy = Math.round(eyeMidY + offsets[k][1] * eyeDist);
            lum[k] = samplePatchLuma(bmp, cx, cy, radius);
            mean += lum[k];
        }
        mean = Math.max(1e-3f, mean / 6f);
        for (int k = 0; k < 6; k++) {
            out[6 + k] = UtilitiesClamp01((lum[k] / mean) * 0.5f);
        }
        return out;
    }

    private float samplePatchLuma(@NonNull Bitmap bmp, int cx, int cy, int r) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int x0 = Math.max(0, cx - r), x1 = Math.min(w - 1, cx + r);
        int y0 = Math.max(0, cy - r), y1 = Math.min(h - 1, cy + r);
        if (x1 < x0 || y1 < y0) return 0.5f;
        float sum = 0f;
        int cnt = 0;
        for (int y = y0; y <= y1; y += 2) {
            for (int x = x0; x <= x1; x += 2) {
                int c = bmp.getPixel(x, y);
                float l = (((c >> 16) & 0xFF) * 0.299f + ((c >> 8) & 0xFF) * 0.587f + (c & 0xFF) * 0.114f) / 255f;
                sum += l;
                cnt++;
            }
        }
        return cnt > 0 ? sum / cnt : 0.5f;
    }

    private void handleValidFaceVector(@NonNull float[] vec) {
        if (mode == MODE_ENROLL_FACE) {
            collectedSamples.add(vec);
            targetScanProgress = Math.min(1f, collectedSamples.size() / (float) ENROLL_REQUIRED_SAMPLES);
            int pct = Math.round(targetScanProgress * 100f);
            telemetrySubView.setText(LocaleController.formatString("ShieldHudCalibratingPercent", R.string.ShieldHudCalibratingPercent, pct));
            try {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            } catch (Throwable ignore) {
            }
            if (collectedSamples.size() >= ENROLL_REQUIRED_SAMPLES) {
                float[] avg = new float[FEATURE_DIM];
                for (float[] s : collectedSamples) {
                    for (int i = 0; i < FEATURE_DIM; i++) {
                        avg[i] += s[i];
                    }
                }
                for (int i = 0; i < FEATURE_DIM; i++) {
                    avg[i] /= collectedSamples.size();
                }
                ShieldGramConfig.saveEnrolledFaceVector(avg);
                completeEnrollmentSuccess();
            }
        } else {
            if (ShieldGramConfig.matchesEnrolledFaceVector(vec)) {
                consecutiveVerifyMatches++;
                targetScanProgress = Math.min(1f, consecutiveVerifyMatches / 2f);
                if (consecutiveVerifyMatches >= 2) {
                    completeWithSuccess(true);
                }
            } else {
                consecutiveVerifyMatches = 0;
                targetScanProgress = 0.25f;
                triggerErrorFlash(LocaleController.getString(R.string.ShieldHudFaceMismatch));
            }
        }
    }

    private void triggerErrorFlash(@NonNull String message) {
        hudState = 2;
        hudStateChangedMs = SystemClock.elapsedRealtime();
        telemetrySubView.setText(message);
        try {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } catch (Throwable ignore) {
        }
        reticleView.invalidate();
        if (fingerprintPodView != null) {
            fingerprintPodView.invalidate();
        }
    }

    private void completeWithSuccess(boolean viaFace) {
        if (finished) return;
        finished = true;
        hudState = 1;
        targetScanProgress = 1f;
        scanProgress = 1f;
        stopAllSensors();
        headlineView.setText(LocaleController.getString(R.string.ShieldHudAccessGranted));
        telemetrySubView.setText(viaFace
                ? LocaleController.getString(R.string.ShieldHudVerifiedByFace)
                : LocaleController.getString(R.string.ShieldHudVerifiedByFingerprint));
        try {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        } catch (Throwable ignore) {
        }
        reticleView.invalidate();
        if (fingerprintPodView != null) {
            fingerprintPodView.invalidate();
        }
        postDelayed(() -> animateDismiss(() -> callback.onAuthenticated(viaFace)), 240);
    }

    private void completeEnrollmentSuccess() {
        if (finished) return;
        finished = true;
        hudState = 1;
        targetScanProgress = 1f;
        scanProgress = 1f;
        stopAllSensors();
        headlineView.setText(LocaleController.getString(R.string.ShieldHudFaceCalibratedTitle));
        telemetrySubView.setText(LocaleController.getString(R.string.ShieldHudFaceCalibratedSub));
        try {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        } catch (Throwable ignore) {
        }
        reticleView.invalidate();
        postDelayed(() -> animateDismiss(callback::onEnrollmentCompleted), 480);
    }

    private static float UtilitiesClamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /**
     * Orbital Cyber/Gold Reticle Container framing the circular live camera viewport (or shield scanner)
     * with real-time landmark tracking dots, sweeping laser beam, and progress arc.
     */
    private final class ScannerReticleView extends FrameLayout {

        private final Paint outerOrbitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint innerOrbitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint laserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bracketPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint landmarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path circularClipPath = new Path();
        private final RectF arcRect = new RectF();
        private final Drawable centerFallbackIcon;
        private final long startMs = SystemClock.elapsedRealtime();

        ScannerReticleView(Context context) {
            super(context);
            setWillNotDraw(false);

            outerOrbitPaint.setStyle(Paint.Style.STROKE);
            outerOrbitPaint.setStrokeWidth(dpf2(1.4f));
            outerOrbitPaint.setPathEffect(new DashPathEffect(new float[]{dpf2(10f), dpf2(7f)}, 0));

            innerOrbitPaint.setStyle(Paint.Style.STROKE);
            innerOrbitPaint.setStrokeWidth(dpf2(1.2f));
            innerOrbitPaint.setPathEffect(new DashPathEffect(new float[]{dpf2(22f), dpf2(12f)}, 0));

            progressArcPaint.setStyle(Paint.Style.STROKE);
            progressArcPaint.setStrokeCap(Paint.Cap.ROUND);
            progressArcPaint.setStrokeWidth(dpf2(3.4f));

            bracketPaint.setStyle(Paint.Style.STROKE);
            bracketPaint.setStrokeCap(Paint.Cap.ROUND);
            bracketPaint.setStrokeWidth(dpf2(2.2f));

            landmarkPaint.setStyle(Paint.Style.FILL);

            int iconRes = enableFaceScanner ? R.drawable.ic_sg_face_id : R.drawable.fingerprint;
            Drawable d = ContextCompat.getDrawable(context, iconRes);
            centerFallbackIcon = d != null ? d.mutate() : null;
        }

        private int resolveActiveHudColor() {
            if (hudState == 1) {
                return 0xFF34D399;
            }
            if (hudState == 2 && SystemClock.elapsedRealtime() - hudStateChangedMs < 900L) {
                return 0xFFFB7185;
            } else if (hudState == 2) {
                hudState = 0;
            }
            return ShieldGramConfig.getSignatureAccentColor(true);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == cameraTextureView) {
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float r = Math.min(getWidth(), getHeight()) / 2f - dpf2(18f);
                circularClipPath.rewind();
                circularClipPath.addCircle(cx, cy, r, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(circularClipPath);
                boolean res = super.drawChild(canvas, child, drawingTime);
                // Subtle cyber vignette inside viewport
                vignettePaint.setShader(new RadialGradient(
                        cx, cy, Math.max(1f, r),
                        new int[]{0x00000000, 0x22000000, 0xAA040710},
                        new float[]{0f, 0.72f, 1f},
                        Shader.TileMode.CLAMP
                ));
                canvas.drawCircle(cx, cy, r, vignettePaint);
                vignettePaint.setShader(null);
                canvas.restore();
                return res;
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float maxR = Math.min(getWidth(), getHeight()) / 2f - dpf2(4f);
            float innerR = maxR - dpf2(14f);
            int color = resolveActiveHudColor();

            // Ambient radial glow behind reticle
            vignettePaint.setShader(new RadialGradient(
                    cx, cy, Math.max(1f, maxR),
                    new int[]{ColorUtils.setAlphaComponent(color, 44), 0x00000000},
                    new float[]{0.35f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(cx, cy, maxR, vignettePaint);
            vignettePaint.setShader(null);

            super.dispatchDraw(canvas);

            long elapsed = SystemClock.elapsedRealtime() - startMs;
            float rot1 = (elapsed * 0.028f) % 360f;
            float rot2 = -(elapsed * 0.042f) % 360f;

            // If camera isn't showing live preview yet, draw sleek center biometric glyph
            if ((cameraTextureView == null || cameraDevice == null) && centerFallbackIcon != null) {
                int isz = dp(56);
                centerFallbackIcon.setBounds((int) (cx - isz / 2f), (int) (cy - isz / 2f), (int) (cx + isz / 2f), (int) (cy + isz / 2f));
                centerFallbackIcon.setColorFilter(ColorUtils.setAlphaComponent(color, 220), PorterDuff.Mode.SRC_IN);
                centerFallbackIcon.draw(canvas);
            }

            // Draw live detected facial landmarks & tracking box inside circular viewport
            if (cameraDevice != null && cameraTextureView != null) {
                float vpLeft = cameraTextureView.getLeft();
                float vpTop = cameraTextureView.getTop();
                float vpW = cameraTextureView.getWidth();
                float vpH = cameraTextureView.getHeight();
                RectF box = liveFaceBoxNormalized;
                if (box != null && vpW > 0 && vpH > 0) {
                    float bl = vpLeft + box.left * vpW;
                    float bt = vpTop + box.top * vpH;
                    float br = vpLeft + box.right * vpW;
                    float bb = vpTop + box.bottom * vpH;
                    float cl = dpf2(12f);
                    bracketPaint.setColor(ColorUtils.setAlphaComponent(color, 215));
                    // 4 HUD corner brackets around tracked face
                    canvas.drawLine(bl, bt, bl + cl, bt, bracketPaint);
                    canvas.drawLine(bl, bt, bl, bt + cl, bracketPaint);
                    canvas.drawLine(br - cl, bt, br, bt, bracketPaint);
                    canvas.drawLine(br, bt, br, bt + cl, bracketPaint);
                    canvas.drawLine(bl, bb - cl, bl, bb, bracketPaint);
                    canvas.drawLine(bl, bb, bl + cl, bb, bracketPaint);
                    canvas.drawLine(br - cl, bb, br, bb, bracketPaint);
                    canvas.drawLine(br, bb - cl, br, bb, bracketPaint);
                }
                float[] pts = liveLandmarksNormalized;
                if (pts != null && vpW > 0 && vpH > 0) {
                    landmarkPaint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, 225));
                    for (int i = 0; i + 1 < pts.length; i += 2) {
                        float px = vpLeft + pts[i] * vpW;
                        float py = vpTop + pts[i + 1] * vpH;
                        if (Math.hypot(px - cx, py - cy) < innerR - dpf2(6f)) {
                            canvas.drawCircle(px, py, dpf2(2.3f), landmarkPaint);
                        }
                    }
                }
            }

            // Sweeping horizontal laser scan line inside innerR
            if (hudState != 1) {
                float phase = (float) ((Math.sin(elapsed * 0.0032) + 1.0) * 0.5);
                float ly = (cy - innerR * 0.78f) + phase * (innerR * 1.56f);
                float halfChord = (float) Math.sqrt(Math.max(0f, innerR * innerR - (ly - cy) * (ly - cy))) - dpf2(6f);
                if (halfChord > dpf2(12f)) {
                    laserPaint.setStrokeWidth(dpf2(1.8f));
                    laserPaint.setShader(new LinearGradient(
                            cx - halfChord, ly, cx + halfChord, ly,
                            new int[]{0x00000000, ColorUtils.setAlphaComponent(color, 210), 0x00000000},
                            new float[]{0f, 0.5f, 1f},
                            Shader.TileMode.CLAMP
                    ));
                    canvas.drawLine(cx - halfChord, ly, cx + halfChord, ly, laserPaint);
                    laserPaint.setShader(null);
                }
            }

            // Counter-rotating orbital rings
            canvas.save();
            canvas.rotate(rot1, cx, cy);
            outerOrbitPaint.setColor(ColorUtils.setAlphaComponent(color, 130));
            canvas.drawCircle(cx, cy, maxR, outerOrbitPaint);
            canvas.restore();

            canvas.save();
            canvas.rotate(rot2, cx, cy);
            innerOrbitPaint.setColor(ColorUtils.setAlphaComponent(color, 175));
            canvas.drawCircle(cx, cy, innerR, innerOrbitPaint);
            canvas.restore();

            // Smooth animated scan progress ring
            scanProgress += (targetScanProgress - scanProgress) * 0.18f;
            float sweep = Math.max(24f, 360f * scanProgress);
            arcRect.set(cx - innerR, cy - innerR, cx + innerR, cy + innerR);
            progressArcPaint.setColor(color);
            canvas.drawArc(arcRect, -90f + (hudState == 0 && scanProgress < 0.08f ? rot1 * 1.4f : 0f), sweep, false, progressArcPaint);

            if (!finished || hudState == 1) {
                postInvalidateOnAnimation();
            }
        }
    }

    /**
     * Pulsating holographic fingerprint pod for silent hardware sensor feedback.
     */
    private final class FingerprintPulsePod extends View {
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable fpDrawable;
        private final long t0 = SystemClock.elapsedRealtime();

        FingerprintPulsePod(Context context) {
            super(context);
            ringPaint.setStyle(Paint.Style.STROKE);
            Drawable d = ContextCompat.getDrawable(context, R.drawable.fingerprint);
            fpDrawable = d != null ? d.mutate() : null;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float maxR = Math.min(getWidth(), getHeight()) / 2f - dpf2(2f);
            int clr = hudState == 1 ? 0xFF34D399 : (hudState == 2 ? 0xFFFB7185 : ShieldGramConfig.getSignatureAccentColor(true));

            float pulse = (float) ((Math.sin((SystemClock.elapsedRealtime() - t0) * 0.0045) + 1.0) * 0.5);
            fillPaint.setColor(ColorUtils.setAlphaComponent(clr, (int) (24 + 22 * pulse)));
            canvas.drawCircle(cx, cy, maxR * (0.84f + 0.12f * pulse), fillPaint);

            ringPaint.setStrokeWidth(dpf2(1.5f));
            ringPaint.setColor(ColorUtils.setAlphaComponent(clr, (int) (190 - 90 * pulse)));
            canvas.drawCircle(cx, cy, maxR * (0.78f + 0.20f * pulse), ringPaint);

            if (fpDrawable != null) {
                int sz = dp(34);
                fpDrawable.setBounds((int) (cx - sz / 2f), (int) (cy - sz / 2f), (int) (cx + sz / 2f), (int) (cy + sz / 2f));
                fpDrawable.setColorFilter(clr, PorterDuff.Mode.SRC_IN);
                fpDrawable.draw(canvas);
            }
            if (!finished) {
                postInvalidateOnAnimation();
            }
        }
    }
}
