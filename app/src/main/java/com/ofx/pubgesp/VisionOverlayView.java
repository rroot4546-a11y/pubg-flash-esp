package com.ofx.pubgesp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

/** Transparent view that draws person detections from the screen-capture engine. */
public final class VisionOverlayView extends View {
    /** One detection box, in screen pixels. */
    public static final class Box {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        public final int id;
        public final int color;
        public final String tag;

        public Box(float left, float top, float right, float bottom, int id,
                   int color, String tag) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.id = id;
            this.color = color;
            this.tag = tag;
        }
    }

    private volatile List<Box> boxes;
    private volatile String status = "";
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);

    public VisionOverlayView(Context c, AttributeSet a) {
        super(c, a);
        setWillNotDraw(false);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(Color.rgb(255, 64, 64));
        stroke.setStrokeWidth(2f);
        labelBg.setStyle(Paint.Style.FILL);
        labelBg.setColor(Color.argb(190, 10, 10, 10));
        label.setTextSize(20f);
        label.setColor(Color.WHITE);
    }

    public void setBoxes(List<Box> detected) {
        boxes = detected;
        postInvalidate();
    }

    public void setStatus(String text) {
        status = text == null ? "" : text;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        String line = status;
        if (!line.isEmpty()) {
            float size = Math.max(16f, getHeight() / 48f);
            label.setTextSize(size);
            float tw = label.measureText(line);
            canvas.drawRect(4f, 4f, tw + 18f, size + 14f, labelBg);
            canvas.drawText(line, 10f, size + 8f, label);
        }
        List<Box> current = boxes;
        if (current == null || current.isEmpty()) return;
        float w = getWidth();
        float h = getHeight();
        if (w == 0f || h == 0f) return;

        stroke.setStrokeWidth(Math.max(1.5f, w / 540f));
        label.setTextSize(Math.max(14f, h / 52f));
        int index = 0;
        for (Box b : current) {
            if (b == null) continue;
            float left = Math.max(0f, Math.min(w, b.left));
            float top = Math.max(0f, Math.min(h, b.top));
            float right = Math.max(0f, Math.min(w, b.right));
            float bottom = Math.max(0f, Math.min(h, b.bottom));
            if (right - left < 8f || bottom - top < 12f) continue;
            stroke.setColor(b.color);
            canvas.drawRect(left, top, right, bottom, stroke);
            String text = b.tag != null ? b.tag : "P" + (b.id >= 0 ? b.id : index);
            float tw = label.measureText(text);
            canvas.drawRect(left, Math.max(0f, top - 24f), left + tw + 10f, top, labelBg);
            canvas.drawText(text, left + 5f, Math.max(16f, top - 5f), label);
            index++;
        }
    }
}
