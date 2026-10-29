package org.telegram.shieldgram;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.AppIconsSelectorCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.PasscodeActivity;
import org.telegram.ui.SettingsActivity;

import java.util.ArrayList;

public class ShieldGramSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_LIQUID_GLASS = 10;
    private static final int ID_HEADER_STYLE = 20;
    private static final int ID_QUICK_LOCK = 21;
    private static final int ID_PASSCODE = 30;
    private static final int ID_DURESS_SETUP = 31;
    private static final int ID_DURESS_HIDE_SAVED = 32;
    private static final int ID_DURESS_HIDE_ARCHIVE = 33;
    private static final int ID_DURESS_HIDE_SECRET = 34;
    private static final int ID_DURESS_CLEAR_SEARCH = 35;

    private FrameLayout contentView;
    private UniversalRecyclerView listView;
    private CleanHeroHeaderView heroHeaderView;
    private ThemeSpheresRowView themeSpheresRowView;
    private AppIconsSelectorCell appIconsSelectorCell;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        ShieldGramConfig.ensureLoaded();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetPasscode);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetPasscode);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetPasscode || id == NotificationCenter.didSetNewTheme) {
            refreshUiColorsAndItems(true);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
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

        contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = contentView;

        heroHeaderView = new CleanHeroHeaderView(context);
        themeSpheresRowView = new ThemeSpheresRowView(context);
        appIconsSelectorCell = new AppIconsSelectorCell(context, this, currentAccount);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        actionBar.setAdaptiveBackground(listView);
        listView.setVerticalScrollBarEnabled(false);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.adapter.update(false);
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUiColorsAndItems(false);
    }

    private void refreshUiColorsAndItems(boolean animated) {
        if (contentView != null) {
            contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        }
        if (heroHeaderView != null) {
            heroHeaderView.updateAppearance();
        }
        if (themeSpheresRowView != null) {
            themeSpheresRowView.updateSelection();
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(animated);
            listView.invalidateViews();
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustomShadow(heroHeaderView, LayoutHelper.WRAP_CONTENT).setId(1));

        // 1. Appearance & Liquid Glass
        items.add(UItem.asHeader(getString(R.string.ShieldCleanAppearance)));
        items.add(UItem.asCustom(themeSpheresRowView, LayoutHelper.WRAP_CONTENT).setId(2));
        if (Build.VERSION.SDK_INT >= 33) {
            items.add(UItem.asCheck(ID_LIQUID_GLASS, getString(R.string.ShieldLiquidGlassToggle))
                    .setChecked(LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS)));
        }
        items.add(UItem.asShadow(Build.VERSION.SDK_INT >= 33 ? getString(R.string.ShieldLiquidGlassHint) : null));

        // 2. Chat Bubble Rounding
        items.add(UItem.asHeader(getString(R.string.ShieldBubbleRadius)));
        items.add(UItem.asIntSlideView(
                1,
                10,
                ShieldGramConfig.bubbleCornerRadius,
                22,
                val -> val + " dp",
                val -> ShieldGramConfig.setBubbleCornerRadius(val)
        ));
        items.add(UItem.asShadow(null));

        // 3. Native 3D App Icon Carousel
        items.add(UItem.asHeader(getString(R.string.ShieldCleanAppIcon)));
        items.add(UItem.asCustom(appIconsSelectorCell, LayoutHelper.WRAP_CONTENT).setId(3));
        items.add(UItem.asShadow(getString(R.string.ShieldCleanAppIconHint)));

        // 4. Navigation & Header
        items.add(UItem.asHeader(getString(R.string.ShieldCleanNavigation)));
        items.add(UItem.asSlideView(
                new String[]{
                        getString(R.string.ShieldDockShortFull),
                        getString(R.string.ShieldDockShortCompact),
                        getString(R.string.ShieldDockShortHidden)
                },
                ShieldGramConfig.glassDockMode,
                chosen -> ShieldGramConfig.setGlassDockMode(chosen)
        ));
        items.add(UItem.asSettingsCell(ID_HEADER_STYLE, getString(R.string.ShieldHeaderStyle), getHeaderStyleLabel()));
        items.add(UItem.asCheck(ID_QUICK_LOCK, getString(R.string.ShieldQuickLockButton))
                .setChecked(ShieldGramConfig.showQuickLockInHeader));
        items.add(UItem.asShadow(null));

        // 5. Security & Android Biometrics
        items.add(UItem.asHeader(getString(R.string.ShieldCleanSecurity)));
        final boolean gold = ShieldGramConfig.isRoyalGoldActive();
        final boolean hasPasscode = !SharedConfig.passcodeHash.isEmpty();
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_PASSCODE,
                gold ? 0xFFF2C955 : IconBackgroundColors.BLUE.top,
                gold ? 0xFFC48B1E : IconBackgroundColors.BLUE.bottom,
                R.drawable.settings_privacy,
                getString(R.string.Passcode),
                getPasscodeSummarySubtitle(),
                getString(hasPasscode ? R.string.PasswordOn : R.string.PasswordOff)
        ));

        final boolean duressConfigured = ShieldGramConfig.hasDuressPasscode() && !ShieldGramConfig.duressSessionActive;
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_DURESS_SETUP,
                IconBackgroundColors.ORANGE_DEEP.top,
                IconBackgroundColors.ORANGE_DEEP.bottom,
                R.drawable.ic_sg_panic,
                getString(R.string.ShieldDuressSetup),
                getString(R.string.ShieldCleanDuressSub),
                getString(duressConfigured ? R.string.ShieldDuressConfigured : R.string.ShieldDuressNotSet)
        ));
        if (duressConfigured) {
            items.add(UItem.asCheck(ID_DURESS_HIDE_SAVED, getString(R.string.ShieldDuressHideSaved))
                    .setChecked(ShieldGramConfig.duressHideSavedMessages));
            items.add(UItem.asCheck(ID_DURESS_HIDE_ARCHIVE, getString(R.string.ShieldDuressHideArchive))
                    .setChecked(ShieldGramConfig.duressHideArchive));
            items.add(UItem.asCheck(ID_DURESS_HIDE_SECRET, getString(R.string.ShieldDuressHideSecret))
                    .setChecked(ShieldGramConfig.duressHideSecretChats));
            items.add(UItem.asCheck(ID_DURESS_CLEAR_SEARCH, getString(R.string.ShieldDuressClearSearch))
                    .setChecked(ShieldGramConfig.duressClearRecentSearch));
        }
        items.add(UItem.asShadow(getString(R.string.ShieldDuressHint)));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item == null) {
            return;
        }
        switch (item.id) {
            case ID_LIQUID_GLASS: {
                boolean next = !LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS);
                LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, next);
                if (next) {
                    LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, true);
                }
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(next);
                }
                if (heroHeaderView != null) {
                    heroHeaderView.updateAppearance();
                }
                ShieldGramConfig.notifyUiChanged();
                break;
            }
            case ID_HEADER_STYLE:
                showHeaderStyleDialog();
                break;
            case ID_QUICK_LOCK: {
                boolean next = !ShieldGramConfig.showQuickLockInHeader;
                ShieldGramConfig.setShowQuickLockInHeader(next);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(next);
                }
                break;
            }
            case ID_PASSCODE:
                presentFragment(new PasscodeActivity(PasscodeActivity.determineOpenFragment()));
                break;
            case ID_DURESS_SETUP:
                showDuressSetupDialog();
                break;
            case ID_DURESS_HIDE_SAVED:
                ShieldGramConfig.duressHideSavedMessages = !ShieldGramConfig.duressHideSavedMessages;
                ShieldGramConfig.save();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.duressHideSavedMessages);
                }
                break;
            case ID_DURESS_HIDE_ARCHIVE:
                ShieldGramConfig.duressHideArchive = !ShieldGramConfig.duressHideArchive;
                ShieldGramConfig.save();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.duressHideArchive);
                }
                break;
            case ID_DURESS_HIDE_SECRET:
                ShieldGramConfig.duressHideSecretChats = !ShieldGramConfig.duressHideSecretChats;
                ShieldGramConfig.save();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.duressHideSecretChats);
                }
                break;
            case ID_DURESS_CLEAR_SEARCH:
                ShieldGramConfig.duressClearRecentSearch = !ShieldGramConfig.duressClearRecentSearch;
                ShieldGramConfig.save();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(ShieldGramConfig.duressClearRecentSearch);
                }
                break;
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private String getHeaderStyleLabel() {
        switch (ShieldGramConfig.headerStyle) {
            case ShieldGramConfig.HEADER_STYLE_MINIMAL:
                return getString(R.string.ShieldHeaderMinimal);
            case ShieldGramConfig.HEADER_STYLE_STEALTH_USERNAME:
                return getString(R.string.ShieldHeaderStealth);
            case ShieldGramConfig.HEADER_STYLE_SHIELD_BADGE:
            default:
                return "ShieldGram";
        }
    }

    private String getPasscodeSummarySubtitle() {
        if (SharedConfig.passcodeHash.isEmpty()) {
            return getString(R.string.ShieldCleanPasscodeSub);
        }
        String mode;
        switch (SharedConfig.passcodeType) {
            case SharedConfig.PASSCODE_TYPE_PIN6:
                mode = getString(R.string.ShieldPasscodePIN6);
                break;
            case SharedConfig.PASSCODE_TYPE_PATTERN:
                mode = getString(R.string.ShieldPasscodePattern);
                break;
            case SharedConfig.PASSCODE_TYPE_PASSWORD:
                mode = getString(R.string.PasscodeSwitchToPassword);
                break;
            case SharedConfig.PASSCODE_TYPE_PIN:
            default:
                mode = getString(R.string.ShieldPasscodePIN4);
                break;
        }
        if (SharedConfig.useFaceLock || SharedConfig.useFingerprintLock) {
            return mode + " • Biometrics";
        }
        return mode;
    }

    private void showHeaderStyleDialog() {
        if (getParentActivity() == null) {
            return;
        }
        CharSequence[] opts = new CharSequence[]{
                "ShieldGram",
                getString(R.string.ShieldHeaderMinimal),
                getString(R.string.ShieldHeaderStealth)
        };
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.ShieldHeaderStyle));
        b.setItems(opts, (d, which) -> {
            ShieldGramConfig.setHeaderStyle(which);
            refreshUiColorsAndItems(true);
        });
        showDialog(b.create());
    }

    private void showDuressSetupDialog() {
        if (getParentActivity() == null) {
            return;
        }
        if (SharedConfig.passcodeHash.isEmpty()) {
            BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.error,
                    getString(R.string.ShieldDuressErrorNeedMain)
            ).show();
            return;
        }

        Context ctx = getParentActivity();
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(8), dp(24), dp(4));

        TextView desc = new TextView(ctx);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        desc.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        desc.setText(getString(R.string.ShieldDuressSetDialogMessage));
        container.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        final EditTextBoldCursor input = new EditTextBoldCursor(ctx);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setHint(getString(R.string.ShieldDuressInputHint));
        input.setSingleLine(true);
        input.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setCursorSize(dp(20));
        input.setCursorWidth(1.5f);
        input.setBackground(Theme.createEditTextDrawable(ctx, true));
        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN
                || SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN6) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        container.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42));

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(getString(R.string.ShieldDuressSetup));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            String raw = input.getText() != null ? input.getText().toString().trim() : "";
            if (raw.length() < 4) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.ShieldDuressErrorTooShort)).show();
                return;
            }
            if (SharedConfig.checkPasscode(raw)) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.ShieldDuressErrorSameAsMain)).show();
                return;
            }
            int dType = TextUtils.isDigitsOnly(raw)
                    ? (raw.length() == 6 ? SharedConfig.PASSCODE_TYPE_PIN6 : SharedConfig.PASSCODE_TYPE_PIN)
                    : SharedConfig.PASSCODE_TYPE_PASSWORD;
            ShieldGramConfig.setDuressPasscode(raw, dType);
            refreshUiColorsAndItems(true);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.ShieldDuressSavedToast)).show();
        });
        if (ShieldGramConfig.hasDuressPasscode() && !ShieldGramConfig.duressSessionActive) {
            builder.setNeutralButton(getString(R.string.ShieldDuressDisableAction), (dialog, which) -> {
                ShieldGramConfig.clearDuressPasscode();
                refreshUiColorsAndItems(true);
            });
        }
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        });
        showDialog(dialog);
    }

    private void applyBuiltInThemeNow(int presetId, String themeName) {
        try {
            Theme.ThemeInfo target = Theme.getTheme(themeName);
            if (target == null) {
                target = Theme.getTheme("Cyber Sapphire");
            }
            if (target == null) {
                return;
            }
            ShieldGramConfig.setThemePreset(presetId);
            SharedPreferences.Editor editor = ApplicationLoader.applicationContext
                    .getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit();
            if (target.isDark()) {
                editor.putString("lastDarkTheme", target.getKey());
            } else {
                editor.putString("lastDayTheme", target.getKey());
            }
            editor.apply();

            Theme.applyTheme(target, !target.isDark());
            if (getParentLayout() != null) {
                getParentLayout().rebuildAllFragmentViews(false, false);
            }
            refreshUiColorsAndItems(true);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /**
     * Clean, borderless top header matching Telegram 13.1.1's native SettingsActivity aesthetic.
     */
    private class CleanHeroHeaderView extends LinearLayout {

        private final ImageView crestView;
        private final TextView titleView;
        private final TextView subtitleView;

        CleanHeroHeaderView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(dp(24), dp(14), dp(24), dp(16));

            crestView = new ImageView(context);
            crestView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ScaleStateListAnimator.apply(crestView);
            crestView.setOnClickListener(v -> {
                try {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                } catch (Throwable ignore) {
                }
                if (ShieldGramConfig.isRoyalGoldActive()) {
                    applyBuiltInThemeNow(ShieldGramConfig.THEME_CYBER_SAPPHIRE, "Cyber Sapphire");
                } else {
                    applyBuiltInThemeNow(ShieldGramConfig.THEME_ROYAL_GOLD, "Royal Gold");
                }
            });
            addView(crestView, LayoutHelper.createLinear(84, 84, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

            titleView = new TextView(context);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            titleView.setGravity(Gravity.CENTER);
            titleView.setText("ShieldGram");
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setGravity(Gravity.CENTER);
            addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

            updateAppearance();
        }

        void updateAppearance() {
            crestView.setImageResource(ShieldGramConfig.getBrandCrestResId());
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

            String themeLabel;
            switch (ShieldGramConfig.selectedThemePreset) {
                case ShieldGramConfig.THEME_ROYAL_GOLD:
                    themeLabel = getString(R.string.ShieldShortGold);
                    break;
                case ShieldGramConfig.THEME_SHIELD_OBSIDIAN:
                    themeLabel = getString(R.string.ShieldShortObsidian);
                    break;
                case ShieldGramConfig.THEME_ARCTIC_PLATINUM:
                    themeLabel = getString(R.string.ShieldShortDaylight);
                    break;
                case ShieldGramConfig.THEME_CYBER_SAPPHIRE:
                default:
                    themeLabel = getString(R.string.ShieldShortSapphire);
                    break;
            }
            boolean glassOn = Build.VERSION.SDK_INT >= 33 && LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS);
            subtitleView.setText(glassOn ? themeLabel + " • Liquid Glass" : themeLabel);
        }
    }

    /**
     * Minimal, borderless color-sphere theme selector inside the Appearance section card.
     */
    private class ThemeSpheresRowView extends LinearLayout {

        private final SphereItem[] items = new SphereItem[4];

        ThemeSpheresRowView(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            setPadding(dp(12), dp(16), dp(12), dp(14));

            items[0] = new SphereItem(context, ShieldGramConfig.THEME_CYBER_SAPPHIRE, "Cyber Sapphire",
                    getString(R.string.ShieldShortSapphire), 0xFF60A5FA, 0xFF1D4ED8);
            items[1] = new SphereItem(context, ShieldGramConfig.THEME_ROYAL_GOLD, "Royal Gold",
                    getString(R.string.ShieldShortGold), 0xFFF7DA7E, 0xFFB88016);
            items[2] = new SphereItem(context, ShieldGramConfig.THEME_SHIELD_OBSIDIAN, "Shield Obsidian",
                    getString(R.string.ShieldShortObsidian), 0xFF334155, 0xFF090D16);
            items[3] = new SphereItem(context, ShieldGramConfig.THEME_ARCTIC_PLATINUM, "Blue",
                    getString(R.string.ShieldShortDaylight), 0xFF93C5FD, 0xFF3B82F6);

            for (int i = 0; i < items.length; i++) {
                addView(items[i], LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            }
            updateSelection();
        }

        void updateSelection() {
            for (int i = 0; i < items.length; i++) {
                items[i].updateState();
            }
        }
    }

    private class SphereItem extends LinearLayout {

        private final int presetId;
        private final String themeName;
        private final int topColor;
        private final int bottomColor;
        private final View sphereView;
        private final TextView labelView;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SphereItem(Context context, int presetId, String themeName, String label, int topColor, int bottomColor) {
            super(context);
            this.presetId = presetId;
            this.themeName = themeName;
            this.topColor = topColor;
            this.bottomColor = bottomColor;

            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            ScaleStateListAnimator.apply(this);

            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dpf2(2.0f));

            sphereView = new View(context) {
                @Override
                protected void onDraw(@NonNull Canvas canvas) {
                    float cx = getMeasuredWidth() / 2f;
                    float cy = getMeasuredHeight() / 2f;
                    boolean selected = ShieldGramConfig.selectedThemePreset == presetId;
                    float fillR = selected ? dpf2(18f) : dpf2(20f);

                    fillPaint.setShader(new LinearGradient(
                            cx - fillR, cy - fillR, cx + fillR, cy + fillR,
                            topColor, bottomColor, Shader.TileMode.CLAMP
                    ));
                    canvas.drawCircle(cx, cy, fillR, fillPaint);
                    fillPaint.setShader(null);

                    if (selected) {
                        int ringClr = presetId == ShieldGramConfig.THEME_ROYAL_GOLD
                                ? 0xFFE5B842
                                : Theme.getColor(Theme.key_featuredStickers_addButton);
                        ringPaint.setColor(ColorUtils.setAlphaComponent(ringClr, 230));
                        canvas.drawCircle(cx, cy, dpf2(22.5f), ringPaint);
                    }
                }
            };
            addView(sphereView, LayoutHelper.createLinear(50, 50, Gravity.CENTER_HORIZONTAL));

            labelView = new TextView(context);
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            labelView.setSingleLine(true);
            labelView.setEllipsize(TextUtils.TruncateAt.END);
            labelView.setGravity(Gravity.CENTER);
            labelView.setText(label);
            addView(labelView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 2, 6, 2, 0));

            setOnClickListener(v -> {
                try {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                } catch (Throwable ignore) {
                }
                applyBuiltInThemeNow(presetId, themeName);
            });
        }

        void updateState() {
            boolean selected = ShieldGramConfig.selectedThemePreset == presetId;
            labelView.setTypeface(selected ? AndroidUtilities.bold() : null);
            labelView.setTextColor(Theme.getColor(selected
                    ? Theme.key_windowBackgroundWhiteBlackText
                    : Theme.key_windowBackgroundWhiteGrayText));
            sphereView.invalidate();
        }
    }
}
