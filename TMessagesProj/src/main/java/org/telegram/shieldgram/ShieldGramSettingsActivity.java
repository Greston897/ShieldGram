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
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
    private int iconCarouselRow;

    private int appearanceHeaderRow;
    private int themePresetRow;
    private int accentPaletteRow;
    private int cyberGlowRow;
    private int appearanceInfoRow;

    private int chatStudioHeaderRow;
    private int chatPreviewRow;
    private int bubbleRadiusRow;
    private int glassInputOutlineRow;

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
    private int customBioHudRow;
    private int calibrateFaceRow;
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
            if (fragmentView != null) {
                fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    private void updateRows() {
        rowCount = 0;

        heroRow = rowCount++;
        iconCarouselRow = rowCount++;

        appearanceHeaderRow = rowCount++;
        themePresetRow = rowCount++;
        accentPaletteRow = rowCount++;
        cyberGlowRow = rowCount++;
        appearanceInfoRow = rowCount++;

        chatStudioHeaderRow = rowCount++;
        chatPreviewRow = rowCount++;
        bubbleRadiusRow = rowCount++;
        glassInputOutlineRow = rowCount++;

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
            customBioHudRow = rowCount++;
            calibrateFaceRow = ShieldGramConfig.customBiometricHud && ShieldBiometricHelper.hasFrontCamera(ApplicationLoaderSafeContext()) ? rowCount++ : -1;
            systemFaceUnlockRow = rowCount++;
            instantFaceUnlockRow = (!ShieldGramConfig.customBiometricHud && SharedConfig.useFaceLock) ? rowCount++ : -1;
            fingerprintUnlockRow = rowCount++;
            testBiometricsRow = rowCount++;
        } else {
            customBioHudRow = -1;
            calibrateFaceRow = -1;
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
            } else if (position == accentPaletteRow) {
                showAccentPaletteSelector();
            } else if (position == cyberGlowRow) {
                ShieldGramConfig.setCyberGlowAccents(!ShieldGramConfig.cyberGlowAccents);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.cyberGlowAccents);
                }
                notifyGlobalUiChanged();
            } else if (position == bubbleRadiusRow) {
                showBubbleRadiusSelector();
            } else if (position == glassInputOutlineRow) {
                ShieldGramConfig.setChatGlassInputBar(!ShieldGramConfig.chatGlassInputBar);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.chatGlassInputBar);
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
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload, true);
                notifyGlobalUiChanged();
            } else if (position == avatarShapeRow) {
                showAvatarShapeSelector();
            } else if (position == folderTabsStyleRow) {
                showFolderTabStyleSelector();
            } else if (position == managePasscodeRow) {
                presentFragment(PasscodeActivity.determineOpenFragment());
            } else if (position == customBioHudRow) {
                ShieldGramConfig.setCustomBiometricHud(!ShieldGramConfig.customBiometricHud);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.customBiometricHud);
                }
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            } else if (position == calibrateFaceRow) {
                handleCalibrateFaceClick();
            } else if (position == systemFaceUnlockRow) {
                SharedConfig.useFaceLock = !SharedConfig.useFaceLock;
                SharedConfig.saveConfig();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.useFaceLock);
                }
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                if (SharedConfig.useFaceLock && ShieldGramConfig.customBiometricHud && !ShieldGramConfig.hasEnrolledFaceSignature()) {
                    ShieldBiometricHelper.launchFaceCalibrationOverlay(getParentActivity(), () -> {
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                    });
                }
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
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode, false);
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

    private void applyBuiltInThemeNow(int presetConst, String themeKey) {
        ShieldGramConfig.setThemePreset(presetConst);
        Theme.ThemeInfo themeInfo = Theme.getTheme(themeKey);
        if (themeInfo != null) {
            Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
            Theme.saveAutoNightThemeConfig();
            NotificationCenter.getGlobalInstance().postNotificationName(
                    NotificationCenter.needSetDayNightTheme, themeInfo, false, null, -1);
        }
        notifyGlobalUiChanged();
    }

    private void showThemePresetSelector() {
        if (getParentActivity() == null) {
            return;
        }
        CharSequence[] items = new CharSequence[]{
                getString(R.string.ShieldThemeCyberSapphire),
                getString(R.string.ShieldThemeRoyalGold),
                getString(R.string.ShieldThemeObsidian),
                getString(R.string.ShieldThemeRoyalIndigo),
                getString(R.string.ShieldThemeClassicNight)
        };
        final int[] presets = new int[]{
                ShieldGramConfig.THEME_CYBER_SAPPHIRE,
                ShieldGramConfig.THEME_ROYAL_GOLD,
                ShieldGramConfig.THEME_SHIELD_OBSIDIAN,
                ShieldGramConfig.THEME_ROYAL_INDIGO,
                ShieldGramConfig.THEME_CLASSIC_NIGHT
        };
        final String[] keys = new String[]{
                "Cyber Sapphire",
                "Royal Gold",
                "Shield Obsidian",
                "Royal Indigo",
                "Dark Blue"
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ShieldPresetTheme));
        builder.setItems(items, (dialog, which) -> {
            if (which >= 0 && which < keys.length) {
                applyBuiltInThemeNow(presets[which], keys[which]);
            }
        });
        showDialog(builder.create());
    }

    private void showAccentPaletteSelector() {
        if (getParentActivity() == null) {
            return;
        }
        CharSequence[] items = new CharSequence[]{
                getString(R.string.ShieldAccentSapphire),
                getString(R.string.ShieldAccentGold),
                getString(R.string.ShieldAccentEmerald)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ShieldAccentPalette));
        builder.setItems(items, (d, which) -> {
            ShieldGramConfig.setAccentPalette(which);
            notifyGlobalUiChanged();
        });
        showDialog(builder.create());
    }

    private void showBubbleRadiusSelector() {
        if (getParentActivity() == null) {
            return;
        }
        final int[] radii = new int[]{12, 16, 18, 22};
        CharSequence[] items = new CharSequence[]{"12 dp", "16 dp", "18 dp (Shield)", "22 dp (Liquid)"};
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ShieldBubbleRadius));
        builder.setItems(items, (d, which) -> {
            if (which >= 0 && which < radii.length) {
                ShieldGramConfig.setBubbleCornerRadius(radii[which]);
                SharedConfig.bubbleRadius = radii[which] > 17 ? 17 : radii[which];
                SharedConfig.saveConfig();
                notifyGlobalUiChanged();
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

    private void handleCalibrateFaceClick() {
        if (getParentActivity() == null) {
            return;
        }
        if (!ShieldGramConfig.hasEnrolledFaceSignature()) {
            ShieldBiometricHelper.launchFaceCalibrationOverlay(getParentActivity(), () -> {
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            });
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ShieldCalibrateFaceRow));
        builder.setMessage(getString(R.string.ShieldFaceResetConfirm));
        builder.setPositiveButton(getString(R.string.ShieldFaceRecalibrateBtn), (d, w) -> {
            ShieldBiometricHelper.launchFaceCalibrationOverlay(getParentActivity(), () -> {
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            });
        });
        builder.setNeutralButton(getString(R.string.ShieldFaceClearBtn), (d, w) -> {
            ShieldGramConfig.clearEnrolledFaceVector();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void runLiveBiometricTest() {
        if (getParentActivity() == null) {
            return;
        }
        if (ShieldGramConfig.customBiometricHud && SharedConfig.useFaceLock
                && !ShieldGramConfig.hasEnrolledFaceSignature()
                && !ShieldBiometricHelper.hasHardwareFingerprintReady(getParentActivity())) {
            ShieldBiometricHelper.launchFaceCalibrationOverlay(getParentActivity(), () -> {
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            });
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
            int dType;
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
                case "Royal Gold":
                    return getString(R.string.ShieldThemeRoyalGold);
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

    private String getCurrentAccentLabel() {
        if (ShieldGramConfig.isRoyalGoldActive()) {
            return getString(R.string.ShieldAccentGold);
        }
        if (ShieldGramConfig.accentPalette == ShieldGramConfig.ACCENT_EMERALD) {
            return getString(R.string.ShieldAccentEmerald);
        }
        return getString(R.string.ShieldAccentSapphire);
    }

    private String getActivePasscodeModeLabel() {
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

    private String getActiveLauncherIconLabel() {
        for (LauncherIconController.LauncherIcon icon : LauncherIconController.LauncherIcon.values()) {
            if (LauncherIconController.isEnabled(icon)) {
                return getString(icon.title);
            }
        }
        return getString(R.string.ShieldIconCyberSapphire);
    }

    private String getDockModeLabel() {
        switch (ShieldGramConfig.glassDockMode) {
            case ShieldGramConfig.DOCK_MODE_COMPACT:
                return getString(R.string.ShieldDockCompact);
            case ShieldGramConfig.DOCK_MODE_HIDDEN:
                return getString(R.string.ShieldDockHidden);
            default:
                return getString(R.string.ShieldDockFull);
        }
    }

    private String getHeaderStyleLabel() {
        switch (ShieldGramConfig.headerStyle) {
            case ShieldGramConfig.HEADER_STYLE_MINIMAL:
                return getString(R.string.ShieldHeaderMinimal);
            case ShieldGramConfig.HEADER_STYLE_STEALTH_USERNAME:
                return getString(R.string.ShieldHeaderStealth);
            default:
                return getString(R.string.ShieldHeaderBadge);
        }
    }

    private String getAvatarShapeLabel() {
        switch (ShieldGramConfig.avatarShape) {
            case ShieldGramConfig.AVATAR_SHAPE_CIRCLE:
                return getString(R.string.ShieldAvatarCircle);
            case ShieldGramConfig.AVATAR_SHAPE_CYBER_CARD:
                return getString(R.string.ShieldAvatarCyberCard);
            default:
                return getString(R.string.ShieldAvatarSquircle);
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private static final int TYPE_HERO = 0;
        private static final int TYPE_HEADER = 1;
        private static final int TYPE_TEXT = 2;
        private static final int TYPE_CHECK = 3;
        private static final int TYPE_INFO = 4;
        private static final int TYPE_ICON_CAROUSEL = 5;
        private static final int TYPE_CHAT_PREVIEW = 6;

        private final Context mContext;

        ListAdapter(Context context) {
            this.mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_TEXT || type == TYPE_CHECK;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == heroRow) {
                return TYPE_HERO;
            } else if (position == iconCarouselRow) {
                return TYPE_ICON_CAROUSEL;
            } else if (position == chatPreviewRow) {
                return TYPE_CHAT_PREVIEW;
            } else if (position == appearanceHeaderRow || position == chatStudioHeaderRow
                    || position == layoutHeaderRow || position == securityHeaderRow || position == duressHeaderRow) {
                return TYPE_HEADER;
            } else if (position == appearanceInfoRow || position == layoutInfoRow || position == securityInfoRow || position == duressInfoRow) {
                return TYPE_INFO;
            } else if (position == cyberGlowRow || position == glassInputOutlineRow || position == customUiIconsRow
                    || position == quickLockHeaderRow || position == modernChatCardsRow || position == customBioHudRow
                    || position == systemFaceUnlockRow || position == instantFaceUnlockRow || position == fingerprintUnlockRow
                    || position == preventScreenshotsRow || position == duressHideSavedRow || position == duressHideArchiveRow
                    || position == duressHideSecretRow || position == duressHideMutedRow || position == duressClearSearchRow
                    || position == duressWipeSecretsRow) {
                return TYPE_CHECK;
            }
            return TYPE_TEXT;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_HERO:
                    view = new LuxuryHeroBannerView(mContext);
                    break;
                case TYPE_ICON_CAROUSEL:
                    view = new AppIconCarouselCell(mContext);
                    break;
                case TYPE_CHAT_PREVIEW:
                    view = new LiveChatStudioPreviewCell(mContext);
                    break;
                case TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_INFO:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case TYPE_TEXT:
                default:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int type = holder.getItemViewType();
            if (type == TYPE_HERO) {
                ((LuxuryHeroBannerView) holder.itemView).bind();
                return;
            }
            if (type == TYPE_ICON_CAROUSEL) {
                ((AppIconCarouselCell) holder.itemView).bind();
                return;
            }
            if (type == TYPE_CHAT_PREVIEW) {
                ((LiveChatStudioPreviewCell) holder.itemView).bind();
                return;
            }
            if (type == TYPE_HEADER) {
                HeaderCell cell = (HeaderCell) holder.itemView;
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                if (position == appearanceHeaderRow) {
                    cell.setText(getString(R.string.ShieldSectionAppearance));
                } else if (position == chatStudioHeaderRow) {
                    cell.setText(getString(R.string.ShieldChatStudioHeader));
                } else if (position == layoutHeaderRow) {
                    cell.setText(getString(R.string.ShieldSectionLayout));
                } else if (position == securityHeaderRow) {
                    cell.setText(getString(R.string.ShieldSectionSecurity));
                } else if (position == duressHeaderRow) {
                    cell.setText(getString(R.string.ShieldSectionDuress));
                }
            } else if (type == TYPE_TEXT) {
                TextCell cell = (TextCell) holder.itemView;
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                if (position == themePresetRow) {
                    cell.setTextAndValueAndIcon(getString(R.string.ShieldPresetTheme), getCurrentThemeLabel(), R.drawable.ic_sg_palette, true);
                } else if (position == accentPaletteRow) {
                    cell.setTextAndValueAndIcon(getString(R.string.ShieldAccentPalette), getCurrentAccentLabel(), R.drawable.ic_sg_palette, true);
                } else if (position == bubbleRadiusRow) {
                    cell.setTextAndValue(getString(R.string.ShieldBubbleRadius), ShieldGramConfig.bubbleCornerRadius + " dp", true);
                } else if (position == launcherIconRow) {
                    cell.setTextAndValueAndIcon(getString(R.string.ShieldAppIconSelector), getActiveLauncherIconLabel(), R.drawable.ic_sg_shield, true);
                } else if (position == dockModeRow) {
                    cell.setTextAndValueAndIcon(getString(R.string.ShieldDockMode), getDockModeLabel(), R.drawable.ic_sg_dock, true);
                } else if (position == headerStyleRow) {
                    cell.setTextAndValue(getString(R.string.ShieldHeaderStyle), getHeaderStyleLabel(), true);
                } else if (position == avatarShapeRow) {
                    cell.setTextAndValue(getString(R.string.ShieldAvatarShape), getAvatarShapeLabel(), true);
                } else if (position == folderTabsStyleRow) {
                    String tabVal = ShieldGramConfig.folderTabStyle == ShieldGramConfig.FOLDER_TAB_UNDERLINE
                            ? getString(R.string.ShieldFolderUnderline) : getString(R.string.ShieldFolderPills);
                    cell.setTextAndValue(getString(R.string.ShieldFolderTabStyle), tabVal, false);
                } else if (position == managePasscodeRow) {
                    cell.setTextAndValueAndIcon(getString(R.string.ShieldManagePasscode), getActivePasscodeModeLabel(), R.drawable.ic_sg_pattern, true);
                } else if (position == calibrateFaceRow) {
                    String faceStatus = ShieldGramConfig.hasEnrolledFaceSignature()
                            ? getString(R.string.ShieldFaceCalibratedValue)
                            : getString(R.string.ShieldFaceNotCalibratedValue);
                    cell.setTextAndValueAndIcon(getString(R.string.ShieldCalibrateFaceRow), faceStatus, R.drawable.ic_sg_face_id, true);
                } else if (position == testBiometricsRow) {
                    cell.setTextAndIcon(getString(R.string.ShieldTestBiometricNow), R.drawable.ic_sg_face_id, true);
                } else if (position == duressSetupRow) {
                    String val = ShieldGramConfig.hasDuressPasscode()
                            ? getString(R.string.ShieldDuressConfigured)
                            : getString(R.string.ShieldDuressNotSet);
                    cell.setTextAndValueAndIcon(getString(R.string.ShieldDuressSetup), val, R.drawable.ic_sg_panic, ShieldGramConfig.hasDuressPasscode());
                }
            } else if (type == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                if (position == cyberGlowRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldCyberGlow), ShieldGramConfig.cyberGlowAccents, false);
                } else if (position == glassInputOutlineRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldGlassInputOutline), ShieldGramConfig.chatGlassInputBar, false);
                } else if (position == customUiIconsRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldCustomUiIcons), ShieldGramConfig.useShieldCustomIcons, true);
                } else if (position == quickLockHeaderRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldQuickLockButton), ShieldGramConfig.showQuickLockInHeader, true);
                } else if (position == modernChatCardsRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldModernChatCards), ShieldGramConfig.modernChatCards, true);
                } else if (position == customBioHudRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldCustomBioHudToggle), ShieldGramConfig.customBiometricHud, true);
                } else if (position == systemFaceUnlockRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldSystemFaceUnlock), SharedConfig.useFaceLock, true);
                } else if (position == instantFaceUnlockRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldInstantFaceUnlock), ShieldGramConfig.instantFaceUnlock, true);
                } else if (position == fingerprintUnlockRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldFingerprintUnlock), SharedConfig.useFingerprintLock, true);
                } else if (position == preventScreenshotsRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldPreventScreenshots), !SharedConfig.allowScreenCapture, false);
                } else if (position == duressHideSavedRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldDuressHideSaved), ShieldGramConfig.duressHideSavedMessages, true);
                } else if (position == duressHideArchiveRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldDuressHideArchive), ShieldGramConfig.duressHideArchive, true);
                } else if (position == duressHideSecretRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldDuressHideSecret), ShieldGramConfig.duressHideSecretChats, true);
                } else if (position == duressHideMutedRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldDuressHideMuted), ShieldGramConfig.duressHideArchivedAndMuted, true);
                } else if (position == duressClearSearchRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldDuressClearSearch), ShieldGramConfig.duressClearRecentSearch, true);
                } else if (position == duressWipeSecretsRow) {
                    cell.setTextAndCheck(getString(R.string.ShieldDuressWipeSecrets), ShieldGramConfig.duressTerminateSecretChats, false);
                }
            } else if (type == TYPE_INFO) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                if (position == appearanceInfoRow) {
                    cell.setText(getString(R.string.ShieldAppearanceHint));
                } else if (position == layoutInfoRow) {
                    cell.setText(getString(R.string.ShieldLayoutHint));
                } else if (position == securityInfoRow) {
                    String diag = ShieldBiometricHelper.getDiagnosticSummary(mContext);
                    cell.setText(diag + "\n\n" + getString(R.string.ShieldSecurityBioHint));
                } else if (position == duressInfoRow) {
                    cell.setText(getString(R.string.ShieldDuressHint));
                }
            }
        }
    }

    /**
     * Luxury Hero Showcase with 3D ShieldGram Crest + Instant 1-Tap Sapphire / Royal Gold Switcher.
     */
    private final class LuxuryHeroBannerView extends FrameLayout {

        private final ImageView crestImageView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView lockStatusChip;
        private final TextView bioStatusChip;
        private final TextView sapphireQuickBtn;
        private final TextView goldQuickBtn;
        private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();

        LuxuryHeroBannerView(Context context) {
            super(context);
            setWillNotDraw(false);
            setPadding(dp(12), dp(10), dp(12), dp(8));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setPadding(dp(18), dp(20), dp(18), dp(18));

            crestImageView = new ImageView(context);
            crestImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            col.addView(crestImageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

            titleView = new TextView(context);
            titleView.setText("ShieldGram Studio");
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 21f);
            titleView.setTextColor(0xFFFAF8F5);
            titleView.setGravity(Gravity.CENTER);
            col.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            subtitleView.setGravity(Gravity.CENTER);
            col.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 0, 8, 12));

            // Status chips row
            LinearLayout chipsRow = new LinearLayout(context);
            chipsRow.setOrientation(LinearLayout.HORIZONTAL);
            chipsRow.setGravity(Gravity.CENTER);

            lockStatusChip = createStatusChip(context);
            bioStatusChip = createStatusChip(context);
            chipsRow.addView(lockStatusChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));
            chipsRow.addView(bioStatusChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            col.addView(chipsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

            // 1-Tap Luxury Theme Switcher Pills (Cyber Sapphire vs Imperial Royal Gold)
            LinearLayout quickRow = new LinearLayout(context);
            quickRow.setOrientation(LinearLayout.HORIZONTAL);
            quickRow.setGravity(Gravity.CENTER);

            sapphireQuickBtn = createQuickThemePill(context, "✦ " + getString(R.string.ShieldQuickSwitchSapphire), false);
            sapphireQuickBtn.setOnClickListener(v -> {
                try { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); } catch (Throwable ignore) {}
                applyBuiltInThemeNow(ShieldGramConfig.THEME_CYBER_SAPPHIRE, "Cyber Sapphire");
            });

            goldQuickBtn = createQuickThemePill(context, "♛ " + getString(R.string.ShieldQuickSwitchGold), true);
            goldQuickBtn.setOnClickListener(v -> {
                try { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); } catch (Throwable ignore) {}
                applyBuiltInThemeNow(ShieldGramConfig.THEME_ROYAL_GOLD, "Royal Gold");
            });

            quickRow.addView(sapphireQuickBtn, new LinearLayout.LayoutParams(0, dp(38), 1f));
            View gap = new View(context);
            quickRow.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
            quickRow.addView(goldQuickBtn, new LinearLayout.LayoutParams(0, dp(38), 1f));

            col.addView(quickRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        private TextView createStatusChip(Context context) {
            TextView tv = new TextView(context);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f);
            tv.setTypeface(AndroidUtilities.bold());
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setPadding(dp(10), dp(4), dp(10), dp(4));
            return tv;
        }

        private TextView createQuickThemePill(Context context, String text, boolean goldStyle) {
            TextView tv = new TextView(context);
            tv.setText(text);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
            tv.setTypeface(AndroidUtilities.bold());
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            return tv;
        }

        void bind() {
            boolean gold = ShieldGramConfig.isRoyalGoldActive();
            crestImageView.setImageResource(ShieldGramConfig.getBrandCrestResId());
            subtitleView.setText(getString(R.string.ShieldGramHeroTagline));
            subtitleView.setTextColor(gold ? 0xFFD9C69E : 0xFFA5B8E0);

            boolean armed = !SharedConfig.passcodeHash.isEmpty();
            GradientDrawable c1 = new GradientDrawable();
            c1.setCornerRadius(dp(10));
            c1.setColor(armed ? 0x2834D399 : 0x2494A3B8);
            c1.setStroke(dp(1), armed ? 0x7034D399 : 0x4594A3B8);
            lockStatusChip.setBackground(c1);
            lockStatusChip.setTextColor(armed ? 0xFF6EE7B7 : 0xFFCBD5E1);
            lockStatusChip.setText(armed ? (getString(R.string.ShieldGramHeroStatusArmed) + " • " + getActivePasscodeModeLabel())
                    : getString(R.string.ShieldGramHeroStatusUnarmed));

            int sig = ShieldGramConfig.getSignatureAccentColor(true);
            GradientDrawable c2 = new GradientDrawable();
            c2.setCornerRadius(dp(10));
            c2.setColor(ColorUtils.setAlphaComponent(sig, 36));
            c2.setStroke(dp(1), ColorUtils.setAlphaComponent(sig, 115));
            bioStatusChip.setBackground(c2);
            bioStatusChip.setTextColor(gold ? 0xFFF7D774 : 0xFF93C5FD);
            bioStatusChip.setText(ShieldGramConfig.customBiometricHud ? "HUD ACTIVE" : "ANDROID BIO");

            // Highlight active quick button
            GradientDrawable sapBg = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    !gold ? new int[]{0xFF1E4FBF, 0xFF153588} : new int[]{0x221E3A8A, 0x180F172A}
            );
            sapBg.setCornerRadius(dp(12));
            sapBg.setStroke(dp(1), !gold ? 0xFF60A5FA : 0x4460A5FA);
            sapphireQuickBtn.setBackground(sapBg);
            sapphireQuickBtn.setTextColor(!gold ? 0xFFFFFFFF : 0xFF93C5FD);

            GradientDrawable gldBg = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    gold ? new int[]{0xFF6B4E14, 0xFF3E2C0A} : new int[]{0x2442300D, 0x181C1406}
            );
            gldBg.setCornerRadius(dp(12));
            gldBg.setStroke(dp(1), gold ? 0xFFF5D061 : 0x55D4AF37);
            goldQuickBtn.setBackground(gldBg);
            goldQuickBtn.setTextColor(gold ? 0xFFFFF4D2 : 0xFFE5C158);

            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            cardRect.set(dp(12), dp(8), getMeasuredWidth() - dp(12), getMeasuredHeight() - dp(6));
            boolean gold = ShieldGramConfig.isRoyalGoldActive();
            int[] gradColors = gold
                    ? new int[]{0xFF1D1609, 0xFF120E06, 0xFF0A0804}
                    : new int[]{0xFF0E1B38, 0xFF091226, 0xFF060B17};
            cardPaint.setShader(new LinearGradient(
                    cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                    gradColors, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP
            ));
            canvas.drawRoundRect(cardRect, dp(20), dp(20), cardPaint);
            cardPaint.setShader(null);

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dpf2(1.15f));
            borderPaint.setColor(gold ? 0x88E5B842 : 0x773B82F6);
            canvas.drawRoundRect(cardRect, dp(20), dp(20), borderPaint);
            super.onDraw(canvas);
        }
    }

    /**
     * Interactive 3D App Icon Carousel right inside ShieldGram Studio — 1 tap updates APK launcher icon!
     */
    private final class AppIconCarouselCell extends LinearLayout {

        private final LinearLayout stripLayout;
        private final LauncherIconController.LauncherIcon[] icons = new LauncherIconController.LauncherIcon[]{
                LauncherIconController.LauncherIcon.DEFAULT,
                LauncherIconController.LauncherIcon.PREMIUM,
                LauncherIconController.LauncherIcon.NOX,
                LauncherIconController.LauncherIcon.AQUA,
                LauncherIconController.LauncherIcon.TURBO,
                LauncherIconController.LauncherIcon.VINTAGE
        };
        private final int[] crestResIds = new int[]{
                R.drawable.sg_crest_sapphire,
                R.drawable.sg_crest_gold,
                R.drawable.sg_crest_obsidian,
                R.drawable.sg_crest_aqua,
                R.drawable.sg_crest_emerald,
                R.drawable.sg_crest_amethyst
        };

        AppIconCarouselCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(0, dp(12), 0, dp(14));

            TextView title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            title.setTypeface(AndroidUtilities.bold());
            title.setPadding(dp(20), 0, dp(20), dp(10));
            title.setText(getString(R.string.ShieldIconCarouselTitle));
            addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            setTag(title);

            HorizontalScrollView hsv = new HorizontalScrollView(context);
            hsv.setHorizontalScrollBarEnabled(false);
            hsv.setClipToPadding(false);
            hsv.setPadding(dp(14), 0, dp(14), 0);

            stripLayout = new LinearLayout(context);
            stripLayout.setOrientation(HORIZONTAL);
            hsv.addView(stripLayout, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            addView(hsv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        void bind() {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            if (getTag() instanceof TextView) {
                ((TextView) getTag()).setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            }
            stripLayout.removeAllViews();
            final int accent = ShieldGramConfig.getSignatureAccentColor(true);

            for (int i = 0; i < icons.length; i++) {
                final LauncherIconController.LauncherIcon ic = icons[i];
                final boolean selected = LauncherIconController.isEnabled(ic);

                LinearLayout tile = new LinearLayout(getContext());
                tile.setOrientation(VERTICAL);
                tile.setGravity(Gravity.CENTER_HORIZONTAL);
                tile.setPadding(dp(10), dp(10), dp(10), dp(8));

                GradientDrawable tileBg = new GradientDrawable();
                tileBg.setCornerRadius(dp(16));
                tileBg.setColor(selected ? ColorUtils.setAlphaComponent(accent, 32) : 0x1294A3B8);
                tileBg.setStroke(dp(selected ? 1.6f : 0.8f), selected ? accent : 0x2694A3B8);
                tile.setBackground(tileBg);

                ImageView iv = new ImageView(getContext());
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageResource(crestResIds[i]);
                tile.addView(iv, LayoutHelper.createLinear(56, 56, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

                TextView label = new TextView(getContext());
                label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f);
                label.setTypeface(selected ? AndroidUtilities.bold() : null);
                label.setSingleLine(true);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setGravity(Gravity.CENTER);
                label.setTextColor(selected ? accent : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                label.setText((selected ? "✓ " : "") + getString(ic.title));
                tile.addView(label, LayoutHelper.createLinear(82, LayoutHelper.WRAP_CONTENT));

                tile.setOnClickListener(v -> {
                    try { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); } catch (Throwable ignore) {}
                    LauncherIconController.setIcon(ic);
                    BulletinFactory.of(ShieldGramSettingsActivity.this)
                            .createSimpleBulletin(crestResIds[0], getString(R.string.ShieldIconAppliedToast) + ": " + getString(ic.title))
                            .show();
                    bind();
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(launcherIconRow);
                    }
                });

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
                lp.rightMargin = dp(8);
                stripLayout.addView(tile, lp);
            }
        }
    }

    /**
     * Live Interactive Chat Screen Preview inside ShieldGram Studio showing real-time bubble radius,
     * jewel rim, avatar shape, and Royal Gold / Cyber Sapphire glass input island.
     */
    private static final class LiveChatStudioPreviewCell extends View {

        private final Paint cardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();
        private final RectF rectTmp = new RectF();

        LiveChatStudioPreviewCell(Context context) {
            super(context);
            rimPaint.setStyle(Paint.Style.STROKE);
            textPaint.setTextSize(dpf2(13.5f));
            subTextPaint.setTextSize(dpf2(11.5f));
        }

        void bind() {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(164));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            boolean gold = ShieldGramConfig.isRoyalGoldActive();
            int accent = ShieldGramConfig.getSignatureAccentColor(true);
            float bubbleRad = dp(ShieldGramConfig.bubbleCornerRadius);

            cardRect.set(dp(16), dp(8), getWidth() - dp(16), getHeight() - dp(10));
            cardBgPaint.setShader(new LinearGradient(
                    cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                    gold ? new int[]{0xFF141008, 0xFF0B0905} : new int[]{0xFF0B1324, 0xFF070C18},
                    null, Shader.TileMode.CLAMP
            ));
            canvas.drawRoundRect(cardRect, dp(18), dp(18), cardBgPaint);
            cardBgPaint.setShader(null);

            rimPaint.setStrokeWidth(dpf2(0.9f));
            rimPaint.setColor(ColorUtils.setAlphaComponent(accent, 75));
            canvas.drawRoundRect(cardRect, dp(18), dp(18), rimPaint);

            // 1. Incoming avatar + incoming bubble
            float avSize = dp(32);
            float avLeft = cardRect.left + dp(12);
            float avTop = cardRect.top + dp(14);
            float avRad = ShieldGramConfig.getAvatarRoundRadius((int) (avSize / 2f));
            rectTmp.set(avLeft, avTop, avLeft + avSize, avTop + avSize);
            bubblePaint.setColor(gold ? 0xFF3A2D12 : 0xFF1D3566);
            canvas.drawRoundRect(rectTmp, avRad, avRad, bubblePaint);
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setColor(accent);
            canvas.drawText("SG", rectTmp.centerX() - textPaint.measureText("SG") / 2f, rectTmp.centerY() + dp(4.5f), textPaint);

            String inMsg = getString(R.string.ShieldPreviewInMsg);
            textPaint.setTypeface(null);
            float inW = Math.min(cardRect.width() - dp(72), textPaint.measureText(inMsg) + dp(24));
            rectTmp.set(avLeft + avSize + dp(8), avTop, avLeft + avSize + dp(8) + inW, avTop + dp(34));
            bubblePaint.setColor(gold ? 0xFF1C170E : 0xFF151E32);
            canvas.drawRoundRect(rectTmp, bubbleRad, bubbleRad, bubblePaint);
            textPaint.setColor(0xFFFAF6EE);
            CharSequence clippedIn = TextUtils.ellipsize(inMsg, new android.text.TextPaint(textPaint), inW - dp(20), TextUtils.TruncateAt.END);
            canvas.drawText(clippedIn, 0, clippedIn.length(), rectTmp.left + dp(11), rectTmp.centerY() + dp(4.5f), textPaint);

            // 2. Outgoing luxury bubble with specular rim
            String outMsg = getString(R.string.ShieldPreviewOutMsg);
            float outW = Math.min(cardRect.width() - dp(48), textPaint.measureText(outMsg) + dp(28));
            float outTop = avTop + dp(44);
            rectTmp.set(cardRect.right - dp(12) - outW, outTop, cardRect.right - dp(12), outTop + dp(34));
            bubblePaint.setShader(new LinearGradient(
                    rectTmp.left, rectTmp.top, rectTmp.right, rectTmp.bottom,
                    gold ? new int[]{0xFF4B3712, 0xFF2E210A} : new int[]{0xFF214694, 0xFF152C62},
                    null, Shader.TileMode.CLAMP
            ));
            canvas.drawRoundRect(rectTmp, bubbleRad, bubbleRad, bubblePaint);
            bubblePaint.setShader(null);

            if (ShieldGramConfig.cyberGlowAccents) {
                rimPaint.setStrokeWidth(dpf2(0.95f));
                rimPaint.setColor(ColorUtils.setAlphaComponent(accent, 145));
                canvas.drawRoundRect(rectTmp, bubbleRad, bubbleRad, rimPaint);
            }
            textPaint.setColor(0xFFFFFFFF);
            CharSequence clippedOut = TextUtils.ellipsize(outMsg, new android.text.TextPaint(textPaint), outW - dp(20), TextUtils.TruncateAt.END);
            canvas.drawText(clippedOut, 0, clippedOut.length(), rectTmp.left + dp(12), rectTmp.centerY() + dp(4.5f), textPaint);

            // 3. Bottom Glass Input Island
            float islTop = cardRect.bottom - dp(38);
            rectTmp.set(cardRect.left + dp(12), islTop, cardRect.right - dp(12), cardRect.bottom - dp(8));
            bubblePaint.setColor(gold ? 0xFF1A150C : 0xFF121A2B);
            canvas.drawRoundRect(rectTmp, dp(15), dp(15), bubblePaint);
            if (ShieldGramConfig.chatGlassInputBar) {
                rimPaint.setStrokeWidth(dpf2(1.0f));
                rimPaint.setColor(ColorUtils.setAlphaComponent(accent, 128));
                canvas.drawRoundRect(rectTmp, dp(15), dp(15), rimPaint);
            }
            subTextPaint.setColor(gold ? 0xFFA8997B : 0xFF7F91B5);
            canvas.drawText(getString(R.string.ShieldPreviewInputHint), rectTmp.left + dp(14), rectTmp.centerY() + dp(4f), subTextPaint);

            bubblePaint.setColor(accent);
            canvas.drawCircle(rectTmp.right - dp(15), rectTmp.centerY(), dp(9.5f), bubblePaint);
        }
    }
}
