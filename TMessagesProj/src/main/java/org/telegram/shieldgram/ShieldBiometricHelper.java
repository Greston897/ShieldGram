package org.telegram.shieldgram;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.text.TextUtils;

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
        private boolean cancelled;

        public void cancel() {
            if (cancelled) {
                return;
            }
            cancelled = true;
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

    public static boolean hasFaceFeature(@Nullable Context context) {
        if (context == null) {
            context = ApplicationLoader.applicationContext;
        }
        if (context == null) {
            return false;
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
            // Samsung One UI provides system 2D Face Recognition on virtually all devices with a front camera
            if (isSamsungDevice() && Build.VERSION.SDK_INT >= 28 && pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
                return true;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return false;
    }

    /**
     * Checks whether any system biometric (Face Unlock Class 2 / Weak OR Fingerprint / Class 3 Strong)
     * is enrolled and ready to authenticate right now.
     */
    public static boolean canAuthenticate(@Nullable Context context) {
        if (Build.VERSION.SDK_INT < 23) {
            return false;
        }
        if (context == null) {
            context = ApplicationLoader.applicationContext;
        }
        if (context == null || !AndroidUtilities.isKeyguardSecure()) {
            return false;
        }
        ShieldGramConfig.ensureLoaded();
        final boolean allowFaceWeak = SharedConfig.useFaceLock;

        // 1. Android 11+ (API 30+): query native android.hardware.biometrics.BiometricManager directly
        // so Samsung One UI Class 2 Face Recognition (BIOMETRIC_WEAK) is never filtered out.
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

        // 2. Android 10 (API 29): native BiometricManager.canAuthenticate() covers Samsung Face & Fingerprint
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

        // 3. AndroidX BiometricManager check
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

        // 4. Legacy FingerprintManager fallback for older Android 6-8 devices
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

    /**
     * Checks whether the device supports biometric hardware at all (even if user hasn't enrolled yet),
     * so settings toggles are always visible on capable phones.
     */
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
        if (hasFaceFeature(context)) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                android.hardware.biometrics.BiometricManager nativeBm =
                        context.getSystemService(android.hardware.biometrics.BiometricManager.class);
                if (nativeBm != null) {
                    int res = nativeBm.canAuthenticate(
                            android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
                                    | android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK);
                    if (res == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
                            || res == android.hardware.biometrics.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                        return true;
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        try {
            BiometricManager bm = BiometricManager.from(context);
            int res = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);
            if (res == BiometricManager.BIOMETRIC_SUCCESS || res == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                return true;
            }
        } catch (Throwable e) {
            FileLog.e(e);
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

    @Nullable
    public static CancellationHandle authenticate(
            @Nullable Activity activity,
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
        if (!canAuthenticate(activity)) {
            return null;
        }

        ShieldGramConfig.ensureLoaded();
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

        // Preferred path on Android 11+ (API 30+) and Android 10 (API 29) when Face Unlock is enabled:
        // Use the platform's native android.hardware.biometrics.BiometricPrompt directly!
        // Why: androidx.biometric:1.1.0 has legacy Samsung device quirks that force FingerprintDialogFragment
        // on certain Galaxy models and block Samsung One UI's native Face Recognition sheet.
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
                builder.setNegativeButton(negText, executor, (dialog, which) -> {
                    callback.onFallbackToPasscode(negText);
                });

                android.hardware.biometrics.BiometricPrompt nativePrompt = builder.build();
                CancellationSignal signal = new CancellationSignal();
                handle.platformSignal = signal;

                nativePrompt.authenticate(signal, executor, new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) {
                        FileLog.d("ShieldBiometricHelper native onAuthenticationSucceeded");
                        callback.onSucceeded();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        FileLog.d("ShieldBiometricHelper native onAuthenticationError " + errorCode + ": " + errString);
                        if (!handle.cancelled) {
                            callback.onFallbackToPasscode(errString);
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        FileLog.d("ShieldBiometricHelper native onAuthenticationFailed (continuing scan)");
                        callback.onTransientFailure();
                    }
                });
                return handle;
            } catch (Throwable e) {
                FileLog.e(e);
                // Fall through to AndroidX BiometricPrompt below if anything unexpected occurs
            }
        }

        // Fallback path for Android 6.0 - 9.0 (API 23-28) or if platform builder threw
        FragmentActivity fragmentActivity = null;
        if (activity instanceof FragmentActivity) {
            fragmentActivity = (FragmentActivity) activity;
        } else if (LaunchActivity.instance != null) {
            fragmentActivity = LaunchActivity.instance;
        }
        if (fragmentActivity == null) {
            return null;
        }

        // Ensure legacy keystore key doesn't block non-crypto biometric unlock
        if (!allowFace && Build.VERSION.SDK_INT >= 23 && !FingerprintController.isKeyReady()) {
            FingerprintController.checkKeyReady(false);
        }

        try {
            BiometricPrompt prompt = new BiometricPrompt(fragmentActivity, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    FileLog.d("ShieldBiometricHelper androidx onAuthenticationError " + errorCode + ": " + errString);
                    if (!handle.cancelled) {
                        callback.onFallbackToPasscode(errString);
                    }
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    FileLog.d("ShieldBiometricHelper androidx onAuthenticationSucceeded");
                    callback.onSucceeded();
                }

                @Override
                public void onAuthenticationFailed() {
                    FileLog.d("ShieldBiometricHelper androidx onAuthenticationFailed");
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
