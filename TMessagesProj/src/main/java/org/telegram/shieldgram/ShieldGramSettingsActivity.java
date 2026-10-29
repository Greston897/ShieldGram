package org.telegram.shieldgram;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LauncherIconController;
import org.telegram.ui.PasscodeActivity;

public class ShieldGramSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private int rowCount;

    private int heroRow;

    private int appearanceHeaderRow;
    private int themePresetRow;
    private int cyberGlowRow;
    private int appearanceInfoRow;

    private int layoutHeaderRow;
    private int launcherIconRow;
    private int customUiIconsRow;
    private int dockModeRow;
    private int headerStyleRow;
    private int quickLockHeaderRow;
    private int modernChatCardsRow;
    private int avatarShapeRow;
    private int folderTabsStyleRow;
    private int layoutInfoRow;

    private int securityHeaderRow;
    private int managePasscodeRow;
    private int systemFaceUnlockRow;
    private int instantFaceUnlockRow;
    private int fingerprintUnlockRow;
    private int testBiometricsRow;
    private int preventScreenshotsRow;
    private int securityInfoRow;

    private int duressHeaderRow;
    private int duressSetupRow;
    private int duressHideSavedRow;
    private int duressHideArchiveRow;
    private int duressHideSecretRow;
    private int duressHideMutedRow;
    private int duressClearSearchRow;
    private int duressWipeSecretsRow;
    private int duressInfoRow;

    private ShieldBiometricHelper.CancellationHandle activeBioTestHandle;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        ShieldGramConfig.ensureLoaded();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetPasscode);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (activeBioTestHandle != null) {
            activeBioTestHandle.cancel();
            activeBioTestHandle = null;
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetPasscode);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetPasscode || id == NotificationCenter.didSetNewTheme) {
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    private void updateRows() {
        rowCount = 0;

        heroRow = rowCount++;

        appearanceHeaderRow = rowCount++;
        themePresetRow = rowCount++;
        cyberGlowRow = rowCount++;
        appearanceInfoRow = rowCount++;

        layoutHeaderRow = rowCount++;
        launcherIconRow = rowCount++;
        customUiIconsRow = rowCount++;
        dockModeRow = rowCount++;
        headerStyleRow = rowCount++;
        quickLockHeaderRow = rowCount++;
        modernChatCardsRow = rowCount++;
        avatarShapeRow = rowCount++;
        folderTabsStyleRow = rowCount++;
        layoutInfoRow = rowCount++;

        securityHeaderRow = rowCount++;
        managePasscodeRow = rowCount++;
        if (Build.VERSION.SDK_INT >= 23 && ShieldBiometricHelper.isHardwareSupported(ApplicationLoaderSafeContext())) {
            systemFaceUnlockRow = rowCount++;
            instantFaceUnlockRow = SharedConfig.useFaceLock ? rowCount++ : -1;
            fingerprintUnlockRow = rowCount++;
            testBiometricsRow = rowCount++;
        } else {
            systemFaceUnlockRow = -1;
            instantFaceUnlockRow = -1;
            fingerprintUnlockRow = -1;
            testBiometricsRow = -1;
        }
        preventScreenshotsRow = rowCount++;
        securityInfoRow = rowCount++;

        duressHeaderRow = rowCount++;
        duressSetupRow = rowCount++;
        boolean showDuressOptions = ShieldGramConfig.hasDuressPasscode() && !ShieldGramConfig.duressSessionActive;
        if (showDuressOptions) {
            duressHideSavedRow = rowCount++;
            duressHideArchiveRow = rowCount++;
            duressHideSecretRow = rowCount++;
            duressHideMutedRow = rowCount++;
            duressClearSearchRow = rowCount++;
            duressWipeSecretsRow = rowCount++;
        } else {
            duressHideSavedRow = -1;
            duressHideArchiveRow = -1;
            duressHideSecretRow = -1;
            duressHideMutedRow = -1;
            duressClearSearchRow = -1;
            duressWipeSecretsRow = -1;
        }
        duressInfoRow = rowCount++;
    }

    private Context ApplicationLoaderSafeContext() {
        return getParentActivity() != null ? getParentActivity() : org.telegram.messenger.ApplicationLoader.applicationContext;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.ShieldGramCenterTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(listAdapter = new ListAdapter(context));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (position == themePresetRow) {
                showThemePresetSelector();
            } else if (position == cyberGlowRow) {
                ShieldGramConfig.setCyberGlowAccents(!ShieldGramConfig.cyberGlowAccents);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.cyberGlowAccents);
                }
                notifyGlobalUiChanged();
            } else if (position == launcherIconRow) {
                showLauncherIconSelector();
            } else if (position == customUiIconsRow) {
                ShieldGramConfig.setUseShieldCustomIcons(!ShieldGramConfig.useShieldCustomIcons);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.useShieldCustomIcons);
                }
                notifyGlobalUiChanged();
            } else if (position == dockModeRow) {
                showDockModeSelector();
            } else if (position == headerStyleRow) {
                showHeaderStyleSelector();
            } else if (position == quickLockHeaderRow) {
                ShieldGramConfig.setShowQuickLockInHeader(!ShieldGramConfig.showQuickLockInHeader);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.showQuickLockInHeader);
                }
                notifyGlobalUiChanged();
            } else if (position == modernChatCardsRow) {
                ShieldGramConfig.setModernChatCards(!ShieldGramConfig.modernChatCards);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.modernChatCards);
                }
                notifyGlobalUiChanged();
            } else if (position == avatarShapeRow) {
                showAvatarShapeSelector();
            } else if (position == folderTabsStyleRow) {
                showFolderTabStyleSelector();
            } else if (position == managePasscodeRow) {
                presentFragment(PasscodeActivity.determineOpenFragment());
            } else if (position == systemFaceUnlockRow) {
                SharedConfig.useFaceLock = !SharedConfig.useFaceLock;
                SharedConfig.saveConfig();
                updateRows();
                listAdapter.notifyDataSetChanged();
            } else if (position == instantFaceUnlockRow) {
                ShieldGramConfig.setInstantFaceUnlock(!ShieldGramConfig.instantFaceUnlock);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.instantFaceUnlock);
                }
            } else if (position == fingerprintUnlockRow) {
                SharedConfig.useFingerprintLock = !SharedConfig.useFingerprintLock;
                SharedConfig.saveConfig();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.useFingerprintLock);
                }
            } else if (position == testBiometricsRow) {
                runLiveBiometricTest();
            } else if (position == preventScreenshotsRow) {
                SharedConfig.allowScreenCapture = !SharedConfig.allowScreenCapture;
                SharedConfig.saveConfig();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(!SharedConfig.allowScreenCapture);
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
            } else if (position == duressSetupRow) {
                showDuressSetupDialog();
            } else if (position == duressHideSavedRow) {
                ShieldGramConfig.duressHideSavedMessages = !ShieldGramConfig.duressHideSavedMessages;
                ShieldGramConfig.save();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.duressHideSavedMessages);
                }
            } else if (position == duressHideArchiveRow) {
                ShieldGramConfig.duressHideArchive = !ShieldGramConfig.duressHideArchive;
                ShieldGramConfig.save();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.duressHideArchive);
                }
            } else if (position == duressHideSecretRow) {
                ShieldGramConfig.duressHideSecretChats = !ShieldGramConfig.duressHideSecretChats;
                ShieldGramConfig.save();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.duressHideSecretChats);
                }
            } else if (position == duressHideMutedRow) {
                ShieldGramConfig.duressHideArchivedAndMuted = !ShieldGramConfig.duressHideArchivedAndMuted;
                ShieldGramConfig.save();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.duressHideArchivedAndMuted);
                }
            } else if (position == duressClearSearchRow) {
                ShieldGramConfig.duressClearRecentSearch = !ShieldGramConfig.duressClearRecentSearch;
                ShieldGramConfig.save();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.duressClearRecentSearch);
                }
            } else if (position == duressWipeSecretsRow) {
                ShieldGramConfig.duressTerminateSecretChats = !ShieldGramConfig.duressTerminateSecretChats;
                ShieldGramConfig.save();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.duressTerminateSecretChats);
                }
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void notifyGlobalUiChanged() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false);
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void showThemePresetSelector() {
        if (getParentActivity() == null) {
            return;
        }
        CharSequence[] items = new CharSequence[]{
                getString(R.string.ShieldThemeCyberSapphire),
                getString(R.string.ShieldThemeObsidian),
                getString(R.string.ShieldThemeRoyalIndigo),
                getString(R.string.ShieldThemeClassicNight)
        };
        String[] themeKeys = new String[]{
                "Cyber Sapphire",
                "Shield Obsidian",
                "Royal Indigo",
                "Dark Blue"
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ShieldPresetTheme));
        builder.setItems(items, (dialog, which) -> {
            if (which < 0 || which >= themeKeys.length) {
                return;
            }
            ShieldGramConfig.setThemePreset(which);
            Theme.ThemeInfo themeInfo = Theme.getTheme(themeKeys[which]);
            if (themeInfo != null) {
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
                Theme.saveAutoNightThemeConfig();
                NotificationCenter.getGlobalInstance().postNotificationName(
                        NotificationCenter.needSetDayNightTheme, themeInfo, false, null, -1);
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        showDialog(builder.create());
    }

    private void showLauncherIconSelector() {
        if (getParentActivity() == null) {
            return;
        }
        final LauncherIconController.LauncherIcon[] icons = LauncherIconController.LauncherIcon.values();
        CharSequence[] labels = new CharSequence[icons.length];
        for (int i = 0; i < icons.length; i++) {
            String name = getString(icons[i].title);
            if (LauncherIconController.isEnabled(icons[i])) {
                name = "✓  " + name;
            }
            labels[i] = name;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ShieldAppIconSelector));
        builder.setItems(labels, (dialog, which) -> {
            if (which >= 0 && which < icons.length) {
                LauncherIconController.setIcon(icons[which]);
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        });
        showDialog(builder.create());
    }

    private void showDockModeSelector() {
        if (getParentActivity() == null) {
            return;
        }
        CharSequence[] items = new CharSequence[]{
                getString(R.string.ShieldDockFull),
                getString(R.string.ShieldDockCompact),
                getString(R.string.ShieldDockHidden)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ShieldDockMode));
        builder.setItems(items, (d, which) -> {
            ShieldGramConfig.setGlassDockMode(which);
            notifyGlobalUiChanged();
        });
        showDialog(builder.create());
    }

    private void showHeaderStyleSelector() {
        if (getParentActivity() == null) {
            return;
        }
        CharSequence[] items = new CharSequence[]{
                getString(R.string.ShieldHeaderBadge),
                getString(R.string.ShieldHeaderMinimal),
                getString(R.string.ShieldHeaderStealth)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ShieldHeaderStyle));
        builder.setItems(items, (d, which) -> {
            ShieldGramConfig.setHeaderStyle(which);
            notifyGlobalUiChanged();
        });
        showDialog(builder.create());
    }

    private void showAvatarShapeSelector() {
        if (getParentActivity() == null) {
            return;
        }
        CharSequence[] items = new CharSequence[]{
                getString(R.string.ShieldAvatarCircle),
                getString(R.string.ShieldAvatarSquircle),
                getString(R.string.ShieldAvatarCyberCard)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ShieldAvatarShape));
        builder.setItems(items, (d, which) -> {
            ShieldGramConfig.setAvatarShape(which);
            notifyGlobalUiChanged();
        });
        showDialog(builder.create());
    }

    private void showFolderTabStyleSelector() {
        if (getParentActivity() == null) {
            return;
        }
        CharSequence[] items = new CharSequence[]{
                getString(R.string.ShieldFolderPills),
                getString(R.string.ShieldFolderUnderline)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ShieldFolderTabStyle));
        builder.setItems(items, (d, which) -> {
            ShieldGramConfig.setFolderTabStyle(which);
            notifyGlobalUiChanged();
        });
        showDialog(builder.create());
    }

    private void runLiveBiometricTest() {
        if (getParentActivity() == null) {
            return;
        }
        if (!ShieldBiometricHelper.canAuthenticate(getParentActivity())) {
            BulletinFactory.of(this).createErrorBulletin(ShieldBiometricHelper.getDiagnosticSummary(getParentActivity())).show();
            return;
        }
        if (activeBioTestHandle != null) {
            activeBioTestHandle.cancel();
        }
        activeBioTestHandle = ShieldBiometricHelper.authenticate(
                getParentActivity(),
                getString(R.string.ShieldBiometricPromptTitle),
                getString(R.string.ShieldBiometricPromptSubtitleFace),
                getString(R.string.Cancel),
                new ShieldBiometricHelper.AuthCallback() {
                    @Override
                    public void onSucceeded() {
                        activeBioTestHandle = null;
                        BulletinFactory.of(ShieldGramSettingsActivity.this)
                                .createSimpleBulletin(R.drawable.ic_sg_face_id, getString(R.string.ShieldTestBiometricSuccess))
                                .show();
                    }

                    @Override
                    public void onFallbackToPasscode(CharSequence reason) {
                        activeBioTestHandle = null;
                    }

                    @Override
                    public void onTransientFailure() {
                    }
                }
        );
    }

    private void showDuressSetupDialog() {
        if (getParentActivity() == null) {
            return;
        }
        if (SharedConfig.passcodeHash.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.ShieldDuressErrorNeedMain)).show();
            return;
        }

        Context context = getParentActivity();
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(8), dp(24), dp(4));

        TextView msgView = new TextView(context);
        msgView.setText(getString(R.string.ShieldDuressSetDialogMessage));
        msgView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        msgView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        container.addView(msgView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        final EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setBackground(Theme.createEditTextDrawable(context, false));
        input.setSingleLine(true);
        input.setHint(getString(R.string.ShieldDuressInputHint));
        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN || SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN6) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        input.setTransformationMethod(PasswordTransformationMethod.getInstance());
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        input.setCursorSize(dp(20));
        input.setCursorWidth(1.5f);
        input.setPadding(0, dp(8), 0, dp(10));
        container.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.ShieldDuressSetDialogTitle));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            String raw = input.getText() != null ? input.getText().toString().trim() : "";
            if (raw.length() < 4) {
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.ShieldDuressErrorTooShort)).show();
                return;
            }
            if (SharedConfig.checkPasscode(raw)) {
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.ShieldDuressErrorSameAsMain)).show();
                return;
            }
            int dType = SharedConfig.passcodeType;
            if (raw.length() == 4 && TextUtils.isDigitsOnly(raw)) {
                dType = SharedConfig.PASSCODE_TYPE_PIN;
            } else if (raw.length() == 6 && TextUtils.isDigitsOnly(raw)) {
                dType = SharedConfig.PASSCODE_TYPE_PIN6;
            } else {
                dType = SharedConfig.PASSCODE_TYPE_PASSWORD;
            }
            ShieldGramConfig.setDuressPasscode(raw, dType);
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            BulletinFactory.of(this).createSimpleBulletin(R.drawable.ic_sg_panic, getString(R.string.ShieldDuressSavedToast)).show();
        });
        if (ShieldGramConfig.hasDuressPasscode() && !ShieldGramConfig.duressSessionActive) {
            builder.setNeutralButton(getString(R.string.ShieldDuressDisableAction), (dialog, which) -> {
                ShieldGramConfig.clearDuressPasscode();
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                BulletinFactory.of(this).createSimpleBulletin(R.drawable.ic_sg_shield, getString(R.string.ShieldDuressClearedToast)).show();
            });
        }
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        AndroidUtilities.runOnUIThread(() -> {
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        }, 120);
    }

    private String getCurrentThemeLabel() {
        Theme.ThemeInfo cur = Theme.getActiveTheme();
        if (cur != null && cur.name != null) {
            switch (cur.name) {
                case "Cyber Sapphire":
                    return getString(R.string.ShieldThemeCyberSapphire);
                case "Shield Obsidian":
                    return getString(R.string.ShieldThemeObsidian);
                case "Royal Indigo":
                    return getString(R.string.ShieldThemeRoyalIndigo);
                case "Dark Blue":
                    return getString(R.string.ShieldThemeClassicNight);
                default:
                    return cur.getName();
            }
        }
        return getString(R.string.ShieldThemeCyberSapphire);
    }

    private String getActiveIconLabel() {
        for (LauncherIconController.LauncherIcon icon : LauncherIconController.LauncherIcon.values()) {
            if (LauncherIconController.isEnabled(icon)) {
                return getString(icon.title);
            }
        }
        return getString(R.string.ShieldIconCyberSapphire);
    }

    private String getActiveLockTypeLabel() {
        if (SharedConfig.passcodeHash.isEmpty()) {
            return getString(R.string.ShieldLockOff);
        }
        switch (SharedConfig.passcodeType) {
            case SharedConfig.PASSCODE_TYPE_PIN6:
                return getString(R.string.ShieldLockActivePin6);
            case SharedConfig.PASSCODE_TYPE_PATTERN:
                return getString(R.string.ShieldLockActivePattern);
            case SharedConfig.PASSCODE_TYPE_PASSWORD:
                return getString(R.string.ShieldLockActivePassword);
            case SharedConfig.PASSCODE_TYPE_PIN:
            default:
                return getString(R.string.ShieldLockActivePin4);
        }
    }

    private static class ShieldHeroBannerCell extends FrameLayout {
        private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();
        private final TextView statusChip;
        private final TextView bioStatusView;

        public ShieldHeroBannerCell(Context context) {
            super(context);
            setWillNotDraw(false);
            setPadding(dp(16), dp(14), dp(16), dp(10));

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dpf2(1.2f));

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_HORIZONTAL);
            content.setPadding(dp(20), dp(20), dp(20), dp(20));

            ImageView shieldLogo = new ImageView(context);
            Drawable d = ContextCompat.getDrawable(context, R.drawable.ic_sg_shield);
            if (d != null) {
                d = d.mutate();
                d.setTint(0xFF6BA0FF);
                shieldLogo.setImageDrawable(d);
            }
            content.addView(shieldLogo, LayoutHelper.createLinear(46, 46, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

            TextView title = new TextView(context);
            title.setText("ShieldGram");
            title.setTypeface(AndroidUtilities.bold());
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            title.setTextColor(0xFFF8FAFC);
            title.setGravity(Gravity.CENTER);
            content.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            TextView sub = new TextView(context);
            sub.setText(LocaleController.getString(R.string.ShieldGramHeroTagline));
            sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            sub.setTextColor(0xFFA5B8D8);
            sub.setGravity(Gravity.CENTER);
            content.addView(sub, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 12));

            statusChip = new TextView(context);
            statusChip.setTypeface(AndroidUtilities.bold());
            statusChip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            statusChip.setPadding(dp(14), dp(5), dp(14), dp(5));
            statusChip.setGravity(Gravity.CENTER);
            content.addView(statusChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            bioStatusView = new TextView(context);
            bioStatusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            bioStatusView.setTextColor(0xFF7FA2E0);
            bioStatusView.setGravity(Gravity.CENTER);
            content.addView(bioStatusView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

            addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        public void bind() {
            boolean armed = !SharedConfig.passcodeHash.isEmpty();
            statusChip.setText(armed
                    ? LocaleController.getString(R.string.ShieldGramHeroStatusArmed)
                    : LocaleController.getString(R.string.ShieldGramHeroStatusUnarmed));
            statusChip.setTextColor(armed ? 0xFF6EE7B7 : 0xFF94A3B8);
            statusChip.setBackground(Theme.createRoundRectDrawable(dp(14), armed ? 0x2610B981 : 0x2264748B));
            bioStatusView.setText(ShieldBiometricHelper.getDiagnosticSummary(getContext()));
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            cardRect.set(dp(14), dp(10), getWidth() - dp(14), getHeight() - dp(6));
            LinearGradient grad = new LinearGradient(
                    cardRect.left, cardRect.top,
                    cardRect.right, cardRect.bottom,
                    new int[]{0xFF0C1324, 0xFF132242, 0xFF1D356B},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP
            );
            cardPaint.setShader(grad);
            canvas.drawRoundRect(cardRect, dp(20), dp(20), cardPaint);
            cardPaint.setShader(null);

            borderPaint.setColor(ColorUtils.setAlphaComponent(0xFF5B8CFF, 110));
            canvas.drawRoundRect(cardRect, dp(20), dp(20), borderPaint);
            super.onDraw(canvas);
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;

        private static final int VIEW_HERO = 0;
        private static final int VIEW_HEADER = 1;
        private static final int VIEW_TEXT_CELL = 2;
        private static final int VIEW_CHECK_CELL = 3;
        private static final int VIEW_INFO = 4;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == VIEW_TEXT_CELL || type == VIEW_CHECK_CELL;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == heroRow) {
                return VIEW_HERO;
            }
            if (position == appearanceHeaderRow || position == layoutHeaderRow
                    || position == securityHeaderRow || position == duressHeaderRow) {
                return VIEW_HEADER;
            }
            if (position == appearanceInfoRow || position == layoutInfoRow
                    || position == securityInfoRow || position == duressInfoRow) {
                return VIEW_INFO;
            }
            if (position == themePresetRow || position == launcherIconRow
                    || position == dockModeRow || position == headerStyleRow
                    || position == avatarShapeRow || position == folderTabsStyleRow
                    || position == managePasscodeRow || position == testBiometricsRow
                    || position == duressSetupRow) {
                return VIEW_TEXT_CELL;
            }
            return VIEW_CHECK_CELL;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_HERO:
                    view = new ShieldHeroBannerCell(mContext);
                    break;
                case VIEW_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TEXT_CELL:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_CHECK_CELL:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_INFO:
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
            ));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_HERO: {
                    ((ShieldHeroBannerCell) holder.itemView).bind();
                    break;
                }
                case VIEW_HEADER: {
                    HeaderCell header = (HeaderCell) holder.itemView;
                    if (position == appearanceHeaderRow) {
                        header.setText(getString(R.string.ShieldSectionAppearance));
                    } else if (position == layoutHeaderRow) {
                        header.setText(getString(R.string.ShieldSectionLayout));
                    } else if (position == securityHeaderRow) {
                        header.setText(getString(R.string.ShieldSectionSecurity));
                    } else if (position == duressHeaderRow) {
                        header.setText(getString(R.string.ShieldSectionDuress));
                    }
                    break;
                }
                case VIEW_TEXT_CELL: {
                    TextCell cell = (TextCell) holder.itemView;
                    cell.setPrioritizeTitleOverValue(false);
                    if (position == themePresetRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.ShieldPresetTheme), getCurrentThemeLabel(), R.drawable.ic_sg_palette, true);
                    } else if (position == launcherIconRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.ShieldAppIconSelector), getActiveIconLabel(), R.drawable.ic_sg_shield, true);
                    } else if (position == dockModeRow) {
                        String val = ShieldGramConfig.glassDockMode == ShieldGramConfig.DOCK_MODE_COMPACT
                                ? getString(R.string.ShieldDockCompact)
                                : (ShieldGramConfig.glassDockMode == ShieldGramConfig.DOCK_MODE_HIDDEN
                                ? getString(R.string.ShieldDockHidden)
                                : getString(R.string.ShieldDockFull));
                        cell.setTextAndValueAndIcon(getString(R.string.ShieldDockMode), val, R.drawable.ic_sg_dock, true);
                    } else if (position == headerStyleRow) {
                        String val = ShieldGramConfig.headerStyle == ShieldGramConfig.HEADER_STYLE_MINIMAL
                                ? getString(R.string.ShieldHeaderMinimal)
                                : (ShieldGramConfig.headerStyle == ShieldGramConfig.HEADER_STYLE_STEALTH_USERNAME
                                ? getString(R.string.ShieldHeaderStealth)
                                : getString(R.string.ShieldHeaderBadge));
                        cell.setTextAndValue(getString(R.string.ShieldHeaderStyle), val, true);
                    } else if (position == avatarShapeRow) {
                        String val = ShieldGramConfig.avatarShape == ShieldGramConfig.AVATAR_SHAPE_SQUIRCLE
                                ? getString(R.string.ShieldAvatarSquircle)
                                : (ShieldGramConfig.avatarShape == ShieldGramConfig.AVATAR_SHAPE_CYBER_CARD
                                ? getString(R.string.ShieldAvatarCyberCard)
                                : getString(R.string.ShieldAvatarCircle));
                        cell.setTextAndValue(getString(R.string.ShieldAvatarShape), val, true);
                    } else if (position == folderTabsStyleRow) {
                        String val = ShieldGramConfig.folderTabStyle == ShieldGramConfig.FOLDER_TAB_UNDERLINE
                                ? getString(R.string.ShieldFolderUnderline)
                                : getString(R.string.ShieldFolderPills);
                        cell.setTextAndValue(getString(R.string.ShieldFolderTabStyle), val, false);
                    } else if (position == managePasscodeRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.ShieldManagePasscode), getActiveLockTypeLabel(), R.drawable.ic_sg_pattern, true);
                    } else if (position == testBiometricsRow) {
                        cell.setTextAndIcon(getString(R.string.ShieldTestBiometricNow), R.drawable.ic_sg_face_id, true);
                    } else if (position == duressSetupRow) {
                        boolean configuredVisible = ShieldGramConfig.hasDuressPasscode() && !ShieldGramConfig.duressSessionActive;
                        cell.setTextAndValueAndIcon(
                                getString(R.string.ShieldDuressSetup),
                                configuredVisible ? getString(R.string.ShieldDuressConfigured) : getString(R.string.ShieldDuressNotSet),
                                R.drawable.ic_sg_panic,
                                configuredVisible
                        );
                    }
                    break;
                }
                case VIEW_CHECK_CELL: {
                    TextCheckCell check = (TextCheckCell) holder.itemView;
                    if (position == cyberGlowRow) {
                        check.setTextAndCheck(getString(R.string.ShieldCyberGlow), ShieldGramConfig.cyberGlowAccents, false);
                    } else if (position == customUiIconsRow) {
                        check.setTextAndCheck(getString(R.string.ShieldCustomUiIcons), ShieldGramConfig.useShieldCustomIcons, true);
                    } else if (position == quickLockHeaderRow) {
                        check.setTextAndCheck(getString(R.string.ShieldQuickLockButton), ShieldGramConfig.showQuickLockInHeader, true);
                    } else if (position == modernChatCardsRow) {
                        check.setTextAndCheck(getString(R.string.ShieldModernChatCards), ShieldGramConfig.modernChatCards, true);
                    } else if (position == systemFaceUnlockRow) {
                        check.setTextAndCheck(getString(R.string.ShieldSystemFaceUnlock), SharedConfig.useFaceLock, true);
                    } else if (position == instantFaceUnlockRow) {
                        check.setTextAndCheck(getString(R.string.ShieldInstantFaceUnlock), ShieldGramConfig.instantFaceUnlock, true);
                    } else if (position == fingerprintUnlockRow) {
                        check.setTextAndCheck(getString(R.string.ShieldFingerprintUnlock), SharedConfig.useFingerprintLock, true);
                    } else if (position == preventScreenshotsRow) {
                        check.setTextAndCheck(getString(R.string.ShieldPreventScreenshots), !SharedConfig.allowScreenCapture, false);
                    } else if (position == duressHideSavedRow) {
                        check.setTextAndCheck(getString(R.string.ShieldDuressHideSaved), ShieldGramConfig.duressHideSavedMessages, true);
                    } else if (position == duressHideArchiveRow) {
                        check.setTextAndCheck(getString(R.string.ShieldDuressHideArchive), ShieldGramConfig.duressHideArchive, true);
                    } else if (position == duressHideSecretRow) {
                        check.setTextAndCheck(getString(R.string.ShieldDuressHideSecret), ShieldGramConfig.duressHideSecretChats, true);
                    } else if (position == duressHideMutedRow) {
                        check.setTextAndCheck(getString(R.string.ShieldDuressHideMuted), ShieldGramConfig.duressHideArchivedAndMuted, true);
                    } else if (position == duressClearSearchRow) {
                        check.setTextAndCheck(getString(R.string.ShieldDuressClearSearch), ShieldGramConfig.duressClearRecentSearch, true);
                    } else if (position == duressWipeSecretsRow) {
                        check.setTextAndCheck(getString(R.string.ShieldDuressWipeSecrets), ShieldGramConfig.duressTerminateSecretChats, false);
                    }
                    break;
                }
                case VIEW_INFO: {
                    TextInfoPrivacyCell info = (TextInfoPrivacyCell) holder.itemView;
                    info.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    if (position == appearanceInfoRow) {
                        info.setText(getString(R.string.ShieldAppearanceHint));
                    } else if (position == layoutInfoRow) {
                        info.setText(getString(R.string.ShieldLayoutHint));
                    } else if (position == securityInfoRow) {
                        info.setText(getString(R.string.ShieldSecurityBioHint));
                    } else if (position == duressInfoRow) {
                        info.setText(getString(R.string.ShieldDuressHint));
                    }
                    break;
                }
            }
        }
    }
}
