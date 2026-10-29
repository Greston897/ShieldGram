package org.telegram.shieldgram;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class ShieldHeaderLogoDrawable extends Drawable {

    private final Context context;
    private final int currentAccount;
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pillPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pillRect = new RectF();
    private Drawable cachedCrestDrawable;
    private int cachedCrestResId;
    private int tintColor = 0xFFFFFFFF;
    private int alphaValue = 255;

    public ShieldHeaderLogoDrawable(Context context, int currentAccount) {
        this.context = context;
        this.currentAccount = currentAccount;

        titlePaint.setTypeface(AndroidUtilities.bold());
        titlePaint.setTextSize(dpf2(20f));
        titlePaint.setLetterSpacing(-0.015f);

        pillPaint.setTypeface(AndroidUtilities.bold());
        pillPaint.setTextSize(dpf2(9.2f));
        pillPaint.setLetterSpacing(0.06f);

        badgeBorderPaint.setStyle(Paint.Style.STROKE);
        badgeBorderPaint.setStrokeWidth(dpf2(1f));
    }

    private Drawable resolveCrestDrawable() {
        int resId = ShieldGramConfig.getBrandCrestResId();
        if (cachedCrestDrawable == null || cachedCrestResId != resId) {
            cachedCrestResId = resId;
            Drawable d = ContextCompat.getDrawable(context, resId);
            cachedCrestDrawable = d != null ? d.mutate() : null;
        }
        return cachedCrestDrawable;
    }

    private String resolveTitleText() {
        ShieldGramConfig.ensureLoaded();
        if (ShieldGramConfig.headerStyle == ShieldGramConfig.HEADER_STYLE_STEALTH_USERNAME) {
            TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
            if (user != null) {
                String un = UserObject.getPublicUsername(user);
                if (!TextUtils.isEmpty(un)) {
                    return "@" + un;
                }
                String name = UserObject.getFirstName(user);
                if (!TextUtils.isEmpty(name)) {
                    return name;
                }
            }
        }
        return "ShieldGram";
    }

    private boolean shouldShowShieldEmblem() {
        return ShieldGramConfig.headerStyle != ShieldGramConfig.HEADER_STYLE_MINIMAL;
    }

    private boolean shouldShowArmedPill() {
        return ShieldGramConfig.headerStyle == ShieldGramConfig.HEADER_STYLE_SHIELD_BADGE
                && (!SharedConfig.passcodeHash.isEmpty() || ShieldGramConfig.isRoyalGoldActive());
    }

    private String resolveBadgeLabel() {
        if (!SharedConfig.passcodeHash.isEmpty()) {
            return "ARMED";
        }
        return ShieldGramConfig.isRoyalGoldActive() ? "ROYAL" : "SHIELD";
    }

    @Override
    public int getIntrinsicWidth() {
        String text = resolveTitleText();
        float w = titlePaint.measureText(text);
        if (shouldShowShieldEmblem()) {
            w += dp(30);
        }
        if (shouldShowArmedPill()) {
            w += dp(8) + pillPaint.measureText(resolveBadgeLabel()) + dp(12);
        }
        return Math.max(dp(104), (int) Math.ceil(w + dp(4)));
    }

    @Override
    public int getIntrinsicHeight() {
        return dp(26);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) {
            return;
        }
        final boolean gold = ShieldGramConfig.isRoyalGoldActive();
        final int textColor = Theme.getColor(Theme.key_telegram_color_dialogsLogo);
        final int accent1 = ShieldGramConfig.getSignatureAccentColor(true);
        final int accent2 = ShieldGramConfig.getSignatureSecondaryColor(true);

        float x = b.left;
        float cy = b.centerY();

        if (shouldShowShieldEmblem()) {
            Drawable crest = resolveCrestDrawable();
            if (crest != null) {
                int iconSize = dp(24);
                int iconTop = (int) (cy - iconSize / 2f);
                crest.setBounds((int) x, iconTop, (int) x + iconSize, iconTop + iconSize);
                crest.setAlpha(alphaValue);
                crest.draw(canvas);
                x += iconSize + dp(7);
            }
        }

        String text = resolveTitleText();
        if (gold && ShieldGramConfig.cyberGlowAccents) {
            LinearGradient titleGrad = new LinearGradient(
                    x, cy - dp(10), x + titlePaint.measureText(text), cy + dp(10),
                    new int[]{0xFFFFF4D2, 0xFFF2C955, 0xFFD9A62E},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP
            );
            titlePaint.setShader(titleGrad);
        } else {
            titlePaint.setShader(null);
            titlePaint.setColor(textColor != 0 ? textColor : tintColor);
        }
        titlePaint.setAlpha(alphaValue);
        Paint.FontMetrics fm = titlePaint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, x, baseline, titlePaint);
        titlePaint.setShader(null);
        x += titlePaint.measureText(text);

        if (shouldShowArmedPill()) {
            x += dp(7);
            String badge = resolveBadgeLabel();
            float bw = pillPaint.measureText(badge) + dp(11);
            float bh = dpf2(15.5f);
            pillRect.set(x, cy - bh / 2f, x + bw, cy + bh / 2f);

            LinearGradient bgGrad = new LinearGradient(
                    pillRect.left, pillRect.top, pillRect.right, pillRect.bottom,
                    ColorUtils.setAlphaComponent(accent1, (int) (54 * (alphaValue / 255f))),
                    ColorUtils.setAlphaComponent(accent2, (int) (36 * (alphaValue / 255f))),
                    Shader.TileMode.CLAMP
            );
            badgeBgPaint.setShader(bgGrad);
            canvas.drawRoundRect(pillRect, bh / 2f, bh / 2f, badgeBgPaint);
            badgeBgPaint.setShader(null);

            badgeBorderPaint.setColor(ColorUtils.setAlphaComponent(accent1, (int) (145 * (alphaValue / 255f))));
            canvas.drawRoundRect(pillRect, bh / 2f, bh / 2f, badgeBorderPaint);

            pillPaint.setColor(gold ? 0xFFF7D774 : (ShieldGramConfig.cyberGlowAccents ? 0xFF8AB4FF : accent1));
            pillPaint.setAlpha(alphaValue);
            Paint.FontMetrics pfm = pillPaint.getFontMetrics();
            float pBaseline = cy - (pfm.ascent + pfm.descent) / 2f;
            canvas.drawText(badge, pillRect.centerX() - pillPaint.measureText(badge) / 2f, pBaseline, pillPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        this.alphaValue = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
