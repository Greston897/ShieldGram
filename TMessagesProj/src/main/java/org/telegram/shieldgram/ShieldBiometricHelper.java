package org.telegram.shieldgram;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FingerprintController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.support.fingerprint.FingerprintManagerCompat;
import org.telegram.ui.LaunchActivity;

import java.util.Locale;
import java.util.concurrent.Executor;

public final class ShieldBiometricHelper {

    public interface AuthCallback {
        void onSucceeded();
        void onFallbackToPasscode(@Nullable CharSequence reason);
        void onTransientFailure();
    }

    public static final class CancellationHandle {
        private CancellationSignal platformSignal;
        private BiometricPrompt androidxPrompt;
        private ShieldBiometricHudOverlay customHudOverlay;
        private boolean cancelled;

        public boolean isCustomHud() {
            return customHudOverlay != null;
        }

        public void cancel() {
            if (cancelled) {
                return;
            }
            cancelled = true;
            if (customHudOverlay != null) {
                try {
                    customHudOverlay.dismissImmediate();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                customHudOverlay = null;
            }
            if (platformSignal != null) {
                try {
                    platformSignal.cancel();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                platformSignal = null;
            }
            if (androidxPrompt != null) {
                try {
                    androidxPrompt.cancelAuthentication();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                androidxPrompt = null;
            }
        }
    }

    private ShieldBiometricHelper() {
    }

    public static boolean isSamsungDevice() {
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.US) : "";
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase(Locale.US) : "";
        return manufacturer.contains("samsung") || brand.contains("samsung");
    }

    public static boolean hasFrontCamera(@Nullable Context context) {
        if (context == null) {
            context = ApplicationLoader.applicationContext;
        }
        if (context == null) {
            return false;
        }
        try {
            if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= 21) {
                CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                if (cm != null) {
                    for (String id : cm.getCameraIdList()) {
                        Integer facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean hasHardwareFingerprintReady(@Nullable Context context) {
        if (Build.VERSION.SDK_INT < 23) {
            return false;
        }
        if (context == null) {
            context = ApplicationLoader.applicationContext;
        }
        if (context == null) {
            return false;
        }
        try {
            FingerprintManagerCompat fm = FingerprintManagerCompat.from(context);
            return fm.isHardwareDetected() && fm.hasEnrolledFingerprints();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static boolean hasFaceFeature(@Nullable Context context) {
        if (context == null) {
            context = ApplicationLoader.applicationContext;
        }
        if (context == null) {
            return false;
        }
        ShieldGramConfig.ensureLoaded();
        if (ShieldGramConfig.customBiometricHud && hasFrontCamera(context)) {
            return true;
        }
        try {
            PackageManager pm = context.getPackageManager();
            if (pm.hasSystemFeature("android.hardware.biometrics.face")
                    || pm.hasSystemFeature("samsung.hardware.biometrics.face")
                    || pm.hasSystemFeature("com.samsung.android.bio.face")
                    || pm.hasSystemFeature("android.hardware.biometrics.iris")
                    || pm.hasSystemFeature("com.samsung.android.bio.iris")
                    || pm.hasSystemFeature("oppo.hardware.face.support")) {
                return true;
            }
            if (isSamsungDevice() && Build.VERSION.SDK_INT >= 28 && pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
                return true;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean canAuthenticate(@Nullable Context context) {
        if (Build.VERSION.SDK_INT < 23) {
            return false;
        }
        if (context == null) {
            context = ApplicationLoader.applicationContext;
        }
        if (context == null) {
            return false;
        }
        ShieldGramConfig.ensureLoaded();

        // 0. Custom ShieldGram Biometric Special Interface (Face Camera2 Vision + Silent Hardware Fingerprint)
        if (ShieldGramConfig.customBiometricHud) {
            if (SharedConfig.useFingerprintLock && hasHardwareFingerprintReady(context)) {
                return true;
            }
            if (SharedConfig.useFaceLock && hasFrontCamera(context)) {
                return true;
            }
        }

        if (!AndroidUtilities.isKeyguardSecure()) {
            return false;
        }
        final boolean allowFaceWeak = SharedConfig.useFaceLock;

        if (Build.VERSION.SDK_INT >= 30) {
            try {
                android.hardware.biometrics.BiometricManager nativeBm =
                        context.getSystemService(android.hardware.biometrics.BiometricManager.class);
                if (nativeBm != null) {
                    int flags = allowFaceWeak
                            ? (android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
                               | android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                            : android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG;
                    int res = nativeBm.canAuthenticate(flags);
                    if (res == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS) {
                        return true;
                    }
                    if (allowFaceWeak) {
                        if (nativeBm.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                                == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS) {
                            return true;
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        if (Build.VERSION.SDK_INT == 29 && allowFaceWeak) {
            try {
                android.hardware.biometrics.BiometricManager nativeBm =
                        context.getSystemService(android.hardware.biometrics.BiometricManager.class);
                if (nativeBm != null && nativeBm.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS) {
                    return true;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        try {
            BiometricManager bm = BiometricManager.from(context);
            int authFlags = allowFaceWeak
                    ? (BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    : BiometricManager.Authenticators.BIOMETRIC_STRONG;
            if (bm.canAuthenticate(authFlags) == BiometricManager.BIOMETRIC_SUCCESS) {
                return true;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }

        try {
            FingerprintManagerCompat fm = FingerprintManagerCompat.from(context);
            if (fm.isHardwareDetected() && fm.hasEnrolledFingerprints()) {
                return true;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }

        return false;
    }

    public static boolean isHardwareSupported(@Nullable Context context) {
        if (Build.VERSION.SDK_INT < 23) {
            return false;
        }
        if (context == null) {
            context = ApplicationLoader.applicationContext;
        }
        if (context == null) {
            return false;
        }
        if (hasFrontCamera(context) || hasFaceFeature(context)) {
            return true;
        }
        try {
            return FingerprintManagerCompat.from(context).isHardwareDetected();
        } catch (Throwable e) {
            return false;
        }
    }

    public static String getDiagnosticSummary(@Nullable Context context) {
        if (context == null) {
            context = ApplicationLoader.applicationContext;
        }
        ShieldGramConfig.ensureLoaded();
        if (ShieldGramConfig.customBiometricHud) {
            boolean fpReady = hasHardwareFingerprintReady(context);
            boolean faceCalibrated = ShieldGramConfig.hasEnrolledFaceSignature();
            if (faceCalibrated && fpReady) {
                return LocaleController.getString(R.string.ShieldHudStatusDualReady);
            } else if (faceCalibrated) {
                return LocaleController.getString(R.string.ShieldHudStatusFaceReady);
            } else if (fpReady) {
                return LocaleController.getString(R.string.ShieldHudStatusFpReady);
            }
            return LocaleController.getString(R.string.ShieldHudStatusNeedsCalibration);
        }
        boolean faceHw = hasFaceFeature(context);
        boolean samsung = isSamsungDevice();
        boolean ready = canAuthenticate(context);
        if (ready) {
            if (SharedConfig.useFaceLock && faceHw) {
                return samsung
                        ? LocaleController.getString(R.string.ShieldBioStatusSamsungReady)
                        : LocaleController.getString(R.string.ShieldBioStatusFaceReady);
            }
            return LocaleController.getString(R.string.ShieldBioStatusReady);
        }
        if (!AndroidUtilities.isKeyguardSecure()) {
            return LocaleController.getString(R.string.ShieldBioStatusNoKeyguard);
        }
        if (isHardwareSupported(context)) {
            return LocaleController.getString(R.string.ShieldBioStatusNotEnrolled);
        }
        return LocaleController.getString(R.string.ShieldBioStatusUnavailable);
    }

    public static void launchFaceCalibrationOverlay(@Nullable Activity activity, @Nullable Runnable onDone) {
        if (activity == null) {
            activity = LaunchActivity.instance;
        }
        if (activity == null || activity.isFinishing()) {
            return;
        }
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (!(decor instanceof ViewGroup)) {
            return;
        }
        ViewGroup host = (ViewGroup) decor;
        ShieldBiometricHudOverlay hud = new ShieldBiometricHudOverlay(
                activity,
                ShieldBiometricHudOverlay.MODE_ENROLL_FACE,
                true,
                false,
                new ShieldBiometricHudOverlay.Callback() {
                    @Override
                    public void onAuthenticated(boolean viaFace) {
                    }

                    @Override
                    public void onEnrollmentCompleted() {
                        SharedConfig.useFaceLock = true;
                        SharedConfig.saveConfig();
                        if (onDone != null) {
                            onDone.run();
                        }
                    }

                    @Override
                    public void onDismissToManualPasscode() {
                        if (onDone != null) {
                            onDone.run();
                        }
                    }
                }
        );
        hud.showAnimated(host);
    }

    @Nullable
    public static CancellationHandle authenticate(
            @Nullable Activity activity,
            @Nullable ViewGroup customHudHost,
            @Nullable CharSequence title,
            @Nullable CharSequence subtitle,
            @Nullable CharSequence negativeButtonText,
            @NonNull AuthCallback callback
    ) {
        if (Build.VERSION.SDK_INT < 23) {
            return null;
        }
        if (activity == null) {
            activity = LaunchActivity.instance;
        }
        if (activity == null || activity.isFinishing()) {
            return null;
        }
        ShieldGramConfig.ensureLoaded();

        // 1. Custom in-app ShieldGram Biometric Special Interface (NO stock Android BiometricPrompt!)
        if (ShieldGramConfig.customBiometricHud && customHudHost != null) {
            boolean wantFace = SharedConfig.useFaceLock && hasFrontCamera(activity);
            boolean wantFp = SharedConfig.useFingerprintLock && hasHardwareFingerprintReady(activity);
            if (wantFace || wantFp) {
                final CancellationHandle handle = new CancellationHandle();
                ShieldBiometricHudOverlay overlay = new ShieldBiometricHudOverlay(
                        activity,
                        ShieldBiometricHudOverlay.MODE_UNLOCK,
                        wantFace,
                        wantFp,
                        new ShieldBiometricHudOverlay.Callback() {
                            @Override
                            public void onAuthenticated(boolean viaFace) {
                                handle.customHudOverlay = null;
                                callback.onSucceeded();
                            }

                            @Override
                            public void onEnrollmentCompleted() {
                            }

                            @Override
                            public void onDismissToManualPasscode() {
                                handle.customHudOverlay = null;
                                if (!handle.cancelled) {
                                    callback.onFallbackToPasscode(negativeButtonText);
                                }
                            }
                        }
                );
                handle.customHudOverlay = overlay;
                overlay.showAnimated(customHudHost);
                return handle;
            }
        }

        return authenticateSystemPrompt(activity, title, subtitle, negativeButtonText, callback);
    }

    @Nullable
    public static CancellationHandle authenticate(
            @Nullable Activity activity,
            @Nullable CharSequence title,
            @Nullable CharSequence subtitle,
            @Nullable CharSequence negativeButtonText,
            @NonNull AuthCallback callback
    ) {
        ViewGroup decorHost = null;
        if (activity != null && activity.getWindow() != null && activity.getWindow().getDecorView() instanceof ViewGroup) {
            decorHost = (ViewGroup) activity.getWindow().getDecorView();
        }
        return authenticate(activity, decorHost, title, subtitle, negativeButtonText, callback);
    }

    @Nullable
    private static CancellationHandle authenticateSystemPrompt(
            @NonNull Activity activity,
            @Nullable CharSequence title,
            @Nullable CharSequence subtitle,
            @Nullable CharSequence negativeButtonText,
            @NonNull AuthCallback callback
    ) {
        if (!canAuthenticate(activity)) {
            return null;
        }

        final boolean allowFace = SharedConfig.useFaceLock;
        final boolean confirmationRequired = !ShieldGramConfig.instantFaceUnlock;
        final CharSequence promptTitle = !TextUtils.isEmpty(title)
                ? title
                : LocaleController.getString(R.string.ShieldBiometricPromptTitle);
        final CharSequence promptSubtitle = !TextUtils.isEmpty(subtitle)
                ? subtitle
                : LocaleController.getString(allowFace ? R.string.ShieldBiometricPromptSubtitleFace : R.string.UnlockToUse);
        final CharSequence negText = !TextUtils.isEmpty(negativeButtonText)
                ? negativeButtonText
                : LocaleController.getString(R.string.ShieldUsePasscode);

        final Executor executor = ContextCompat.getMainExecutor(activity);
        final CancellationHandle handle = new CancellationHandle();

        if (Build.VERSION.SDK_INT >= 30 || (Build.VERSION.SDK_INT == 29 && allowFace)) {
            try {
                android.hardware.biometrics.BiometricPrompt.Builder builder =
                        new android.hardware.biometrics.BiometricPrompt.Builder(activity)
                                .setTitle(promptTitle)
                                .setConfirmationRequired(confirmationRequired);
                if (!TextUtils.isEmpty(promptSubtitle)) {
                    builder.setSubtitle(promptSubtitle);
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    int authenticators = allowFace
                            ? (android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
                               | android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                            : android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG;
                    builder.setAllowedAuthenticators(authenticators);
                }
                builder.setNegativeButton(negText, executor, (dialog, which) -> callback.onFallbackToPasscode(negText));

                android.hardware.biometrics.BiometricPrompt nativePrompt = builder.build();
                CancellationSignal signal = new CancellationSignal();
                handle.platformSignal = signal;

                nativePrompt.authenticate(signal, executor, new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) {
                        callback.onSucceeded();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        if (!handle.cancelled) {
                            callback.onFallbackToPasscode(errString);
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        callback.onTransientFailure();
                    }
                });
                return handle;
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        FragmentActivity fragmentActivity = null;
        if (activity instanceof FragmentActivity) {
            fragmentActivity = (FragmentActivity) activity;
        } else if (LaunchActivity.instance != null) {
            fragmentActivity = LaunchActivity.instance;
        }
        if (fragmentActivity == null) {
            return null;
        }

        if (!allowFace && Build.VERSION.SDK_INT >= 23 && !FingerprintController.isKeyReady()) {
            FingerprintController.checkKeyReady(false);
        }

        try {
            BiometricPrompt prompt = new BiometricPrompt(fragmentActivity, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    if (!handle.cancelled) {
                        callback.onFallbackToPasscode(errString);
                    }
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    callback.onSucceeded();
                }

                @Override
                public void onAuthenticationFailed() {
                    callback.onTransientFailure();
                }
            });
            handle.androidxPrompt = prompt;

            int authFlags = allowFace
                    ? (BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    : BiometricManager.Authenticators.BIOMETRIC_STRONG;

            BiometricPrompt.PromptInfo.Builder infoBuilder = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(promptTitle)
                    .setNegativeButtonText(negText)
                    .setConfirmationRequired(confirmationRequired)
                    .setAllowedAuthenticators(authFlags);
            if (!TextUtils.isEmpty(promptSubtitle)) {
                infoBuilder.setSubtitle(promptSubtitle);
            }
            prompt.authenticate(infoBuilder.build());
            return handle;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }
}
