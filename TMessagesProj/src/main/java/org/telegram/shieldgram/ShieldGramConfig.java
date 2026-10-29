package org.telegram.shieldgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.DialogsSearchAdapter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public final class ShieldGramConfig {

    private static final String PREFS_NAME = "shieldgram_config";
    private static final Object SYNC = new Object();
    private static boolean loaded;

    // Theme presets
    public static final int THEME_CYBER_SAPPHIRE = 0;
    public static final int THEME_SHIELD_OBSIDIAN = 1;
    public static final int THEME_ROYAL_INDIGO = 2;
    public static final int THEME_CLASSIC_NIGHT = 3;
    public static final int THEME_ROYAL_GOLD = 4;

    // Signature accent palettes
    public static final int ACCENT_SAPPHIRE = 0;
    public static final int ACCENT_ROYAL_GOLD = 1;
    public static final int ACCENT_EMERALD = 2;

    // Avatar shapes
    public static final int AVATAR_SHAPE_CIRCLE = 0;
    public static final int AVATAR_SHAPE_SQUIRCLE = 1;
    public static final int AVATAR_SHAPE_CYBER_CARD = 2;

    // Floating glass dock modes
    public static final int DOCK_MODE_FLOATING = 0;
    public static final int DOCK_MODE_COMPACT = 1;
    public static final int DOCK_MODE_HIDDEN = 2;

    // Folder tab styles
    public static final int FOLDER_TAB_PILLS = 0;
    public static final int FOLDER_TAB_UNDERLINE = 1;

    // Dialogs header styles
    public static final int HEADER_STYLE_SHIELD_BADGE = 0;
    public static final int HEADER_STYLE_MINIMAL = 1;
    public static final int HEADER_STYLE_STEALTH_USERNAME = 2;

    // Visual & Layout customization state
    public static int themePreset = THEME_CYBER_SAPPHIRE;
    public static String activeThemeKey = "Cyber Sapphire";
    public static int accentPalette = ACCENT_SAPPHIRE;
    public static int avatarShape = AVATAR_SHAPE_SQUIRCLE;
    public static int glassDockMode = DOCK_MODE_FLOATING;
    public static int folderTabStyle = FOLDER_TAB_PILLS;
    public static int headerStyle = HEADER_STYLE_SHIELD_BADGE;
    public static int bubbleCornerRadius = 18;

    public static boolean modernChatCards = true;
    public static boolean cyberGlowAccents = true;
    public static boolean chatGlassInputBar = true;
    public static boolean useShieldCustomIcons = true;
    public static boolean showQuickLockInHeader = true;

    // Custom ShieldGram Special Biometric Interface (Face HUD + Hardware Fingerprint HUD)
    public static boolean customBiometricHud = true;
    public static boolean instantFaceUnlock = true;
    public static String faceSignatureData = "";

    // Duress / Panic Decoy Mode state
    public static boolean duressEnabled = false;
    public static String duressPasscodeHash = "";
    public static byte[] duressPasscodeSalt = new byte[0];
    public static int duressPasscodeType = 0;
    public static boolean duressSessionActive = false;

    public static boolean duressHideSavedMessages = true;
    public static boolean duressHideArchive = true;
    public static boolean duressHideSecretChats = true;
    public static boolean duressHideArchivedAndMuted = false;
    public static boolean duressClearRecentSearch = false;
    public static boolean duressTerminateSecretChats = false;

    private ShieldGramConfig() {
    }

    public static void ensureLoaded() {
        synchronized (SYNC) {
            if (loaded || ApplicationLoader.applicationContext == null) {
                return;
            }
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            themePreset = prefs.getInt("themePreset", THEME_CYBER_SAPPHIRE);
            activeThemeKey = prefs.getString("activeThemeKey", "Cyber Sapphire");
            accentPalette = prefs.getInt("accentPalette", ACCENT_SAPPHIRE);
            avatarShape = prefs.getInt("avatarShape", AVATAR_SHAPE_SQUIRCLE);
            glassDockMode = prefs.getInt("glassDockMode", DOCK_MODE_FLOATING);
            folderTabStyle = prefs.getInt("folderTabStyle", FOLDER_TAB_PILLS);
            headerStyle = prefs.getInt("headerStyle", HEADER_STYLE_SHIELD_BADGE);
            bubbleCornerRadius = prefs.getInt("bubbleCornerRadius", 18);

            modernChatCards = prefs.getBoolean("modernChatCards", true);
            cyberGlowAccents = prefs.getBoolean("cyberGlowAccents", true);
            chatGlassInputBar = prefs.getBoolean("chatGlassInputBar", true);
            useShieldCustomIcons = prefs.getBoolean("useShieldCustomIcons", true);
            showQuickLockInHeader = prefs.getBoolean("showQuickLockInHeader", true);

            customBiometricHud = prefs.getBoolean("customBiometricHud", true);
            instantFaceUnlock = prefs.getBoolean("instantFaceUnlock", true);
            faceSignatureData = prefs.getString("faceSignatureData", "");

            duressEnabled = prefs.getBoolean("duressEnabled", false);
            duressPasscodeHash = prefs.getString("duressPasscodeHash", "");
            duressPasscodeType = prefs.getInt("duressPasscodeType", 0);
            duressSessionActive = prefs.getBoolean("duressSessionActive", false);
            String saltBase64 = prefs.getString("duressPasscodeSalt", "");
            if (!TextUtils.isEmpty(saltBase64)) {
                try {
                    duressPasscodeSalt = Base64.decode(saltBase64, Base64.DEFAULT);
                } catch (Exception e) {
                    duressPasscodeSalt = new byte[0];
                }
            } else {
                duressPasscodeSalt = new byte[0];
            }

            duressHideSavedMessages = prefs.getBoolean("duressHideSavedMessages", true);
            duressHideArchive = prefs.getBoolean("duressHideArchive", true);
            duressHideSecretChats = prefs.getBoolean("duressHideSecretChats", true);
            duressHideArchivedAndMuted = prefs.getBoolean("duressHideArchivedAndMuted", false);
            duressClearRecentSearch = prefs.getBoolean("duressClearRecentSearch", false);
            duressTerminateSecretChats = prefs.getBoolean("duressTerminateSecretChats", false);

            loaded = true;
        }
    }

    public static void save() {
        ensureLoaded();
        synchronized (SYNC) {
            if (ApplicationLoader.applicationContext == null) {
                return;
            }
            try {
                SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor e = prefs.edit();
                e.putInt("themePreset", themePreset);
                e.putString("activeThemeKey", activeThemeKey);
                e.putInt("accentPalette", accentPalette);
                e.putInt("avatarShape", avatarShape);
                e.putInt("glassDockMode", glassDockMode);
                e.putInt("folderTabStyle", folderTabStyle);
                e.putInt("headerStyle", headerStyle);
                e.putInt("bubbleCornerRadius", bubbleCornerRadius);

                e.putBoolean("modernChatCards", modernChatCards);
                e.putBoolean("cyberGlowAccents", cyberGlowAccents);
                e.putBoolean("chatGlassInputBar", chatGlassInputBar);
                e.putBoolean("useShieldCustomIcons", useShieldCustomIcons);
                e.putBoolean("showQuickLockInHeader", showQuickLockInHeader);

                e.putBoolean("customBiometricHud", customBiometricHud);
                e.putBoolean("instantFaceUnlock", instantFaceUnlock);
                e.putString("faceSignatureData", faceSignatureData != null ? faceSignatureData : "");

                e.putBoolean("duressEnabled", duressEnabled);
                e.putString("duressPasscodeHash", duressPasscodeHash);
                e.putInt("duressPasscodeType", duressPasscodeType);
                e.putBoolean("duressSessionActive", duressSessionActive);
                e.putString("duressPasscodeSalt", duressPasscodeSalt != null && duressPasscodeSalt.length > 0
                        ? Base64.encodeToString(duressPasscodeSalt, Base64.DEFAULT) : "");

                e.putBoolean("duressHideSavedMessages", duressHideSavedMessages);
                e.putBoolean("duressHideArchive", duressHideArchive);
                e.putBoolean("duressHideSecretChats", duressHideSecretChats);
                e.putBoolean("duressHideArchivedAndMuted", duressHideArchivedAndMuted);
                e.putBoolean("duressClearRecentSearch", duressClearRecentSearch);
                e.putBoolean("duressTerminateSecretChats", duressTerminateSecretChats);
                e.apply();
            } catch (Exception ex) {
                FileLog.e(ex);
            }
        }
    }

    public static void setThemePreset(int preset) {
        ensureLoaded();
        themePreset = preset;
        switch (preset) {
            case THEME_ROYAL_GOLD:
                activeThemeKey = "Royal Gold";
                accentPalette = ACCENT_ROYAL_GOLD;
                break;
            case THEME_SHIELD_OBSIDIAN:
                activeThemeKey = "Shield Obsidian";
                break;
            case THEME_ROYAL_INDIGO:
                activeThemeKey = "Royal Indigo";
                break;
            case THEME_CLASSIC_NIGHT:
                activeThemeKey = "Dark Blue";
                break;
            case THEME_CYBER_SAPPHIRE:
            default:
                activeThemeKey = "Cyber Sapphire";
                if (accentPalette == ACCENT_ROYAL_GOLD) {
                    accentPalette = ACCENT_SAPPHIRE;
                }
                break;
        }
        save();
    }

    public static void setAccentPalette(int palette) {
        ensureLoaded();
        accentPalette = palette;
        save();
    }

    public static boolean isRoyalGoldActive() {
        ensureLoaded();
        if (accentPalette == ACCENT_ROYAL_GOLD || themePreset == THEME_ROYAL_GOLD) {
            return true;
        }
        try {
            Theme.ThemeInfo current = Theme.getActiveTheme();
            if (current != null && "Royal Gold".equals(current.name)) {
                return true;
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    public static int getSignatureAccentColor(boolean dark) {
        ensureLoaded();
        if (isRoyalGoldActive()) {
            return dark ? 0xFFF2C955 : 0xFFB8860B;
        }
        if (accentPalette == ACCENT_EMERALD) {
            return dark ? 0xFF34D399 : 0xFF059669;
        }
        return dark ? 0xFF4C8DFF : 0xFF2563EB;
    }

    public static int getSignatureSecondaryColor(boolean dark) {
        ensureLoaded();
        if (isRoyalGoldActive()) {
            return dark ? 0xFFC89628 : 0xFF996B15;
        }
        if (accentPalette == ACCENT_EMERALD) {
            return dark ? 0xFF10B981 : 0xFF047857;
        }
        return dark ? 0xFF2B63D9 : 0xFF1D4ED8;
    }

    public static int getBrandCrestResId() {
        ensureLoaded();
        if (isRoyalGoldActive()) {
            return R.drawable.sg_crest_gold;
        }
        if (themePreset == THEME_SHIELD_OBSIDIAN) {
            return R.drawable.sg_crest_obsidian;
        }
        return R.drawable.sg_crest_sapphire;
    }

    public static void setAvatarShape(int shape) {
        ensureLoaded();
        avatarShape = shape;
        save();
    }

    public static void setGlassDockMode(int mode) {
        ensureLoaded();
        glassDockMode = mode;
        save();
    }

    public static void setFolderTabStyle(int style) {
        ensureLoaded();
        folderTabStyle = style;
        save();
    }

    public static void setHeaderStyle(int style) {
        ensureLoaded();
        headerStyle = style;
        save();
    }

    public static void setBubbleCornerRadius(int radiusDp) {
        ensureLoaded();
        bubbleCornerRadius = Math.max(10, Math.min(24, radiusDp));
        save();
    }

    public static void setModernChatCards(boolean enabled) {
        ensureLoaded();
        modernChatCards = enabled;
        save();
    }

    public static void setCyberGlowAccents(boolean enabled) {
        ensureLoaded();
        cyberGlowAccents = enabled;
        save();
    }

    public static void setChatGlassInputBar(boolean enabled) {
        ensureLoaded();
        chatGlassInputBar = enabled;
        save();
    }

    public static void setUseShieldCustomIcons(boolean enabled) {
        ensureLoaded();
        useShieldCustomIcons = enabled;
        save();
    }

    public static void setShowQuickLockInHeader(boolean enabled) {
        ensureLoaded();
        showQuickLockInHeader = enabled;
        save();
    }

    public static void setCustomBiometricHud(boolean enabled) {
        ensureLoaded();
        customBiometricHud = enabled;
        save();
    }

    public static void setInstantFaceUnlock(boolean enabled) {
        ensureLoaded();
        instantFaceUnlock = enabled;
        save();
    }

    // Custom Face Recognition Signature Storage
    public static boolean hasEnrolledFaceSignature() {
        ensureLoaded();
        float[] vec = getEnrolledFaceVector();
        return vec != null && vec.length >= 6;
    }

    public static void saveEnrolledFaceVector(float[] vector) {
        ensureLoaded();
        if (vector == null || vector.length < 6) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US, "%.5f", vector[i]));
        }
        byte[] raw = sb.toString().getBytes(StandardCharsets.UTF_8);
        faceSignatureData = Base64.encodeToString(raw, Base64.NO_WRAP);
        save();
    }

    public static float[] getEnrolledFaceVector() {
        ensureLoaded();
        if (TextUtils.isEmpty(faceSignatureData)) {
            return null;
        }
        try {
            String decoded = new String(Base64.decode(faceSignatureData, Base64.DEFAULT), StandardCharsets.UTF_8);
            String[] parts = decoded.split(",");
            if (parts.length < 6) {
                return null;
            }
            float[] res = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                res[i] = Float.parseFloat(parts[i]);
            }
            return res;
        } catch (Exception e) {
            return null;
        }
    }

    public static void clearEnrolledFaceVector() {
        ensureLoaded();
        faceSignatureData = "";
        save();
    }

    public static boolean matchesEnrolledFaceVector(float[] candidate) {
        float[] ref = getEnrolledFaceVector();
        if (ref == null || candidate == null || ref.length != candidate.length) {
            return false;
        }
        float sumSq = 0f;
        for (int i = 0; i < ref.length; i++) {
            float d = ref[i] - candidate[i];
            sumSq += d * d;
        }
        float rmsd = (float) Math.sqrt(sumSq / ref.length);
        // Normalized facial landmark ratios typically vary < 0.055 for the same person across lighting/angles
        return rmsd <= 0.095f;
    }

    public static int getAvatarRoundRadius(int defaultRadiusPx) {
        ensureLoaded();
        if (avatarShape == AVATAR_SHAPE_SQUIRCLE) {
            return Math.max(AndroidUtilities.dp(6), Math.round(defaultRadiusPx * 0.62f));
        } else if (avatarShape == AVATAR_SHAPE_CYBER_CARD) {
            return Math.max(AndroidUtilities.dp(5), Math.round(defaultRadiusPx * 0.42f));
        }
        return defaultRadiusPx;
    }

    public static boolean isDockCompact() {
        ensureLoaded();
        return glassDockMode == DOCK_MODE_COMPACT;
    }

    public static boolean isDockHidden() {
        ensureLoaded();
        return glassDockMode == DOCK_MODE_HIDDEN;
    }

    public static boolean hasDuressPasscode() {
        ensureLoaded();
        return duressEnabled && !TextUtils.isEmpty(duressPasscodeHash) && duressPasscodeSalt != null && duressPasscodeSalt.length == 16;
    }

    public static void setDuressPasscode(String code, int passcodeType) {
        ensureLoaded();
        if (TextUtils.isEmpty(code)) {
            clearDuressPasscode();
            return;
        }
        try {
            byte[] salt = new byte[16];
            Utilities.random.nextBytes(salt);
            byte[] codeBytes = code.getBytes(StandardCharsets.UTF_8);
            byte[] bytes = new byte[32 + codeBytes.length];
            System.arraycopy(salt, 0, bytes, 0, 16);
            System.arraycopy(codeBytes, 0, bytes, 16, codeBytes.length);
            System.arraycopy(salt, 0, bytes, 16 + codeBytes.length, 16);
            duressPasscodeSalt = salt;
            duressPasscodeHash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
            duressPasscodeType = passcodeType;
            duressEnabled = true;
            save();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void clearDuressPasscode() {
        ensureLoaded();
        duressEnabled = false;
        duressPasscodeHash = "";
        duressPasscodeSalt = new byte[0];
        duressSessionActive = false;
        save();
    }

    public static boolean checkDuressPasscode(String candidate) {
        ensureLoaded();
        if (!hasDuressPasscode() || TextUtils.isEmpty(candidate)) {
            return false;
        }
        try {
            byte[] codeBytes = candidate.getBytes(StandardCharsets.UTF_8);
            byte[] bytes = new byte[32 + codeBytes.length];
            System.arraycopy(duressPasscodeSalt, 0, bytes, 0, 16);
            System.arraycopy(codeBytes, 0, bytes, 16, codeBytes.length);
            System.arraycopy(duressPasscodeSalt, 0, bytes, 16 + codeBytes.length, 16);
            String hash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
            return duressPasscodeHash.equals(hash);
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void triggerDuressUnlock() {
        ensureLoaded();
        duressSessionActive = true;
        save();

        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (!UserConfig.getInstance(a).isClientActivated()) {
                    continue;
                }
                if (duressClearRecentSearch) {
                    try {
                        DialogsSearchAdapter.clearRecentSearch(a);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
                if (duressTerminateSecretChats) {
                    try {
                        MessagesController mc = AccountInstance.getInstance(a).getMessagesController();
                        ArrayList<TLRPC.Dialog> all = new ArrayList<>(mc.getAllDialogs());
                        for (int i = 0; i < all.size(); i++) {
                            TLRPC.Dialog d = all.get(i);
                            if (d != null && DialogObject.isEncryptedDialog(d.id)) {
                                int encId = DialogObject.getEncryptedChatId(d.id);
                                SecretChatHelper.getInstance(a).declineSecretChat(encId, true);
                            }
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
                NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogsNeedReload, true);
            }
            if (duressClearRecentSearch) {
                try {
                    ImageLoader.getInstance().clearMemory();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        });
    }

    public static void clearDuressSessionOnPrimaryUnlock() {
        ensureLoaded();
        if (duressSessionActive) {
            duressSessionActive = false;
            save();
            AndroidUtilities.runOnUIThread(() -> {
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogsNeedReload, true);
                    }
                }
            });
        }
    }

    public static boolean shouldHideDialogInDuress(int currentAccount, TLRPC.Dialog dialog) {
        ensureLoaded();
        if (!duressSessionActive || dialog == null) {
            return false;
        }
        long id = dialog.id;
        if (duressHideArchive && (DialogObject.isFolderDialogId(id) || dialog.folder_id == 1)) {
            return true;
        }
        if (duressHideSecretChats && DialogObject.isEncryptedDialog(id)) {
            return true;
        }
        if (duressHideSavedMessages && id == UserConfig.getInstance(currentAccount).getClientUserId()) {
            return true;
        }
        if (duressHideArchivedAndMuted) {
            if (dialog.folder_id == 1) {
                return true;
            }
            try {
                if (MessagesController.getInstance(currentAccount).isDialogMuted(id, 0)) {
                    return true;
                }
            } catch (Throwable ignore) {
            }
        }
        return false;
    }

    public static ArrayList<TLRPC.Dialog> filterDialogsForDuress(int currentAccount, int dialogsType, int folderId, ArrayList<TLRPC.Dialog> source) {
        ensureLoaded();
        if (!duressSessionActive || source == null) {
            return source;
        }
        if ((duressHideArchive || duressHideArchivedAndMuted) && folderId != 0) {
            return new ArrayList<>(0);
        }
        ArrayList<TLRPC.Dialog> filtered = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            TLRPC.Dialog d = source.get(i);
            if (!shouldHideDialogInDuress(currentAccount, d)) {
                filtered.add(d);
            }
        }
        return filtered;
    }
}
