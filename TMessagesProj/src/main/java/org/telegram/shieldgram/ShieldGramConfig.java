package org.telegram.shieldgram;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public final class ShieldGramConfig {

    private static final String PREFS_NAME = "shieldgram_config";

    public static final int THEME_CYBER_SAPPHIRE = 0;
    public static final int THEME_SHIELD_OBSIDIAN = 1;
    public static final int THEME_ROYAL_INDIGO = 2;
    public static final int THEME_ARCTIC_PLATINUM = 3;
    public static final int THEME_ROYAL_GOLD = 4;

    public static final int ACCENT_SAPPHIRE = 0;
    public static final int ACCENT_ROYAL_GOLD = 1;

    public static final int DOCK_MODE_FULL = 0;
    public static final int DOCK_MODE_COMPACT = 1;
    public static final int DOCK_MODE_HIDDEN = 2;

    public static final int HEADER_STYLE_SHIELD_BADGE = 0;
    public static final int HEADER_STYLE_MINIMAL = 1;
    public static final int HEADER_STYLE_STEALTH_USERNAME = 2;

    private static volatile boolean configLoaded;

    // Appearance & Navigation
    public static int selectedThemePreset = THEME_CYBER_SAPPHIRE;
    public static int accentPalette = ACCENT_SAPPHIRE;
    public static int glassDockMode = DOCK_MODE_FULL;
    public static int headerStyle = HEADER_STYLE_SHIELD_BADGE;
    public static boolean useShieldCustomIcons = false;
    public static boolean showQuickLockInHeader = true;
    public static boolean cyberGlowAccents = true;

    // Chat bubble radius (10..22 dp)
    public static int bubbleCornerRadius = 17;

    // Duress / Panic Passcode State
    public static String duressPasscodeHash = "";
    public static String duressPasscodeSaltBase64 = "";
    public static int duressPasscodeType = SharedConfig.PASSCODE_TYPE_PIN;
    public static boolean duressHideSavedMessages = true;
    public static boolean duressHideArchive = true;
    public static boolean duressHideSecretChats = true;
    public static boolean duressHideArchivedAndMuted = false;
    public static boolean duressClearRecentSearch = true;
    public static boolean duressTerminateSecretChats = false;

    public static volatile boolean duressSessionActive = false;

    private ShieldGramConfig() {
    }

    public static synchronized void ensureLoaded() {
        if (configLoaded) {
            return;
        }
        load();
    }

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
    }

    public static synchronized void load() {
        try {
            SharedPreferences prefs = getPrefs();
            selectedThemePreset = prefs.getInt("selectedThemePreset", THEME_CYBER_SAPPHIRE);
            accentPalette = prefs.getInt("accentPalette",
                    selectedThemePreset == THEME_ROYAL_GOLD ? ACCENT_ROYAL_GOLD : ACCENT_SAPPHIRE);
            glassDockMode = prefs.getInt("glassDockMode", DOCK_MODE_FULL);
            headerStyle = prefs.getInt("headerStyle", HEADER_STYLE_SHIELD_BADGE);
            useShieldCustomIcons = false;
            showQuickLockInHeader = prefs.getBoolean("showQuickLockInHeader", true);
            cyberGlowAccents = prefs.getBoolean("cyberGlowAccents", true);
            bubbleCornerRadius = Utilities.clamp(prefs.getInt("bubbleCornerRadius", 17), 22, 10);

            duressPasscodeHash = prefs.getString("duressPasscodeHash", "");
            duressPasscodeSaltBase64 = prefs.getString("duressPasscodeSalt", "");
            duressPasscodeType = prefs.getInt("duressPasscodeType", SharedConfig.PASSCODE_TYPE_PIN);
            duressHideSavedMessages = prefs.getBoolean("duressHideSavedMessages", true);
            duressHideArchive = prefs.getBoolean("duressHideArchive", true);
            duressHideSecretChats = prefs.getBoolean("duressHideSecretChats", true);
            duressHideArchivedAndMuted = prefs.getBoolean("duressHideArchivedAndMuted", false);
            duressClearRecentSearch = prefs.getBoolean("duressClearRecentSearch", true);
            duressTerminateSecretChats = prefs.getBoolean("duressTerminateSecretChats", false);
            duressSessionActive = prefs.getBoolean("duressSessionActive", false);
            configLoaded = true;
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static synchronized void save() {
        try {
            getPrefs().edit()
                    .putInt("selectedThemePreset", selectedThemePreset)
                    .putInt("accentPalette", accentPalette)
                    .putInt("glassDockMode", glassDockMode)
                    .putInt("headerStyle", headerStyle)
                    .putBoolean("showQuickLockInHeader", showQuickLockInHeader)
                    .putBoolean("cyberGlowAccents", cyberGlowAccents)
                    .putInt("bubbleCornerRadius", bubbleCornerRadius)
                    .putString("duressPasscodeHash", duressPasscodeHash)
                    .putString("duressPasscodeSalt", duressPasscodeSaltBase64)
                    .putInt("duressPasscodeType", duressPasscodeType)
                    .putBoolean("duressHideSavedMessages", duressHideSavedMessages)
                    .putBoolean("duressHideArchive", duressHideArchive)
                    .putBoolean("duressHideSecretChats", duressHideSecretChats)
                    .putBoolean("duressHideArchivedAndMuted", duressHideArchivedAndMuted)
                    .putBoolean("duressClearRecentSearch", duressClearRecentSearch)
                    .putBoolean("duressTerminateSecretChats", duressTerminateSecretChats)
                    .putBoolean("duressSessionActive", duressSessionActive)
                    .apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static boolean isRoyalGoldActive() {
        ensureLoaded();
        if (selectedThemePreset == THEME_ROYAL_GOLD || accentPalette == ACCENT_ROYAL_GOLD) {
            return true;
        }
        try {
            Theme.ThemeInfo active = Theme.getActiveTheme();
            if (active != null && "Royal Gold".equals(active.name)) {
                return true;
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    public static int getSignatureAccentColor(boolean dark) {
        ensureLoaded();
        if (isRoyalGoldActive()) {
            return dark ? 0xFFF2C955 : 0xFFC5921E;
        }
        return dark ? 0xFF5B8CFF : 0xFF2563EB;
    }

    public static int getSignatureSecondaryColor(boolean dark) {
        ensureLoaded();
        if (isRoyalGoldActive()) {
            return dark ? 0xFFC89628 : 0xFFA37010;
        }
        return dark ? 0xFF38BDF8 : 0xFF1D4ED8;
    }

    public static int getBrandCrestResId() {
        ensureLoaded();
        if (isRoyalGoldActive()) {
            return R.drawable.sg_crest_gold;
        }
        if (selectedThemePreset == THEME_SHIELD_OBSIDIAN) {
            return R.drawable.sg_crest_obsidian;
        }
        return R.drawable.sg_crest_sapphire;
    }

    public static void setThemePreset(int preset) {
        ensureLoaded();
        selectedThemePreset = preset;
        if (preset == THEME_ROYAL_GOLD) {
            accentPalette = ACCENT_ROYAL_GOLD;
        } else {
            accentPalette = ACCENT_SAPPHIRE;
        }
        save();
    }

    public static void setGlassDockMode(int mode) {
        ensureLoaded();
        glassDockMode = mode;
        save();
        notifyUiChanged();
    }

    public static void setHeaderStyle(int style) {
        ensureLoaded();
        headerStyle = style;
        save();
        notifyUiChanged();
    }

    public static void setBubbleCornerRadius(int radiusDp) {
        ensureLoaded();
        bubbleCornerRadius = Utilities.clamp(radiusDp, 22, 10);
        SharedConfig.bubbleRadius = bubbleCornerRadius;
        SharedConfig.saveConfig();
        save();
        notifyUiChanged();
    }

    public static void setShowQuickLockInHeader(boolean enabled) {
        ensureLoaded();
        showQuickLockInHeader = enabled;
        save();
        notifyUiChanged();
    }

    public static boolean isDockHidden() {
        ensureLoaded();
        return glassDockMode == DOCK_MODE_HIDDEN;
    }

    public static boolean isDockCompact() {
        ensureLoaded();
        return glassDockMode == DOCK_MODE_COMPACT;
    }

    public static boolean hasDuressPasscode() {
        ensureLoaded();
        return !TextUtils.isEmpty(duressPasscodeHash) && !TextUtils.isEmpty(duressPasscodeSaltBase64);
    }

    public static void clearDuressPasscode() {
        ensureLoaded();
        duressPasscodeHash = "";
        duressPasscodeSaltBase64 = "";
        duressSessionActive = false;
        save();
        notifyUiChanged();
    }

    public static boolean setDuressPasscode(String rawPasscode, int type) {
        if (TextUtils.isEmpty(rawPasscode)) {
            return false;
        }
        ensureLoaded();
        byte[] salt = new byte[16];
        Utilities.random.nextBytes(salt);
        String computed = computePasscodeSaltedHash(rawPasscode, salt);
        if (TextUtils.isEmpty(computed)) {
            return false;
        }
        duressPasscodeSaltBase64 = Base64.encodeToString(salt, Base64.DEFAULT);
        duressPasscodeHash = computed;
        duressPasscodeType = type;
        duressSessionActive = false;
        save();
        return true;
    }

    public static boolean checkDuressPasscode(String candidate) {
        ensureLoaded();
        if (!hasDuressPasscode() || TextUtils.isEmpty(candidate)) {
            return false;
        }
        try {
            byte[] salt = Base64.decode(duressPasscodeSaltBase64, Base64.DEFAULT);
            if (salt == null || salt.length != 16) {
                return false;
            }
            String candidateHash = computePasscodeSaltedHash(candidate, salt);
            return !TextUtils.isEmpty(candidateHash) && TextUtils.equals(duressPasscodeHash, candidateHash);
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    private static String computePasscodeSaltedHash(String passcode, byte[] salt) {
        try {
            byte[] passcodeBytes = passcode.getBytes(StandardCharsets.UTF_8);
            byte[] bytes = new byte[32 + passcodeBytes.length];
            System.arraycopy(salt, 0, bytes, 0, 16);
            System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
            System.arraycopy(salt, 0, bytes, passcodeBytes.length + 16, 16);
            return Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
        } catch (Throwable e) {
            FileLog.e(e);
            return "";
        }
    }

    public static void triggerDuressUnlock() {
        ensureLoaded();
        duressSessionActive = true;
        save();
        executeDuressActions();
    }

    public static void clearDuressSessionOnPrimaryUnlock() {
        ensureLoaded();
        if (duressSessionActive) {
            duressSessionActive = false;
            save();
            notifyAllAccountDialogsReload();
        }
    }

    private static void executeDuressActions() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                continue;
            }
            final int account = a;
            if (duressClearRecentSearch) {
                try {
                    MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
                        try {
                            MessagesStorage.getInstance(account).getDatabase().executeFast("DELETE FROM hashtag_recent_v2 WHERE 1").stepThis().dispose();
                        } catch (Throwable ignore) {
                        }
                    });
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            if (duressTerminateSecretChats) {
                AndroidUtilitiesSafeRun(() -> {
                    try {
                        ArrayList<TLRPC.Dialog> all = new ArrayList<>(MessagesController.getInstance(account).getAllDialogs());
                        for (int i = 0; i < all.size(); i++) {
                            long did = all.get(i).id;
                            if (DialogObject.isEncryptedDialog(did)) {
                                int encId = DialogObject.getEncryptedChatId(did);
                                SecretChatHelper.getInstance(account).declineSecretChat(encId, true);
                            }
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                });
            }
        }
        notifyAllAccountDialogsReload();
    }

    public static ArrayList<TLRPC.Dialog> filterDialogsForDuress(int currentAccount, int dialogsType, int folderId, ArrayList<TLRPC.Dialog> source) {
        ensureLoaded();
        if (!duressSessionActive || source == null || source.isEmpty()) {
            return source;
        }
        if (folderId == 1 && duressHideArchive) {
            return new ArrayList<>(0);
        }
        long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        MessagesController mc = MessagesController.getInstance(currentAccount);
        ArrayList<TLRPC.Dialog> filtered = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            TLRPC.Dialog d = source.get(i);
            if (d == null) {
                continue;
            }
            if (d instanceof TLRPC.TL_dialogFolder && duressHideArchive) {
                continue;
            }
            long did = d.id;
            if (duressHideSavedMessages && did == selfId) {
                continue;
            }
            if (duressHideSecretChats && DialogObject.isEncryptedDialog(did)) {
                continue;
            }
            if (duressHideArchivedAndMuted && mc.isDialogMuted(did, 0)) {
                continue;
            }
            filtered.add(d);
        }
        return filtered;
    }

    private static void notifyAllAccountDialogsReload() {
        AndroidUtilitiesSafeRun(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogsNeedReload, true);
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                }
            }
        });
    }

    public static void notifyUiChanged() {
        AndroidUtilitiesSafeRun(() -> {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false);
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                }
            }
        });
    }

    private static void AndroidUtilitiesSafeRun(Runnable r) {
        try {
            org.telegram.messenger.AndroidUtilities.runOnUIThread(r);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
