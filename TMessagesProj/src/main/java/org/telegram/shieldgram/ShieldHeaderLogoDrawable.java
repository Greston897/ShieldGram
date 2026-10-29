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
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class ShieldHeaderLogoDrawable extends Drawable {

    private final Context context;
    private final int currentAccount;
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Drawable cachedCrestDrawable;
    private int cachedCrestResId;
    private int alphaValue = 255;

    public ShieldHeaderLogoDrawable(Context context, int currentAccount) {
        this.context = context;
        this.currentAccount = currentAccount;

        titlePaint.setTypeface(AndroidUtilities.bold());
        titlePaint.setTextSize(dpf2(20f));
        titlePaint.setLetterSpacing(-0.015f);
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

    @Override
    public int getIntrinsicWidth() {
        String text = resolveTitleText();
        float w = titlePaint.measureText(text);
        if (shouldShowShieldEmblem()) {
            w += dp(28);
        }
        return Math.max(dp(96), (int) Math.ceil(w + dp(4)));
    }

    @Override
    public int getIntrinsicHeight() {
        return dp(24);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) {
            return;
        }
        final boolean gold = ShieldGramConfig.isRoyalGoldActive();
        final int textColor = Theme.getColor(Theme.key_telegram_color_dialogsLogo);

        float x = b.left;
        float cy = b.centerY();

        if (shouldShowShieldEmblem()) {
            Drawable crest = resolveCrestDrawable();
            if (crest != null) {
                int iconSize = dp(22);
                int iconTop = (int) (cy - iconSize / 2f);
                crest.setBounds((int) x, iconTop, (int) x + iconSize, iconTop + iconSize);
                crest.setAlpha(alphaValue);
                crest.draw(canvas);
                x += iconSize + dp(6);
            }
        }

        String text = resolveTitleText();
        if (gold) {
            LinearGradient titleGrad = new LinearGradient(
                    x, cy - dp(10), x + titlePaint.measureText(text), cy + dp(10),
                    new int[]{0xFFFFF5D6, 0xFFF2C955, 0xFFDCAA32},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP
            );
            titlePaint.setShader(titleGrad);
        } else {
            titlePaint.setShader(null);
            titlePaint.setColor(textColor != 0 ? textColor : 0xFFFFFFFF);
        }
        titlePaint.setAlpha(alphaValue);
        Paint.FontMetrics fm = titlePaint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, x, baseline, titlePaint);
        titlePaint.setShader(null);
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
