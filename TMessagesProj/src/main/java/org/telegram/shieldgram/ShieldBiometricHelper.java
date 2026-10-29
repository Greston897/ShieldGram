package org.telegram.shieldgram;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.support.fingerprint.FingerprintManagerCompat;
import org.telegram.ui.LaunchActivity;

import java.util.concurrent.Executor;

/**
 * Clean native Android BiometricPrompt helper for ShieldGram.
 * Uses Android's standard system dialog for Fingerprint and Face Unlock.
 */
public final class ShieldBiometricHelper {

    public static final int ALLOWED_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.BIOMETRIC_WEAK;

    private ShieldBiometricHelper() {
    }

    public interface AuthCallback {
        void onSucceeded();
        void onFallbackToPasscode(@Nullable CharSequence reason);
        void onTransientFailure();
    }

    public interface CancellationHandle {
        void cancel();
    }

    public static boolean hasFaceFeature(@Nullable Context context) {
        if (context == null || Build.VERSION.SDK_INT < 29) {
            return false;
        }
        try {
            return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BIOMETRIC_FACE);
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static boolean hasFingerprintHardware(@Nullable Context context) {
        if (context == null || Build.VERSION.SDK_INT < 23) {
            return false;
        }
        try {
            FingerprintManagerCompat fm = FingerprintManagerCompat.from(context);
            return fm.isHardwareDetected();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static boolean canAuthenticate(@Nullable Context context) {
        Context ctx = context != null ? context : ApplicationLoader.applicationContext;
        if (ctx == null || Build.VERSION.SDK_INT < 23) {
            return false;
        }
        try {
            int res = BiometricManager.from(ctx).canAuthenticate(ALLOWED_AUTHENTICATORS);
            if (res == BiometricManager.BIOMETRIC_SUCCESS) {
                return true;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        try {
            FingerprintManagerCompat fm = FingerprintManagerCompat.from(ctx);
            return fm.isHardwareDetected() && fm.hasEnrolledFingerprints();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static boolean isBiometricHardwarePresent(@Nullable Context context) {
        Context ctx = context != null ? context : ApplicationLoader.applicationContext;
        if (ctx == null || Build.VERSION.SDK_INT < 23) {
            return false;
        }
        try {
            int res = BiometricManager.from(ctx).canAuthenticate(ALLOWED_AUTHENTICATORS);
            return res == BiometricManager.BIOMETRIC_SUCCESS
                    || res == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED;
        } catch (Throwable e) {
            FileLog.e(e);
            return hasFingerprintHardware(ctx) || hasFaceFeature(ctx);
        }
    }

    @Nullable
    public static CancellationHandle authenticate(
            @Nullable Activity activity,
            @NonNull CharSequence title,
            @Nullable CharSequence subtitle,
            @NonNull CharSequence negativeButtonText,
            @NonNull AuthCallback callback
    ) {
        FragmentActivity hostActivity = null;
        if (activity instanceof FragmentActivity) {
            hostActivity = (FragmentActivity) activity;
        } else if (LaunchActivity.instance != null) {
            hostActivity = LaunchActivity.instance;
        }
        if (hostActivity == null || hostActivity.isFinishing()) {
            return null;
        }
        if (!canAuthenticate(hostActivity)) {
            return null;
        }

        try {
            Executor executor = ContextCompat.getMainExecutor(hostActivity);
            final BiometricPrompt prompt = new BiometricPrompt(hostActivity, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    FileLog.d("ShieldBiometricHelper onAuthenticationError " + errorCode + ": " + errString);
                    callback.onFallbackToPasscode(errString);
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    FileLog.d("ShieldBiometricHelper onAuthenticationSucceeded");
                    callback.onSucceeded();
                }

                @Override
                public void onAuthenticationFailed() {
                    FileLog.d("ShieldBiometricHelper onAuthenticationFailed");
                    callback.onTransientFailure();
                }
            });

            BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setNegativeButtonText(negativeButtonText)
                    .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                    .setConfirmationRequired(false);

            if (!TextUtils.isEmpty(subtitle)) {
                builder.setSubtitle(subtitle);
            }

            prompt.authenticate(builder.build());
            return () -> {
                try {
                    prompt.cancelAuthentication();
                } catch (Throwable ignore) {
                }
            };
        } catch (Throwable e) {
            FileLog.e(e);
            callback.onFallbackToPasscode(null);
            return null;
        }
    }
}
