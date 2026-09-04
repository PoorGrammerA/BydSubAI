package com.poorgrammera.bydsubai.ui;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;


import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class KittScannerView extends View {

    private Paint bgPaint;
    private Paint borderPaint;
    private Paint scannerPaint;
    private ValueAnimator animator;
    private float progress = 0.0f;
    private final RectF rect = new RectF();
    private final RectF scannerRect = new RectF();

    public KittScannerView(Context context) {
        super(context);
        init();
    }

    public KittScannerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public KittScannerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xF00D0D0D); // Almost solid very dark gray/black
        bgPaint.setStyle(Paint.Style.FILL);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(0xFFE50914); // Glowing red border
        borderPaint.setAlpha(90); 
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(1.5f));

        scannerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scannerPaint.setStyle(Paint.Style.FILL);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    private void startAnimation() {
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0.0f, 1.0f);
            animator.setDuration(900); // 900ms sweep duration
            animator.setInterpolator(new LinearInterpolator());
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float stroke = borderPaint.getStrokeWidth();
        rect.set(stroke / 2f, stroke / 2f, w - stroke / 2f, h - stroke / 2f);
        float rx = h / 2f;
        float ry = h / 2f;

        // Draw background capsule
        canvas.drawRoundRect(rect, rx, ry, bgPaint);

        // Draw border
        canvas.drawRoundRect(rect, rx, ry, borderPaint);

        // Draw sweeping light capsule
        float lightWidth = w * 0.25f; // 25% of total scanner width
        float left = stroke + (w - 2 * stroke - lightWidth) * progress;
        float right = left + lightWidth;
        
        scannerRect.set(left, stroke, right, h - stroke);

        // Create neon red linear gradient with soft tail on both sides
        LinearGradient gradient = new LinearGradient(
                left, 0, right, 0,
                new int[]{0x00FF0D0D, 0xCCFF0D0D, 0xFFFF0D0D, 0xCCFF0D0D, 0x00FF0D0D},
                new float[]{0.0f, 0.25f, 0.5f, 0.75f, 1.0f},
                Shader.TileMode.CLAMP
        );
        scannerPaint.setShader(gradient);

        canvas.drawRoundRect(scannerRect, (h - 2 * stroke) / 2f, (h - 2 * stroke) / 2f, scannerPaint);
    }
}
