package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

public class MessageBackgroundDrawable extends Drawable {

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint customPaint = null;
    private long lastAnimationTime;
    private float currentAnimationProgress;
    private boolean isSelected;
    private boolean animationInProgress;
    private float finalRadius;
    private float touchX = -1;
    private float touchY = -1;
    private float touchOverrideX = -1;
    private float touchOverrideY = -1;
    private long lastTouchTime;
    private View parentView;

    public MessageBackgroundDrawable(View parent) {
        parentView = parent;
    }

    public void setColor(int color) {
        paint.setColor(color);
    }

    public void setCustomPaint(Paint paint) {
        this.customPaint = paint;
    }

    public void setSelected(boolean selected, boolean animated) {
        if (isSelected == selected) {
            if (animationInProgress != animated && !animated) {
                currentAnimationProgress = selected ? 1.0f : 0.0f;
                animationInProgress = false;
            }
            return;
        }
        isSelected = selected;
        animationInProgress = animated;
        if (animated) {
            lastAnimationTime = SystemClock.elapsedRealtime();
        } else {
            currentAnimationProgress = selected ? 1.0f : 0.0f;
        }
        calcRadius();
        invalidate();
    }

    private void invalidate() {
        if (parentView != null) {
            parentView.invalidate();
            if (parentView.getParent() != null) {
                ((ViewGroup) parentView.getParent()).invalidate();
            }
        }
    }

    private void calcRadius() {
        Rect bounds = getBounds();
        float x1 = bounds.centerX();
        float y1 = bounds.centerY();
        finalRadius = (float) Math.ceil(Math.sqrt((bounds.left - x1) * (bounds.left - x1) + (bounds.top - y1) * (bounds.top - y1)));
    }

    public void setTouchCoords(float x, float y) {
        touchX = x;
        touchY = y;
        lastTouchTime = SystemClock.elapsedRealtime();
    }

    public void setTouchCoordsOverride(float x, float y) {
        touchOverrideX = x;
        touchOverrideY = y;
    }

    public float getTouchX() {
        return touchX;
    }

    public float getTouchY() {
        return touchY;
    }

    public long getLastTouchTime() {
        return lastTouchTime;
    }

    public boolean isAnimationInProgress() {
        return animationInProgress;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        calcRadius();
    }

    @Override
    public void setBounds(Rect bounds) {
        super.setBounds(bounds);
        calcRadius();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    private final android.graphics.RectF shieldSelectRect = new android.graphics.RectF();
    private final android.graphics.Path shieldSelectClipPath = new android.graphics.Path();
    private final Paint shieldSelectBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shieldSelectBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (currentAnimationProgress > 0.0f && !bounds.isEmpty()) {
            float ix = org.telegram.messenger.AndroidUtilities.dpf2(5.5f);
            float iy = org.telegram.messenger.AndroidUtilities.dpf2(1.5f);
            float rad = org.telegram.messenger.AndroidUtilities.dpf2(14f);
            shieldSelectRect.set(bounds.left + ix, bounds.top + iy, bounds.right - ix, bounds.bottom - iy);
            if (shieldSelectRect.width() > 0 && shieldSelectRect.height() > 0) {
                float p;
                if (currentAnimationProgress == 1.0f) {
                    p = 1.0f;
                } else if (isSelected) {
                    p = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(currentAnimationProgress);
                } else {
                    p = 1.0f - CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(1.0f - currentAnimationProgress);
                }

                int sigAccent = org.telegram.shieldgram.ShieldGramConfig.getSignatureAccentColor(true);
                int sigSecondary = org.telegram.shieldgram.ShieldGramConfig.getSignatureSecondaryColor(true);

                canvas.save();
                shieldSelectClipPath.rewind();
                shieldSelectClipPath.addRoundRect(shieldSelectRect, rad, rad, android.graphics.Path.Direction.CW);
                canvas.clipPath(shieldSelectClipPath);

                Paint basePaint = customPaint != null ? customPaint : paint;
                if (currentAnimationProgress == 1.0f) {
                    canvas.drawRect(shieldSelectRect, basePaint);
                } else {
                    float centerX = bounds.centerX();
                    float centerY = bounds.centerY();
                    float x1 = (touchOverrideX >= 0 && touchOverrideY >= 0) ? touchOverrideX : (touchX >= 0 && touchY >= 0 ? touchX : centerX);
                    float y1 = (touchOverrideX >= 0 && touchOverrideY >= 0) ? touchOverrideY : (touchX >= 0 && touchY >= 0 ? touchY : centerY);
                    x1 = centerX + (1.0f - p) * (x1 - centerX);
                    y1 = centerY + (1.0f - p) * (y1 - centerY);
                    canvas.drawCircle(x1, y1, finalRadius * p, basePaint);
                }

                shieldSelectBarPaint.setShader(new android.graphics.LinearGradient(
                        shieldSelectRect.left, shieldSelectRect.top, shieldSelectRect.right, shieldSelectRect.bottom,
                        androidx.core.graphics.ColorUtils.setAlphaComponent(sigAccent, (int) (46 * p)),
                        androidx.core.graphics.ColorUtils.setAlphaComponent(sigSecondary, (int) (14 * p)),
                        android.graphics.Shader.TileMode.CLAMP
                ));
                canvas.drawRect(shieldSelectRect, shieldSelectBarPaint);
                shieldSelectBarPaint.setShader(null);
                canvas.restore();

                shieldSelectBorderPaint.setStyle(Paint.Style.STROKE);
                shieldSelectBorderPaint.setStrokeWidth(org.telegram.messenger.AndroidUtilities.dpf2(1.1f));
                shieldSelectBorderPaint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(sigAccent, (int) (150 * p)));
                canvas.drawRoundRect(shieldSelectRect, rad, rad, shieldSelectBorderPaint);

                float barH = Math.min(shieldSelectRect.height() * 0.55f, org.telegram.messenger.AndroidUtilities.dpf2(24f)) * p;
                if (barH > org.telegram.messenger.AndroidUtilities.dpf2(4f)) {
                    float cy = shieldSelectRect.centerY();
                    org.telegram.messenger.AndroidUtilities.rectTmp.set(
                            shieldSelectRect.left + org.telegram.messenger.AndroidUtilities.dpf2(2.5f),
                            cy - barH / 2f,
                            shieldSelectRect.left + org.telegram.messenger.AndroidUtilities.dpf2(5.2f),
                            cy + barH / 2f
                    );
                    shieldSelectBarPaint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(sigAccent, (int) (225 * p)));
                    canvas.drawRoundRect(
                            org.telegram.messenger.AndroidUtilities.rectTmp,
                            org.telegram.messenger.AndroidUtilities.dpf2(2f),
                            org.telegram.messenger.AndroidUtilities.dpf2(2f),
                            shieldSelectBarPaint
                    );
                }
            }
        }
        if (animationInProgress) {
            long newTime = SystemClock.elapsedRealtime();
            long dt = newTime - lastAnimationTime;
            if (dt > 20) {
                dt = 17;
            }
            lastAnimationTime = newTime;

            boolean finished = false;
            if (isSelected) {
                currentAnimationProgress += dt / 240.0f;
                if (currentAnimationProgress >= 1.0f) {
                    currentAnimationProgress = 1.0f;
                    finished = true;
                }
            } else {
                currentAnimationProgress -= dt / 240.0f;
                if (currentAnimationProgress <= 0.0f) {
                    currentAnimationProgress = 0.0f;
                    finished = true;
                }
            }
            if (finished) {
                touchX = -1;
                touchY = -1;
                touchOverrideX = -1;
                touchOverrideY = -1;
                animationInProgress = false;
            }
            invalidate();
        }
    }
}
