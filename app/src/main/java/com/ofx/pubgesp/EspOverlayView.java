package com.ofx.pubgesp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Transparent full-screen view that draws the ESP frame on top of the game. */
public final class EspOverlayView extends View {
    private volatile EspProtocol.Frame frame;
    private volatile int myTeam = Integer.MIN_VALUE;
    private volatile boolean drawBox = true;
    private volatile boolean drawName = true;
    private volatile boolean drawHealth = true;
    private volatile boolean drawDistance = true;
    private volatile boolean hideBots = false;
    private volatile boolean hideTeammates = true;
    private volatile int maxDistance = 600;

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint teammatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public EspOverlayView(Context c, AttributeSet a) {
        super(c, a);
        setWillNotDraw(false);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(2f);
        boxPaint.setColor(Color.rgb(255, 70, 70));
        teammatePaint.setStyle(Paint.Style.STROKE);
        teammatePaint.setStrokeWidth(2f);
        teammatePaint.setColor(Color.rgb(70, 220, 255));
        fillPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(26f);
        textPaint.setColor(Color.WHITE);
        textPaint.setShadowLayer(2f, 1f, 1f, Color.BLACK);
    }

    public void configure(EspProtocol.Frame f, int team, boolean box, boolean name,
                          boolean health, boolean distance, boolean noBots,
                          boolean noTeammates, int maxDist) {
        this.frame = f;
        this.myTeam = team;
        this.drawBox = box;
        this.drawName = name;
        this.drawHealth = health;
        this.drawDistance = distance;
        this.hideBots = noBots;
        this.hideTeammates = noTeammates;
        this.maxDistance = maxDist;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        EspProtocol.Frame f = frame;
        if (f == null || f.entries == null) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float textSize = Math.max(18f, h / 48f);
        textPaint.setTextSize(textSize);
        boxPaint.setStrokeWidth(Math.max(1.5f, w / 540f));
        teammatePaint.setStrokeWidth(Math.max(1.5f, w / 540f));

        for (EspProtocol.Entry e : f.entries) {
            if (e == null) continue;
            if (hideBots && e.bot) continue;
            boolean teammate = myTeam != Integer.MIN_VALUE && e.teamId == myTeam;
            if (hideTeammates && teammate) continue;

            float dist = EspProtocol.distance(f.view, e.x, e.y, e.z);
            if (dist > maxDistance || dist < 0f) continue;

            float[] feet = EspProtocol.worldToScreen(f.view, e.x, e.y, e.z, w, h);
            float[] head = EspProtocol.worldToScreen(f.view, e.x, e.y, e.headZ, w, h);
            if (feet == null || head == null) continue;
            if (feet[0] < -200 || feet[0] > w + 200 || feet[1] < -200 || feet[1] > h + 200) continue;

            float height = Math.abs(feet[1] - head[1]);
            if (height < 6f || height > h * 1.5f) continue;
            float width = height * 0.42f;
            float left = feet[0] - width / 2f;
            float right = feet[0] + width / 2f;
            float top = head[1];
            float bottom = feet[1];

            Paint active = teammate ? teammatePaint : boxPaint;
            if (drawBox) canvas.drawRect(left, top, right, bottom, active);

            float cy = top - 6f;
            if (drawName && e.name != null && !e.name.isEmpty()) {
                textPaint.setColor(teammate ? Color.rgb(90, 220, 255) : Color.WHITE);
                canvas.drawText(e.name, left, cy, textPaint);
                cy -= textSize + 4f;
            }
            if (drawHealth && e.health > 0f && e.health <= 200f) {
                float pct = Math.min(1f, e.health / 100f);
                float barTop = top - textSize - 2f;
                fillPaint.setColor(Color.rgb(60, 60, 60));
                canvas.drawRect(left, barTop, right, barTop + 5f, fillPaint);
                fillPaint.setColor(Color.rgb((int) (255 * (1f - pct)), (int) (255 * pct), 60));
                canvas.drawRect(left, barTop, left + (right - left) * pct, barTop + 5f, fillPaint);
            }
            if (drawDistance) {
                textPaint.setColor(Color.rgb(220, 220, 180));
                canvas.drawText(String.format(java.util.Locale.US, "%.0fm", dist),
                        right + 6f, (top + bottom) / 2f, textPaint);
            }
            if (!teammate && drawBox) {
                fillPaint.setColor(Color.rgb(255, 70, 70));
                canvas.drawRect(feet[0] - 2f, feet[1] - 2f, feet[0] + 2f, feet[1] + 2f, fillPaint);
            }
        }
    }
}
