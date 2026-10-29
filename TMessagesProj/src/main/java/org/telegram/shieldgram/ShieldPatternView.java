package org.telegram.shieldgram;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;

public class ShieldPatternView extends View {

    public static final int MIN_PATTERN_NODES = 4;

    public static final int STATE_IDLE = 0;
    public static final int STATE_DRAWING = 1;
    public static final int STATE_SUCCESS = 2;
    public static final int STATE_ERROR = 3;

    public interface OnPatternListener {
        void onPatternStart();
        void onPatternCleared();
        void onPatternComplete(@NonNull String patternCode, int nodeCount);
    }

    private final ArrayList<Integer> selectedNodes = new ArrayList<>(9);
    private final boolean[] nodeSelected = new boolean[9];
    private final float[] nodeActivationProgress = new float[9];
    private final float[] nodeCenterX = new float[9];
    private final float[] nodeCenterY = new float[9];

    private final Paint outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pathGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();

    private boolean lockScreenMode;
    private int state = STATE_IDLE;
    private float currentTouchX;
    private float currentTouchY;
    private boolean isTracking;
    private boolean inputEnabled = true;

    private OnPatternListener listener;
    private final Runnable autoClearRunnable = this::clearPattern;

    public ShieldPatternView(Context context, boolean lockScreenMode) {
        super(context);
        this.lockScreenMode = lockScreenMode;

        outerRingPaint.setStyle(Paint.Style.STROKE);
        outerRingPaint.setStrokeWidth(dpf2(1.8f));

        innerDotPaint.setStyle(Paint.Style.FILL);

        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeCap(Paint.Cap.ROUND);
        pathPaint.setStrokeJoin(Paint.Join.ROUND);
        pathPaint.setStrokeWidth(dpf2(4.2f));

        pathGlowPaint.setStyle(Paint.Style.STROKE);
        pathGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        pathGlowPaint.setStrokeJoin(Paint.Join.ROUND);
        pathGlowPaint.setStrokeWidth(dpf2(11f));
    }

    public void setOnPatternListener(@Nullable OnPatternListener listener) {
        this.listener = listener;
    }

    public void setInputEnabled(boolean enabled) {
        this.inputEnabled = enabled;
    }

    public void setPatternState(int newState, boolean autoResetOnError) {
        removeCallbacks(autoClearRunnable);
        this.state = newState;
        invalidate();
        if (newState == STATE_ERROR && autoResetOnError) {
            postDelayed(autoClearRunnable, 720);
        }
    }

    public void clearPattern() {
        removeCallbacks(autoClearRunnable);
        selectedNodes.clear();
        for (int i = 0; i < 9; i++) {
            nodeSelected[i] = false;
            nodeActivationProgress[i] = 0f;
        }
        isTracking = false;
        state = STATE_IDLE;
        invalidate();
        if (listener != null) {
            listener.onPatternCleared();
        }
    }

    public String getPatternString() {
        StringBuilder sb = new StringBuilder(selectedNodes.size() + 2);
        sb.append("P:");
        for (int i = 0; i < selectedNodes.size(); i++) {
            sb.append(selectedNodes.get(i));
        }
        return sb.toString();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeNodeCoordinates(w, h);
    }

    private void computeNodeCoordinates(int w, int h) {
        float side = Math.min(w, h);
        float padding = Math.max(dp(28), side * 0.14f);
        float usable = Math.max(dp(120), side - padding * 2f);
        float step = usable / 2f;
        float startX = (w - usable) / 2f;
        float startY = (h - usable) / 2f;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                nodeCenterX[idx] = startX + col * step;
                nodeCenterY[idx] = startY + row * step;
            }
        }
    }

    private int getPrimaryAccentColor() {
        if (state == STATE_ERROR) {
            return 0xFFFB7185;
        }
        if (state == STATE_SUCCESS) {
            return 0xFF34D399;
        }
        if (lockScreenMode) {
            return 0xFF5B8CFF;
        }
        return Theme.getColor(Theme.key_featuredStickers_addButton);
    }

    private int getSecondaryAccentColor() {
        if (state == STATE_ERROR) {
            return 0xFFE11D48;
        }
        if (state == STATE_SUCCESS) {
            return 0xFF10B981;
        }
        return 0xFF38BDF8;
    }

    private int getIdleNodeColor() {
        if (lockScreenMode) {
            return 0x88E2E8F0;
        }
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.65f);
    }

    private int getIdleRingColor() {
        if (lockScreenMode) {
            return 0x2EE2E8F0;
        }
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.24f);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (nodeCenterX[8] == 0 && getWidth() > 0 && getHeight() > 0) {
            computeNodeCoordinates(getWidth(), getHeight());
        }

        final int accent1 = getPrimaryAccentColor();
        final int accent2 = getSecondaryAccentColor();
        final int idleDot = getIdleNodeColor();
        final int idleRing = getIdleRingColor();
        final boolean drawGlow = ShieldGramConfig.cyberGlowAccents;

        // 1. Draw connecting cyber beam
        if (!selectedNodes.isEmpty()) {
            linePath.reset();
            int first = selectedNodes.get(0);
            linePath.moveTo(nodeCenterX[first], nodeCenterY[first]);
            for (int i = 1; i < selectedNodes.size(); i++) {
                int idx = selectedNodes.get(i);
                linePath.lineTo(nodeCenterX[idx], nodeCenterY[idx]);
            }
            if (isTracking && state == STATE_DRAWING) {
                linePath.lineTo(currentTouchX, currentTouchY);
            }

            LinearGradient beamGrad = new LinearGradient(
                    nodeCenterX[0], nodeCenterY[0],
                    nodeCenterX[8], nodeCenterY[8],
                    new int[]{accent1, accent2},
                    null,
                    Shader.TileMode.CLAMP
            );

            if (drawGlow) {
                pathGlowPaint.setShader(null);
                pathGlowPaint.setColor(ColorUtils.setAlphaComponent(accent1, 56));
                canvas.drawPath(linePath, pathGlowPaint);
            }

            pathPaint.setShader(beamGrad);
            pathPaint.setAlpha(235);
            canvas.drawPath(linePath, pathPaint);
            pathPaint.setShader(null);
        }

        // 2. Draw 3x3 cyber nodes
        final float baseDotRadius = dpf2(5.5f);
        final float activeDotRadius = dpf2(7.5f);
        final float baseRingRadius = dpf2(19f);
        final float activeRingRadius = dpf2(22.5f);

        for (int i = 0; i < 9; i++) {
            float cx = nodeCenterX[i];
            float cy = nodeCenterY[i];
            boolean active = nodeSelected[i];
            float p = active ? nodeActivationProgress[i] : 0f;

            if (active && drawGlow) {
                float glowR = dpf2(28f) * (0.7f + 0.3f * p);
                RadialGradient rg = new RadialGradient(
                        cx, cy, Math.max(1f, glowR),
                        new int[]{ColorUtils.setAlphaComponent(accent1, 88), 0x00000000},
                        new float[]{0f, 1f},
                        Shader.TileMode.CLAMP
                );
                glowPaint.setShader(rg);
                canvas.drawCircle(cx, cy, glowR, glowPaint);
                glowPaint.setShader(null);
            }

            float ringR = AndroidUtilities.lerp(baseRingRadius, activeRingRadius, p);
            outerRingPaint.setColor(active ? ColorUtils.blendARGB(idleRing, accent1, Math.max(0.45f, p)) : idleRing);
            outerRingPaint.setStrokeWidth(active ? dpf2(2.2f) : dpf2(1.4f));
            canvas.drawCircle(cx, cy, ringR, outerRingPaint);

            if (active) {
                innerDotPaint.setColor(ColorUtils.setAlphaComponent(accent1, 44));
                canvas.drawCircle(cx, cy, ringR - dpf2(2f), innerDotPaint);
            }

            float dotR = AndroidUtilities.lerp(baseDotRadius, activeDotRadius, p);
            innerDotPaint.setColor(active ? (lockScreenMode ? 0xFFFFFFFF : accent2) : idleDot);
            canvas.drawCircle(cx, cy, dotR, innerDotPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!inputEnabled || !isEnabled()) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                removeCallbacks(autoClearRunnable);
                int hit = detectHitNode(x, y);
                selectedNodes.clear();
                for (int i = 0; i < 9; i++) {
                    nodeSelected[i] = false;
                    nodeActivationProgress[i] = 0f;
                }
                isTracking = true;
                state = STATE_DRAWING;
                currentTouchX = x;
                currentTouchY = y;
                ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                if (listener != null) {
                    listener.onPatternStart();
                }
                if (hit >= 0) {
                    addNode(hit);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!isTracking) {
                    return false;
                }
                int historySize = event.getHistorySize();
                for (int h = 0; h < historySize; h++) {
                    handleDragPoint(event.getHistoricalX(h), event.getHistoricalY(h));
                }
                handleDragPoint(x, y);
                currentTouchX = x;
                currentTouchY = y;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!isTracking) {
                    return false;
                }
                isTracking = false;
                ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(false);
                }
                if (selectedNodes.isEmpty()) {
                    state = STATE_IDLE;
                    invalidate();
                    return true;
                }
                if (listener != null) {
                    listener.onPatternComplete(getPatternString(), selectedNodes.size());
                }
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void handleDragPoint(float x, float y) {
        int hit = detectHitNode(x, y);
        if (hit >= 0 && !nodeSelected[hit]) {
            if (!selectedNodes.isEmpty()) {
                int last = selectedNodes.get(selectedNodes.size() - 1);
                int intermediate = getIntermediateNode(last, hit);
                if (intermediate >= 0 && !nodeSelected[intermediate]) {
                    addNode(intermediate);
                }
            }
            addNode(hit);
        }
    }

    private int getIntermediateNode(int a, int b) {
        int r1 = a / 3, c1 = a % 3;
        int r2 = b / 3, c2 = b % 3;
        if (((r1 + r2) % 2 == 0) && ((c1 + c2) % 2 == 0)) {
            return ((r1 + r2) / 2) * 3 + ((c1 + c2) / 2);
        }
        return -1;
    }

    private int detectHitNode(float x, float y) {
        float hitRadius = dpf2(28f);
        float hitRadiusSq = hitRadius * hitRadius;
        for (int i = 0; i < 9; i++) {
            float dx = x - nodeCenterX[i];
            float dy = y - nodeCenterY[i];
            if (dx * dx + dy * dy <= hitRadiusSq) {
                return i;
            }
        }
        return -1;
    }

    private void addNode(final int idx) {
        if (idx < 0 || idx >= 9 || nodeSelected[idx]) {
            return;
        }
        nodeSelected[idx] = true;
        selectedNodes.add(idx);
        try {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignored) {
        }
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(210);
        anim.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        anim.addUpdateListener(a -> {
            nodeActivationProgress[idx] = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }
}
