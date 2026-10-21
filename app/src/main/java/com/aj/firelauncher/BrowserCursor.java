package com.aj.firelauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/** D-pad mouse with frame-based acceleration and native wheel hit testing, informed by mrowser. */
final class BrowserCursor extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private View target;
    private float x, y;
    private int dx, dy;
    private long holdStart, lastFrame;
    private boolean moving;
    private boolean scrollMode;
    private final float density;
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!moving || target == null) return;
            long now = SystemClock.uptimeMillis();
            float elapsed = Math.min(32, Math.max(1, now - lastFrame)) / 16f;
            lastFrame = now;
            float speed = speedForHold(now - holdStart) * density * elapsed;
            if (scrollMode) wheel(-dx * 0.13f * elapsed, -dy * 0.13f * elapsed);
            else {
                x = clamp(x + dx * speed, getWidth());
                y = clamp(y + dy * speed, getHeight());
                hover();
                float zone = 28 * density;
                if (dy < 0 && y <= zone) wheel(0, 0.13f * elapsed);
                if (dy > 0 && y >= getHeight() - zone) wheel(0, -0.13f * elapsed);
                invalidate();
            }
            postDelayed(this, 16);
        }
    };

    BrowserCursor(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
        setFocusable(false);
        fill.setColor(Color.rgb(141,219,255));
        outline.setColor(Color.BLACK);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(1.5f * density);
    }

    void setTarget(View target) { stop(); this.target = target; }
    void setScrollMode(boolean value) { stop(); scrollMode = value; invalidate(); }
    boolean isScrollMode() { return scrollMode; }
    float cursorX() { return x; }
    float cursorY() { return y; }
    static float speedForHold(long ms) { return 3f + 10f * Math.max(0f, Math.min(1f, ms / 900f)); }
    private static float clamp(float value, int size) { return Math.max(0, Math.min(value, Math.max(0,size - 1))); }

    boolean direction(KeyEvent event) {
        int horizontal = event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT ? -1
                : event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        int vertical = event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP ? -1
                : event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
        if (horizontal == 0 && vertical == 0) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() > 0) return true;
            if (horizontal != 0) dx = horizontal;
            if (vertical != 0) dy = vertical;
            if (!moving) {
                moving = true;
                holdStart = SystemClock.uptimeMillis();
                lastFrame = holdStart - 16;
                frame.run();
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (horizontal == dx) dx = 0;
            if (vertical == dy) dy = 0;
            if (dx == 0 && dy == 0) stop();
        }
        return true;
    }

    void stop() { moving = false; dx = dy = 0; removeCallbacks(frame); }

    void click() {
        if (target == null) return;
        hover();
        long now = SystemClock.uptimeMillis();
        send(MotionEvent.ACTION_DOWN, now, now, MotionEvent.BUTTON_PRIMARY, 0, 0);
        send(MotionEvent.ACTION_UP, now, now + 40, 0, 0, 0);
    }

    private void hover() { long t = SystemClock.uptimeMillis(); send(MotionEvent.ACTION_HOVER_MOVE,t,t,0,0,0); }
    private void wheel(float horizontal, float vertical) {
        hover(); long t = SystemClock.uptimeMillis();
        send(MotionEvent.ACTION_SCROLL,t,t,0,horizontal,vertical);
    }
    private void send(int action, long down, long time, int buttons, float horizontal, float vertical) {
        if (target == null || getWidth() == 0) return;
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x * target.getWidth() / getWidth();
        coords.y = y * target.getHeight() / getHeight();
        coords.pressure = action == MotionEvent.ACTION_DOWN ? 1f : 0f;
        coords.size = 1;
        coords.setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal);
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, vertical);
        MotionEvent event = MotionEvent.obtain(down,time,action,1,
                new MotionEvent.PointerProperties[]{properties},new MotionEvent.PointerCoords[]{coords},
                0,buttons,1,1,0,0,InputDevice.SOURCE_MOUSE,0);
        if (action == MotionEvent.ACTION_SCROLL || action == MotionEvent.ACTION_HOVER_MOVE)
            target.dispatchGenericMotionEvent(event);
        else target.dispatchTouchEvent(event);
        event.recycle();
    }

    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        if (oldw == 0 || oldh == 0) { x = w / 2f; y = h / 2f; }
        else { x = clamp(x,w); y = clamp(y,h); }
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(x,y);
        canvas.scale(x > getWidth() - 20*density ? -density : density,
                y > getHeight() - 24*density ? -density : density);
        Path arrow = new Path();
        arrow.moveTo(0,0); arrow.lineTo(0,18); arrow.lineTo(5,13);
        arrow.lineTo(9,21); arrow.lineTo(12,19); arrow.lineTo(8,12);
        arrow.lineTo(15,12); arrow.close();
        canvas.drawPath(arrow,fill);
        canvas.drawPath(arrow,outline);
        canvas.restore();
    }
}
