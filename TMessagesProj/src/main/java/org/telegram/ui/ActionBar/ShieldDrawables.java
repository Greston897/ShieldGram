package org.telegram.ui.ActionBar;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Cells.BaseCell;

/**
 * ShieldGram UI kit: shared drawables that give menus and tab pills
 * a modern, soft-glow look — rounded glass cards with hairline borders,
 * gradient pills with halos, and rounded ripple selectors for menu rows.
 * All colors are derived at draw time from the current theme tint,
 * so every theme (day / night / custom) keeps working automatically.
 */
public final class ShieldDrawables {

    private ShieldDrawables() {
    }

    //==========================================================================================
    // Color helpers
    //==========================================================================================

    public static int lighten(int color, float amount) {
        return ColorUtils.blendARGB(color, Color.WHITE, amount);
    }

    public static int darken(int color, float amount) {
        return ColorUtils.blendARGB(color, Color.BLACK, amount);
    }

    public static boolean isDarkColor(int color) {
        return ColorUtils.calculateLuminance(color) < 0.5f;
    }

    private static int withAlpha(int color, int alpha) {
        return ColorUtils.setAlphaComponent(color, Math.max(0, Math.min(255, alpha)));
    }

    //==========================================================================================
    // Gradient counter pills (unread badges)
    //==========================================================================================

    private static Paint pillPaint;
    private static final RectF pillRect = new RectF();

    private static final int PILL_CACHE_SIZE = 4;
    private static final LinearGradient[] pillShaders = new LinearGradient[PILL_CACHE_SIZE];
    private static final int[] pillShaderColors = new int[PILL_CACHE_SIZE];
    private static final int[] pillShaderAlphas = new int[PILL_CACHE_SIZE];
    private static final float[] pillShaderHeights = new float[PILL_CACHE_SIZE];
    private static int pillShaderPtr;

    /**
     * Draws a rounded counter pill with a vertical gradient and a crisp inner border,
     * taking both color and alpha from {@code sourcePaint} (callers animate paint alpha).
     */
    public static void drawGradientPill(Canvas canvas, RectF rect, Paint sourcePaint, float radiusPx) {
        if (canvas == null || rect == null || rect.isEmpty() || sourcePaint == null) {
            return;
        }
        int color = sourcePaint.getColor();
        int alpha = (int) (sourcePaint.getAlpha() * (Color.alpha(color) / 255f));
        if (alpha <= 2) {
            return;
        }
        if (pillPaint == null) {
            pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        float height = Math.max(1f, rect.height());
        float radius = Math.min(radiusPx, Math.min(rect.width(), rect.height()) / 2f);
        if (radius < 0) {
            radius = 0;
        }

        LinearGradient shader = null;
        for (int i = 0; i < PILL_CACHE_SIZE; i++) {
            if (pillShaders[i] != null && pillShaderColors[i] == color && pillShaderAlphas[i] == alpha && pillShaderHeights[i] == height) {
                shader = pillShaders[i];
                break;
            }
        }
        if (shader == null) {
            int top = withAlpha(lighten(color, 0.14f), alpha);
            int mid = withAlpha(color, alpha);
            int bottom = withAlpha(darken(color, 0.10f), (int) (alpha * 0.92f));
            shader = new LinearGradient(0, 0, 0, height,
                new int[]{top, mid, bottom},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            pillShaderPtr = (pillShaderPtr + 1) % PILL_CACHE_SIZE;
            pillShaders[pillShaderPtr] = shader;
            pillShaderColors[pillShaderPtr] = color;
            pillShaderAlphas[pillShaderPtr] = alpha;
            pillShaderHeights[pillShaderPtr] = height;
        }

        pillPaint.setStyle(Paint.Style.FILL);
        pillPaint.setShader(shader);
        pillPaint.setAlpha(255);
        canvas.drawRoundRect(rect, radius, radius, pillPaint);
        pillPaint.setShader(null);

        // hairline border for depth
        float stroke = AndroidUtilities.dpf2(0.75f);
        if (radius > stroke && rect.width() > stroke * 2) {
            pillPaint.setStyle(Paint.Style.STROKE);
            pillPaint.setStrokeWidth(stroke);
            pillPaint.setColor(withAlpha(darken(color, 0.16f), (int) (alpha * 0.35f)));
            pillRect.set(rect);
            pillRect.inset(stroke / 2f, stroke / 2f);
            canvas.drawRoundRect(pillRect, Math.max(0, radius - stroke / 2f), Math.max(0, radius - stroke / 2f), pillPaint);
            pillPaint.setStyle(Paint.Style.FILL);
        }
    }

    //==========================================================================================
    // Tab selector pill (chat folders / topics / search tabs)
    //==========================================================================================

    /**
     * Fully-rounded "stadium" selector with a soft halo, gradient body and hairline border.
     * Color is provided by the theme tab line color; intensity is fully controlled by
     * {@link #setAlpha(int)} so existing fade animations keep working.
     */
    public static class PillDrawable extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF tempRect = new RectF();

        private int color = Color.WHITE;
        private int alpha = 255;
        private ColorFilter colorFilter;

        private LinearGradient shader;
        private int shaderColor;
        private int shaderAlpha = -1;
        private float shaderHeight;

        public void setColor(int value) {
            colorFilter = null;
            if (color != value) {
                color = value;
                invalidateSelf();
            }
        }

        private int baseColor() {
            if (colorFilter instanceof PorterDuffColorFilter) {
                return ((PorterDuffColorFilter) colorFilter).getColor();
            }
            return color;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (alpha <= 1) {
                return;
            }
            rect.set(getBounds());
            if (rect.isEmpty()) {
                return;
            }
            final int color = baseColor();
            final float a = alpha / 255f;
            final float rad = Math.min(rect.width(), rect.height()) / 2f;

            final float glowOuter = AndroidUtilities.dpf2(3f);
            final float glowInner = AndroidUtilities.dpf2(1.5f);
            final float border = AndroidUtilities.dpf2(1.1f);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);

            // soft outer halo
            tempRect.set(rect);
            tempRect.inset(-glowOuter, -glowOuter);
            paint.setColor(withAlpha(color, (int) (12 * a)));
            canvas.drawRoundRect(tempRect, rad + glowOuter, rad + glowOuter, paint);

            // inner halo
            tempRect.set(rect);
            tempRect.inset(-glowInner, -glowInner);
            paint.setColor(withAlpha(color, (int) (22 * a)));
            canvas.drawRoundRect(tempRect, rad + glowInner, rad + glowInner, paint);

            // gradient body
            if (shader == null || shaderColor != color || shaderAlpha != alpha || shaderHeight != rect.height()) {
                int top = withAlpha(lighten(color, 0.10f), (int) (38 * a));
                int mid = withAlpha(color, (int) (31 * a));
                int bottom = withAlpha(darken(color, 0.07f), (int) (26 * a));
                shader = new LinearGradient(0, 0, 0, rect.height(),
                    new int[]{top, mid, bottom},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
                shaderColor = color;
                shaderAlpha = alpha;
                shaderHeight = rect.height();
            }
            paint.setShader(shader);
            paint.setAlpha(255);
            canvas.drawRoundRect(rect, rad, rad, paint);
            paint.setShader(null);

            // crisp hairline border
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(border);
            paint.setColor(withAlpha(lighten(color, 0.04f), (int) (76 * a)));
            tempRect.set(rect);
            tempRect.inset(border / 2f, border / 2f);
            float borderRad = Math.max(0, rad - border / 2f);
            canvas.drawRoundRect(tempRect, borderRad, borderRad, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void setAlpha(int alpha) {
            if (this.alpha != alpha) {
                this.alpha = alpha;
                invalidateSelf();
            }
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter filter) {
            colorFilter = filter;
            invalidateSelf();
        }

        @Override
        public void getOutline(@NonNull Outline outline) {
            outline.setRoundRect(getBounds(), Math.min(getBounds().width(), getBounds().height()) / 2f);
        }
    }

    //==========================================================================================
    // Popup menu background (glass card with hairline border and soft shadow)
    //==========================================================================================

    /**
     * Replaces the old popup 9-patch: a rounded card with a subtle vertical gradient,
     * a thin adaptive border (light on dark themes, dark on light ones) and a soft
     * multi-ring shadow. The background color arrives through a MULTIPLY
     * {@link PorterDuffColorFilter} — we read it directly so the card always matches
     * the current theme, including animated theme transitions.
     */
    public static class MenuBackgroundDrawable extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF tempRect = new RectF();
        private final RectF bodyRect = new RectF();
        private final Rect pad = new Rect();

        private int alpha = 255;
        private ColorFilter colorFilter;

        private final float bodyInset;
        private final float bodyRadius;

        public MenuBackgroundDrawable() {
            this(null);
        }

        public MenuBackgroundDrawable(@Nullable Rect padding) {
            if (padding != null) {
                pad.set(padding);
            }
            bodyInset = AndroidUtilities.dpf2(3f);
            bodyRadius = AndroidUtilities.dpf2(16f);
        }

        private int baseColor() {
            if (colorFilter instanceof PorterDuffColorFilter) {
                return ((PorterDuffColorFilter) colorFilter).getColor();
            }
            return Color.WHITE;
        }

        private void drawShadowRing(Canvas canvas, float inset, int color) {
            tempRect.set(rect);
            tempRect.inset(inset, inset);
            float r = Math.max(0, bodyRadius - (bodyInset - inset));
            float rr = Math.min(r, Math.min(tempRect.width(), tempRect.height()) / 2f);
            if (rr < 0) {
                rr = 0;
            }
            paint.setColor(color);
            canvas.drawRoundRect(tempRect, rr, rr, paint);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (alpha <= 1) {
                return;
            }
            rect.set(getBounds());
            if (rect.isEmpty()) {
                return;
            }
            final float a = alpha / 255f;
            final int base = baseColor();
            final boolean dark = isDarkColor(base);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);

            // soft drop shadow made of stacked rounded rings
            drawShadowRing(canvas, AndroidUtilities.dpf2(0.5f), withAlpha(Color.BLACK, (int) ((dark ? 11 : 6) * a)));
            drawShadowRing(canvas, AndroidUtilities.dpf2(1.5f), withAlpha(Color.BLACK, (int) ((dark ? 17 : 9) * a)));
            drawShadowRing(canvas, AndroidUtilities.dpf2(2.5f), withAlpha(Color.BLACK, (int) ((dark ? 24 : 13) * a)));

            // card body — exactly the theme color (shape only, no color shift)
            bodyRect.set(rect);
            bodyRect.inset(bodyInset, bodyInset);
            if (bodyRect.isEmpty()) {
                return;
            }
            float r = Math.min(bodyRadius, Math.min(bodyRect.width(), bodyRect.height()) / 2f);

            paint.setColor(withAlpha(base, (int) (Color.alpha(base) * a)));
            canvas.drawRoundRect(bodyRect, r, r, paint);

            // hairline border, adaptive to theme brightness
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dpf2(1f));
            paint.setColor(withAlpha(dark ? lighten(base, 0.18f) : darken(base, 0.16f), (int) ((dark ? 38 : 26) * a)));
            tempRect.set(bodyRect);
            tempRect.inset(AndroidUtilities.dpf2(0.5f), AndroidUtilities.dpf2(0.5f));
            float br = Math.max(0, r - AndroidUtilities.dpf2(0.5f));
            canvas.drawRoundRect(tempRect, br, br, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void setAlpha(int alpha) {
            if (this.alpha != alpha) {
                this.alpha = alpha;
                invalidateSelf();
            }
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter filter) {
            colorFilter = filter;
            invalidateSelf();
        }

        @Override
        public boolean getPadding(@NonNull Rect padding) {
            padding.set(pad);
            return true;
        }

        @Override
        public void getOutline(@NonNull Outline outline) {
            outline.setRoundRect(
                (int) (getBounds().left + bodyInset),
                (int) (getBounds().top + bodyInset),
                (int) (getBounds().right - bodyInset),
                (int) (getBounds().bottom - bodyInset),
                bodyRadius);
        }
    }

    //==========================================================================================
    // Menu row selector (rounded ripple)
    //==========================================================================================

    /**
     * Fully-rounded ripple highlight for menu rows. Because popup containers add
     * horizontal padding around rows, every row can use the same stadium-like
     * rounding instead of only squaring the outer corners.
     */
    public static Drawable createMenuSelector(int color) {
        int ca = Color.alpha(color);
        if (ca > 0 && ca < 90) {
            color = withAlpha(color, Math.min(90, (int) (ca * 1.7f)));
        }
        float r = AndroidUtilities.dpf2(11f);
        ShapeDrawable mask = new ShapeDrawable(new RoundRectShape(
            new float[]{r, r, r, r, r, r, r, r}, null, null));
        mask.getPaint().setColor(Color.WHITE);
        return new BaseCell.RippleDrawableSafe(ColorStateList.valueOf(color), null, mask);
    }
}
