package com.droidx.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

public class SplitFlipCardView extends FrameLayout {

    private HalfCardView topStatic, bottomStatic;
    private HalfCardView topFlap, bottomFlap;
    private View vSeam;

    private String currentValue = "00";
    private int textColor = Color.WHITE;
    private float textSizePx;

    private int cardWidth = 0;
    private int cardHeight = 0;
    private int halfHeight = 0;
    private int topH = 0;
    private int bottomTop = 0;
    private int seamHeight = 2; // px

    public SplitFlipCardView(Context context) {
        super(context);
        init(context);
    }

    public SplitFlipCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SplitFlipCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void setTextColor(int color) {
        this.textColor = color;
        if (topStatic != null) topStatic.setTextColor(color);
        if (bottomStatic != null) bottomStatic.setTextColor(color);
        if (topFlap != null) topFlap.setTextColor(color);
        if (bottomFlap != null) bottomFlap.setTextColor(color);
    }

    private void init(Context context) {
        float density = getResources().getDisplayMetrics().density;
        // 60sp font size provides clean, uncrowded spacing inside 100x130dp cards
        textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 60, getResources().getDisplayMetrics());
        seamHeight = Math.max(2, (int) (2 * density));

        setClipChildren(false);
        setClipToPadding(false);

        // 1. Top Static (reveals upcoming value top half)
        topStatic = new HalfCardView(context, true, R.drawable.bg_flip_card_top);
        addView(topStatic);

        // 2. Bottom Static (shows current value bottom half)
        bottomStatic = new HalfCardView(context, false, R.drawable.bg_flip_card_bottom);
        addView(bottomStatic);

        // 3. Bottom Flap (unfolds downwards from -90 to 0)
        bottomFlap = new HalfCardView(context, false, R.drawable.bg_flip_card_bottom);
        bottomFlap.setVisibility(View.GONE);
        bottomFlap.setCameraDistance(density * 8000);
        bottomFlap.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        addView(bottomFlap);

        // 4. Top Flap (folds down from 0 to 90)
        topFlap = new HalfCardView(context, true, R.drawable.bg_flip_card_top);
        topFlap.setCameraDistance(density * 8000);
        topFlap.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        addView(topFlap);

        // 5. Center Split Seam Line
        vSeam = new View(context);
        vSeam.setBackgroundColor(Color.parseColor("#0D0D10"));
        addView(vSeam);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int w = getMeasuredWidth();
        int h = getMeasuredHeight();
        if (w > 0 && h > 0) {
            cardWidth = w;
            cardHeight = h;
            halfHeight = h / 2;
            topH = halfHeight - (seamHeight / 2);
            bottomTop = halfHeight + (seamHeight / 2);
            int bottomH = h - bottomTop;

            int widthSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
            int topSpec = MeasureSpec.makeMeasureSpec(topH, MeasureSpec.EXACTLY);
            int bottomSpec = MeasureSpec.makeMeasureSpec(bottomH, MeasureSpec.EXACTLY);

            topStatic.measure(widthSpec, topSpec);
            topFlap.measure(widthSpec, topSpec);
            bottomStatic.measure(widthSpec, bottomSpec);
            bottomFlap.measure(widthSpec, bottomSpec);
            vSeam.measure(widthSpec, MeasureSpec.makeMeasureSpec(seamHeight, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int w = right - left;
        int h = bottom - top;
        if (w <= 0 || h <= 0) return;

        cardWidth = w;
        cardHeight = h;
        halfHeight = h / 2;
        topH = halfHeight - (seamHeight / 2);
        bottomTop = halfHeight + (seamHeight / 2);
        int bottomH = h - bottomTop;

        // Top Static & Top Flap (top edge = 0, bottom edge = topH)
        topStatic.layout(0, 0, w, topH);
        topFlap.layout(0, 0, w, topH);
        topFlap.setPivotX(w / 2f);
        topFlap.setPivotY(topH); // Hinge anchored exactly at the center seam!

        // Bottom Static & Bottom Flap (top edge = bottomTop, bottom edge = h)
        bottomStatic.layout(0, bottomTop, w, bottomTop + bottomH);
        bottomFlap.layout(0, bottomTop, w, bottomTop + bottomH);
        bottomFlap.setPivotX(w / 2f);
        bottomFlap.setPivotY(0f); // Hinge anchored exactly at the center seam!

        // Center Seam Line
        vSeam.layout(0, topH, w, bottomTop);
    }

    public void setValue(String value) {
        if (value == null) return;
        this.currentValue = value;

        topFlap.animate().cancel();
        bottomFlap.animate().cancel();

        topFlap.setRotationX(0f);
        topFlap.setVisibility(View.VISIBLE);
        bottomFlap.setVisibility(View.GONE);

        topStatic.setText(value);
        bottomStatic.setText(value);
        topFlap.setText(value);
        bottomFlap.setText(value);
    }

    public void flipTo(final String newValue, final long durationHalf) {
        if (newValue == null || newValue.equals(currentValue)) return;
        final String oldValue = currentValue;
        this.currentValue = newValue;

        topFlap.animate().cancel();
        bottomFlap.animate().cancel();

        // 1. Prepare static backgrounds
        topStatic.setText(newValue);
        bottomStatic.setText(oldValue);

        // 2. Prepare top flap (old value top half falling down)
        topFlap.setText(oldValue);
        topFlap.setRotationX(0f);
        topFlap.setVisibility(View.VISIBLE);

        // 3. Prepare bottom flap (new value bottom half waiting at -90 deg to unfold)
        bottomFlap.setText(newValue);
        bottomFlap.setRotationX(-90f);
        bottomFlap.setVisibility(View.GONE);

        // 4. Smooth linear fold down from 0 to 90 deg (calm, steady, no whip)
        topFlap.animate()
                .rotationX(90f)
                .setDuration(durationHalf)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(() -> {
                    topFlap.setVisibility(View.GONE);
                    bottomFlap.setVisibility(View.VISIBLE);

                    // 5. Smooth linear unfold from -90 to 0 deg
                    bottomFlap.animate()
                            .rotationX(0f)
                            .setDuration(durationHalf)
                            .setInterpolator(new LinearInterpolator())
                            .withEndAction(() -> {
                                bottomStatic.setText(newValue);
                                bottomFlap.setVisibility(View.GONE);

                                topFlap.setRotationX(0f);
                                topFlap.setText(newValue);
                                topFlap.setVisibility(View.VISIBLE);
                            })
                            .start();
                })
                .start();
    }

    // --- INNER CUSTOM VIEW: PHYSICALLY CLIPPED HALF CARD ---
    private class HalfCardView extends View {
        private final boolean isTop;
        private final Drawable bgDrawable;
        private final Paint textPaint;
        private String text = "00";

        public HalfCardView(Context context, boolean isTop, int bgRes) {
            super(context);
            this.isTop = isTop;
            this.bgDrawable = getResources().getDrawable(bgRes);

            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(textColor);
            textPaint.setTextSize(textSizePx);
            textPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        public void setText(String text) {
            if (text == null) return;
            if (!this.text.equals(text)) {
                this.text = text;
                invalidate();
            }
        }

        public void setTextColor(int color) {
            textPaint.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0 || cardHeight <= 0) return;

            // STRICT CANVAS CLIPPING: Physically impossible for any pixel to draw outside [0, 0, w, h]
            canvas.save();
            canvas.clipRect(0, 0, w, h);

            // Draw half-card background (rounded outer corners, flat inner meeting edge)
            bgDrawable.setBounds(0, 0, w, h);
            bgDrawable.draw(canvas);

            // Compute baseline so text is centered across the FULL card height
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float fontCenterOffset = (fm.descent + fm.ascent) / 2f;
            float fullBaseline = (cardHeight / 2f) - fontCenterOffset;

            if (isTop) {
                // Top half: draws upper part of text
                canvas.drawText(text, w / 2f, fullBaseline, textPaint);
            } else {
                // Bottom half: translate up by bottomTop so only lower part of text is drawn
                canvas.translate(0, -bottomTop);
                canvas.drawText(text, w / 2f, fullBaseline, textPaint);
            }

            canvas.restore();
        }
    }
}
