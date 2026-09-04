package com.poorgrammera.bydsubai.ui;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;


import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * AsuradaView — Asurada control unit overlay.
 *
 * Layout (horizontal):
 *   [ RED PANEL (3 cells) ] — [ CIRCLE (2×2 green orbs) ] — [ RED PANEL (3 cells) ]
 *
 * Red Panel Animation (radial wave from center outward, then back):
 *   Phase 0: ○○○ - ○○○  (all off)
 *   Phase 1: ○○● - ●○○  (innermost cells lit)
 *   Phase 2: ○●● - ●●○  (inner + middle lit)
 *   Phase 3: ●●● - ●●●  (all lit)
 *   Then reverses back to Phase 0.
 *
 * Green Orbs Animation: sequential clockwise highlight.
 */
public class AsuradaView extends View {

    // --- Paints ---
    private Paint bgPaint;
    private Paint panelBgPaint;
    private Paint panelBorderPaint;
    private Paint cellOnPaint;      // lit red cell
    private Paint cellOffPaint;     // unlit red cell
    private Paint circleBgPaint;
    private Paint circleBorderPaint;
    private Paint orbPaint;
    private Paint orbGlowPaint;

    // --- Animation state ---
    // wavePhase: 0.0 → 3.0 → 0.0 (REVERSE), controls which cells are lit
    private ValueAnimator waveAnimator;
    private float wavePhase = 0f;

    // orbProgress: 0.0 → 4.0 cycling, which orb is highlighted
    private ValueAnimator orbAnimator;
    private float orbProgress = 0f;

    // --- Geometry (computed in onSizeChanged) ---
    private final RectF viewRect     = new RectF();
    private final RectF leftPanel    = new RectF();
    private final RectF rightPanel   = new RectF();
    private final RectF centerCircle = new RectF();
    private final RectF[] leftCells  = {new RectF(), new RectF(), new RectF()};
    private final RectF[] rightCells = {new RectF(), new RectF(), new RectF()};
    private final float[] orbCx      = new float[4];
    private final float[] orbCy      = new float[4];
    private float orbRadius = 0f;
    private float cornerR   = 0f;

    // Clockwise orb order: TL, TR, BR, BL
    private static final int[] ORB_ORDER = {0, 1, 3, 2};

    // Left panel: index 2 is closest to center, index 0 is outermost
    // Right panel: index 0 is closest to center, index 2 is outermost
    // wavePhase threshold for each cell distance from center:
    //   distance 1 (innermost) lights at phase >= 1
    //   distance 2 (middle)    lights at phase >= 2
    //   distance 3 (outermost) lights at phase >= 3
    // leftCells[2]  = distance 1, leftCells[1]  = distance 2, leftCells[0]  = distance 3
    // rightCells[0] = distance 1, rightCells[1] = distance 2, rightCells[2] = distance 3
    private static final float[] LEFT_THRESHOLDS  = {3f, 2f, 1f}; // [0]=outer, [1]=mid, [2]=inner
    private static final float[] RIGHT_THRESHOLDS = {1f, 2f, 3f}; // [0]=inner, [1]=mid, [2]=outer

    public AsuradaView(Context context) {
        super(context);
        init();
    }

    public AsuradaView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AsuradaView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xE8181818);
        bgPaint.setStyle(Paint.Style.FILL);

        panelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        panelBgPaint.setColor(0xFF1A1A1A);
        panelBgPaint.setStyle(Paint.Style.FILL);

        panelBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        panelBorderPaint.setColor(0xFF888888);
        panelBorderPaint.setStyle(Paint.Style.STROKE);
        panelBorderPaint.setStrokeWidth(dpToPx(1.2f));

        // Lit cell: bright red with glow
        cellOnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cellOnPaint.setStyle(Paint.Style.FILL);

        // Unlit cell: dark red (barely visible)
        cellOffPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cellOffPaint.setColor(Color.argb(180, 50, 8, 8));
        cellOffPaint.setStyle(Paint.Style.FILL);

        circleBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circleBgPaint.setColor(0xFF111111);
        circleBgPaint.setStyle(Paint.Style.FILL);

        circleBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circleBorderPaint.setColor(0xFFAAAAAA);
        circleBorderPaint.setStyle(Paint.Style.STROKE);
        circleBorderPaint.setStrokeWidth(dpToPx(1.5f));

        orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        orbPaint.setStyle(Paint.Style.FILL);

        orbGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        orbGlowPaint.setStyle(Paint.Style.FILL);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------
    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (w == 0 || h == 0) return;

        float pad     = dpToPx(2f);
        cornerR       = dpToPx(4f);
        float circleD = h - 2f * pad;
        float circleR = circleD / 2f;
        float cx      = w / 2f;
        float cy      = h / 2f;
        float gap     = dpToPx(3f);
        float panelW  = (w / 2f - circleR - gap - pad);
        float panelH  = circleD * 0.75f;
        float panelTop = cy - panelH / 2f;
        float panelBot = cy + panelH / 2f;

        leftPanel.set(pad, panelTop, pad + panelW, panelBot);
        rightPanel.set(w - pad - panelW, panelTop, w - pad, panelBot);
        centerCircle.set(cx - circleR, cy - circleR, cx + circleR, cy + circleR);

        float cellGap = dpToPx(2f);
        float cellW   = (panelW - 4f * cellGap) / 3f;
        for (int i = 0; i < 3; i++) {
            float x0 = leftPanel.left + cellGap + i * (cellW + cellGap);
            leftCells[i].set(x0, panelTop + cellGap, x0 + cellW, panelBot - cellGap);

            float rx = rightPanel.left + cellGap + i * (cellW + cellGap);
            rightCells[i].set(rx, panelTop + cellGap, rx + cellW, panelBot - cellGap);
        }

        float orbOffset = circleR * 0.33f;
        orbRadius = circleR * 0.28f;
        orbCx[0] = cx - orbOffset; orbCy[0] = cy - orbOffset;
        orbCx[1] = cx + orbOffset; orbCy[1] = cy - orbOffset;
        orbCx[2] = cx - orbOffset; orbCy[2] = cy + orbOffset;
        orbCx[3] = cx + orbOffset; orbCy[3] = cy + orbOffset;
    }

    // -------------------------------------------------------------------------
    // Animation
    // -------------------------------------------------------------------------
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimations();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimations();
    }

    private void startAnimations() {
        // Wave: 0.0 → 3.0 (in) then 3.0 → 0.0 (out), each direction ~600ms
        if (waveAnimator == null) {
            waveAnimator = ValueAnimator.ofFloat(0f, 3f);
            waveAnimator.setDuration(600);
            waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
            waveAnimator.setRepeatMode(ValueAnimator.REVERSE);
            waveAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            waveAnimator.addUpdateListener(anim -> {
                wavePhase = (float) anim.getAnimatedValue();
                invalidate();
            });
            waveAnimator.start();
        }

        // Orb sequential clockwise
        if (orbAnimator == null) {
            orbAnimator = ValueAnimator.ofFloat(0f, 4f);
            orbAnimator.setDuration(1600);
            orbAnimator.setRepeatCount(ValueAnimator.INFINITE);
            orbAnimator.setRepeatMode(ValueAnimator.RESTART);
            orbAnimator.setInterpolator(new LinearInterpolator());
            orbAnimator.addUpdateListener(anim -> {
                orbProgress = (float) anim.getAnimatedValue();
                invalidate();
            });
            orbAnimator.start();
        }
    }

    private void stopAnimations() {
        if (waveAnimator != null) { waveAnimator.cancel(); waveAnimator = null; }
        if (orbAnimator  != null) { orbAnimator.cancel();  orbAnimator  = null; }
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        viewRect.set(0, 0, w, h);
        canvas.drawRoundRect(viewRect, h / 2f, h / 2f, bgPaint);

        drawPanel(canvas, leftPanel, leftCells, LEFT_THRESHOLDS);
        drawPanel(canvas, rightPanel, rightCells, RIGHT_THRESHOLDS);
        drawCenterCircle(canvas);
    }

    /**
     * @param thresholds per-cell wavePhase threshold to light up.
     *                   Cell lights up when wavePhase >= threshold.
     *                   Brightness = clamp((wavePhase - threshold + 1), 0, 1)
     *                   so it fades in smoothly over 1 phase unit.
     */
    private void drawPanel(Canvas canvas, RectF panel, RectF[] cells, float[] thresholds) {
        canvas.drawRoundRect(panel, cornerR, cornerR, panelBgPaint);
        canvas.drawRoundRect(panel, cornerR, cornerR, panelBorderPaint);

        for (int i = 0; i < 3; i++) {
            RectF cell = cells[i];
            float threshold = thresholds[i];
            // Smooth brightness: 0 when phase < threshold-1, ramps to 1 at phase == threshold
            float brightness = Math.max(0f, Math.min(1f, wavePhase - threshold + 1f));

            if (brightness <= 0.02f) {
                // Completely off — draw dark base
                canvas.drawRoundRect(cell, cornerR * 0.5f, cornerR * 0.5f, cellOffPaint);
            } else {
                // Lit: interpolate between off-color and full bright red
                int r = (int) (50  + 205 * brightness);  // 50 → 255
                int g = (int) (8   * (1f - brightness));  // slight fade to 0
                int b = (int) (8   * (1f - brightness));
                int a = (int) (180 + 75  * brightness);   // 180 → 255

                cellOnPaint.setColor(Color.argb(a, r, g, b));
                canvas.drawRoundRect(cell, cornerR * 0.5f, cornerR * 0.5f, cellOnPaint);

                // Highlight glow on top half of lit cell
                if (brightness > 0.3f) {
                    Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    glowPaint.setStyle(Paint.Style.FILL);
                    glowPaint.setColor(Color.argb((int)(100 * brightness), 255, 120, 120));
                    RectF glowRect = new RectF(
                            cell.left + 2, cell.top + 2,
                            cell.right - 2, cell.centerY());
                    canvas.drawRoundRect(glowRect, cornerR * 0.4f, cornerR * 0.4f, glowPaint);
                }
            }
        }
    }

    private void drawCenterCircle(Canvas canvas) {
        float cx = centerCircle.centerX();
        float cy = centerCircle.centerY();
        float cr = centerCircle.width() / 2f;

        canvas.drawCircle(cx, cy, cr, circleBgPaint);
        canvas.drawCircle(cx, cy, cr, circleBorderPaint);

        Paint innerRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerRing.setColor(0xFF555555);
        innerRing.setStyle(Paint.Style.STROKE);
        innerRing.setStrokeWidth(dpToPx(0.8f));
        canvas.drawCircle(cx, cy, cr * 0.85f, innerRing);

        int activeIdx = ORB_ORDER[(int) (orbProgress % 4)];
        float phase   = orbProgress % 1.0f;

        for (int i = 0; i < 4; i++) {
            drawOrb(canvas, orbCx[i], orbCy[i], orbRadius,
                    i == activeIdx, phase);
        }
    }

    private void drawOrb(Canvas canvas, float cx, float cy, float r,
                         boolean active, float phase) {
        float baseBrightness   = 0.35f;
        float activeBrightness = 1.0f;
        float brightness = active
                ? (baseBrightness + (activeBrightness - baseBrightness)
                   * (float) Math.sin(phase * Math.PI))
                : baseBrightness;

        int g = (int) (80 + 175 * brightness);
        orbPaint.setColor(Color.rgb(0, g, (int)(20 * brightness)));
        orbPaint.setAlpha(220);
        canvas.drawCircle(cx, cy, r, orbPaint);

        if (active && brightness > 0.5f) {
            RadialGradient glow = new RadialGradient(
                    cx, cy - r * 0.3f, r * 0.7f,
                    new int[]{
                            Color.argb((int)(180 * brightness), 100, 255, 80),
                            Color.argb(0, 0, 180, 0)
                    },
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP
            );
            orbGlowPaint.setShader(glow);
            canvas.drawCircle(cx, cy, r, orbGlowPaint);
        }

        // Specular highlight
        Paint specPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        specPaint.setStyle(Paint.Style.FILL);
        specPaint.setColor(Color.argb(
                active ? (int)(200 * brightness) : 60,
                255, 255, 255));
        canvas.drawCircle(cx - r * 0.28f, cy - r * 0.3f, r * 0.2f, specPaint);
    }
}
