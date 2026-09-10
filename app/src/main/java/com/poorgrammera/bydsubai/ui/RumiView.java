package com.poorgrammera.bydsubai.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.io.InputStream;

/**
 * RumiView — Codex Pet animated desktop companion view for Rumi.
 *
 * Spritesheet Geometry:
 * - 8 columns x 9 rows (total 72 potential frames)
 * - Single cell size: 192 x 208 px
 * - Total texture size: 1536 x 1872 px
 *
 * 9 Animation Rows:
 * 0: IDLE (6 frames)
 * 1: WALKING (8 frames)
 * 2: RUNNING (8 frames)
 * 3: JUMPING (4 frames)
 * 4: SITTING (5 frames)
 * 5: SLEEPING (8 frames)
 * 6: HAPPY (6 frames)
 * 7: WORKING / THINKING (6 frames)
 * 8: CELEBRATION (6 frames)
 */
public class RumiView extends View {

    private static final String TAG = "RumiView";
    private static final String SPRITESHEET_ASSET_PATH = "pets/rumi/spritesheet.webp";

    public static final int CELL_WIDTH = 192;
    public static final int CELL_HEIGHT = 208;
    public static final int GRID_COLS = 8;
    public static final int GRID_ROWS = 9;

    public enum State {
        IDLE(0, 6, 140),
        WALKING(1, 8, 120),
        RUNNING(2, 8, 90),
        JUMPING(3, 4, 120),
        SITTING(4, 5, 150),
        SLEEPING(5, 8, 220),
        HAPPY(6, 6, 120),
        WORKING(7, 6, 110),
        CELEBRATION(8, 6, 120);

        public final int row;
        public final int frameCount;
        public final int frameDurationMs;

        State(int row, int frameCount, int frameDurationMs) {
            this.row = row;
            this.frameCount = frameCount;
            this.frameDurationMs = frameDurationMs;
        }
    }

    private State currentState = State.IDLE;
    private int currentFrame = 0;

    private Bitmap spritesheet;
    private Paint bitmapPaint;
    private ValueAnimator animator;

    private final Rect srcRect = new Rect();
    private final RectF destRect = new RectF();

    private Runnable pendingStateRunnable;

    public RumiView(Context context) {
        super(context);
        init();
    }

    public RumiView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RumiView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        loadSpritesheet();
    }

    private void loadSpritesheet() {
        try (InputStream is = getContext().getAssets().open(SPRITESHEET_ASSET_PATH)) {
            spritesheet = BitmapFactory.decodeStream(is);
            Log.d(TAG, "Spritesheet loaded: " + (spritesheet != null ? spritesheet.getWidth() + "x" + spritesheet.getHeight() : "null"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load spritesheet from assets: " + SPRITESHEET_ASSET_PATH, e);
        }
    }

    public void setState(State state) {
        if (state == null || state == currentState) {
            return;
        }
        post(() -> {
            currentState = state;
            currentFrame = 0;
            if (animator != null && animator.isRunning()) {
                startAnimation();
            }
            invalidate();
        });
    }

    public State getState() {
        return currentState;
    }

    public static final State[] RANDOM_SPEAKING_STATES = {
            State.WALKING,
            State.RUNNING,
            State.JUMPING,
            State.SITTING,
            State.SLEEPING,
            State.WORKING,
            State.CELEBRATION
    };

    private static final java.util.Random RANDOM = new java.util.Random();

    /**
     * Randomly picks and plays one of the active speaking states:
     * WALKING, RUNNING, JUMPING, SITTING, SLEEPING, WORKING, CELEBRATION
     */
    public void playRandomSpeakingState() {
        State randomState = RANDOM_SPEAKING_STATES[RANDOM.nextInt(RANDOM_SPEAKING_STATES.length)];
        setState(randomState);
    }

    /**
     * Plays a temporary state (e.g. HAPPY or CELEBRATION) for a fixed duration, then reverts.
     */
    public void playTransientState(State transientState, long durationMs, State nextState) {
        if (pendingStateRunnable != null) {
            removeCallbacks(pendingStateRunnable);
        }
        setState(transientState);
        pendingStateRunnable = () -> setState(nextState != null ? nextState : State.IDLE);
        postDelayed(pendingStateRunnable, durationMs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (spritesheet == null || spritesheet.isRecycled()) {
            loadSpritesheet();
        }
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
        if (pendingStateRunnable != null) {
            removeCallbacks(pendingStateRunnable);
            pendingStateRunnable = null;
        }
        if (spritesheet != null && !spritesheet.isRecycled()) {
            spritesheet.recycle();
            spritesheet = null;
        }
    }

    private void startAnimation() {
        stopAnimation();
        int totalDuration = currentState.frameCount * currentState.frameDurationMs;
        animator = ValueAnimator.ofInt(0, currentState.frameCount);
        animator.setDuration(totalDuration);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            int val = (int) animation.getAnimatedValue();
            int frame = Math.min(val, currentState.frameCount - 1);
            if (frame != currentFrame) {
                currentFrame = frame;
                invalidate();
            }
        });
        animator.start();
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultW = dpToPx(120);
        int defaultH = dpToPx(130);
        int w = resolveSize(defaultW, widthMeasureSpec);
        int h = resolveSize(defaultH, heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    private int dpToPx(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (spritesheet == null || spritesheet.isRecycled()) {
            return;
        }

        int frame = Math.min(currentFrame, currentState.frameCount - 1);
        int srcX = frame * CELL_WIDTH;
        int srcY = currentState.row * CELL_HEIGHT;

        srcRect.set(srcX, srcY, srcX + CELL_WIDTH, srcY + CELL_HEIGHT);

        float viewW = getWidth();
        float viewH = getHeight();

        float scale = Math.min(viewW / CELL_WIDTH, viewH / CELL_HEIGHT);
        float drawW = CELL_WIDTH * scale;
        float drawH = CELL_HEIGHT * scale;

        float left = (viewW - drawW) / 2f;
        float top = (viewH - drawH) / 2f;

        destRect.set(left, top, left + drawW, top + drawH);

        canvas.drawBitmap(spritesheet, srcRect, destRect, bitmapPaint);
    }
}
