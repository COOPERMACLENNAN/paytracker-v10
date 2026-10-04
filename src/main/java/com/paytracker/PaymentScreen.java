package com.paytracker;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small purple/black panel in the top-left corner. */
public class PaymentScreen extends Screen {
    // layout
    private static final float SCALE = 0.75f; // overall GUI size: lower = smaller (try 0.6 - 1.0)
    private static final int PX = 6, PY = 6, PW = 204, PH = 158;
    private static final int FIELD_Y = PY + 20;
    private static final int MIN_BOX_X = PX + 26, MAX_BOX_X = PX + 112, BOX_W = 58;
    private static final int SET_X = PX + 176;
    private static final int LIST_Y = PY + 59, ROW_H = 14, ROWS = 6;
    private static final int COL_AMT = PX + 78, COL_TIME = PX + 118;
    private static final int X_BOX = PX + PW - 17;
    private static final int RADIUS = 7;   // panel corner roundness (bigger = rounder)
    private static final int GLOW = 7;     // glow size in pixels
    private static final int GLOW_LAYER = 0x169B30FF; // purple, low alpha, stacked per layer

    // purple / black theme
    private static final int C_BG = 0xFF070010;
    private static final int C_HEADER = 0xFF2E0A52;
    private static final int C_BORDER = 0xFF9B30FF;
    private static final int C_TITLE = 0xFFE9D5FF;
    private static final int C_LABEL = 0xFFA78BFA;
    private static final int C_DIM = 0xFF8472A8;
    private static final int C_PLAYER = 0xFFFFFFFF;
    private static final int C_AMOUNT = 0xFFD8B4FE;
    private static final int C_TIME = 0xFF9F7AEA;
    private static final int C_ROW_A = 0xFF0D0518;
    private static final int C_ROW_HOVER = 0xFF2A1250;
    private static final int C_BTN = 0xFF5B21B6;
    private static final int C_BTN_HOVER = 0xFF7C3AED;
    private static final int C_X = 0xFFFF5C8A;
    private static final int C_X_HOVER = 0xFFFFA0BC;
    private static final int C_OK = 0xFFC084FC;
    private static final int C_ERR = 0xFFFF6B8B;

    private TextFieldWidget minField, maxField;
    private int scroll = 0;
    private String status = "";
    private int statusColor = C_DIM;
    private long clearArmedUntil = 0;
    private int dx = 0, dy = 0;          // panel offset (drag to move)
    private boolean dragging = false;
    private double grabX, grabY;

    public PaymentScreen() {
        super(Text.literal("Payment Tracker"));
        dx = TrackerData.INSTANCE.guiX;
        dy = TrackerData.INSTANCE.guiY;
    }

    @Override
    protected void init() {
        TrackerData d = TrackerData.INSTANCE;
        minField = makeField(MIN_BOX_X, Money.format(d.threshold), null);
        maxField = makeField(MAX_BOX_X, d.maximum > 0 ? Money.format(d.maximum) : "", "none");
        addDrawableChild(minField);
        addDrawableChild(maxField);
    }

    private TextFieldWidget makeField(int boxX, String text, String hint) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, boxX + 3, FIELD_Y + 3, BOX_W - 6, 10, Text.empty());
        f.setDrawsBackground(false);
        f.setMaxLength(20);
        f.setEditableColor(0xFFFFFFFF);
        if (hint != null) {
            f.setChangedListener(s -> f.setSuggestion(s.isEmpty() ? hint : null));
        }
        f.setText(text);
        if (hint != null && text.isEmpty()) f.setSuggestion(hint);
        return f;
    }

    // ---------- helpers ----------

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void text(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawText(textRenderer, s, x, y, color, true);
    }

    private List<TrackerData.Entry> view() {
        List<TrackerData.Entry> v = new ArrayList<>(TrackerData.INSTANCE.entries);
        Collections.reverse(v); // newest first
        return v;
    }

    private static int clampScroll(int scroll, int size) {
        return Math.max(0, Math.min(scroll, Math.max(0, size - ROWS)));
    }

    private static String range(TrackerData d) {
        return "$" + Money.format(d.threshold) + (d.maximum > 0 ? " - $" + Money.format(d.maximum) : "+");
    }

    private void setStatus(String s, int color) {
        status = s;
        statusColor = color;
    }

    private boolean clearArmed() {
        return System.currentTimeMillis() < clearArmedUntil;
    }

    private String clearLabel() {
        return clearArmed() ? "sure?" : "clear";
    }

    private void drawBox(DrawContext ctx, int x, boolean focused) {
        roundRect(ctx, x, FIELD_Y, BOX_W, 14, 3, focused ? 0xFFC084FC : 0xFF6D28D9);
        roundRect(ctx, x + 1, FIELD_Y + 1, BOX_W - 2, 12, 2, 0xFF000000);
    }

    private void apply() {
        TrackerData d = TrackerData.INSTANCE;
        Double min = Money.parse(minField.getText());
        if (min == null) {
            setStatus("Invalid minimum (try 25m)", C_ERR);
            return;
        }
        double max = 0;
        String mt = maxField.getText().trim();
        if (!mt.isEmpty() && !mt.equalsIgnoreCase("none")) {
            Double m = Money.parse(mt);
            if (m == null) {
                setStatus("Invalid maximum (try 100m)", C_ERR);
                return;
            }
            max = m;
        }
        if (max > 0 && max < min) {
            setStatus("Max must be above min", C_ERR);
            return;
        }
        d.threshold = min;
        d.maximum = max;
        TrackerData.save();
        minField.setText(Money.format(min));
        maxField.setText(max > 0 ? Money.format(max) : "");
        setStatus("Saved: " + range(d), C_OK);
    }

    // ---------- rounded drawing helpers ----------

    private static void roundRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        ctx.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * (double) r - d * d));
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            ctx.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /** Only the top corners are rounded (used for the header bar). */
    private static void roundRectTop(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w / 2, h)));
        ctx.fill(x, y + r, x + w, y + h, color);
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * (double) r - d * d));
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }

    // ---------- rendering ----------

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // no dimming / blur: the world stays visible behind the small panel
    }

    @Override
    public void render(DrawContext ctx, int rawMx, int rawMy, float delta) {
        clampPos();
        int mx = (int) ((rawMx - dx) / SCALE);
        int my = (int) ((rawMy - dy) / SCALE);
        ctx.getMatrices().push();
        ctx.getMatrices().translate((float) dx, (float) dy, 0f);
        ctx.getMatrices().scale(SCALE, SCALE, 1f);

        TrackerData d = TrackerData.INSTANCE;
        List<TrackerData.Entry> view = view();
        scroll = clampScroll(scroll, view.size());

        // glow: stacked translucent rounded rects, outermost first (gets brighter toward the panel)
        for (int g = GLOW; g >= 1; g--) {
            roundRect(ctx, PX - g, PY - g, PW + 2 * g, PH + 2 * g, RADIUS + g, GLOW_LAYER);
        }
        // panel: rounded border, then rounded body inside it
        roundRect(ctx, PX, PY, PW, PH, RADIUS, C_BORDER);
        roundRect(ctx, PX + 1, PY + 1, PW - 2, PH - 2, RADIUS - 1, C_BG);
        roundRectTop(ctx, PX + 1, PY + 1, PW - 2, 15, RADIUS - 1, C_HEADER);
        ctx.fill(PX + 1, PY + 16, PX + PW - 1, PY + 17, C_BORDER);
        text(ctx, "Payment Tracker", PX + 6, PY + 4, C_TITLE);

        // clear-all (click twice)
        String cl = clearLabel();
        int cw = textRenderer.getWidth(cl);
        int cx = PX + PW - 6 - cw;
        boolean cHover = in(mx, my, cx - 2, PY + 2, cw + 4, 12);
        text(ctx, cl, cx, PY + 4, clearArmed() ? C_ERR : (cHover ? C_TITLE : C_DIM));

        // min / max
        text(ctx, "Min", PX + 6, FIELD_Y + 3, C_LABEL);
        text(ctx, "Max", PX + 90, FIELD_Y + 3, C_LABEL);
        drawBox(ctx, MIN_BOX_X, minField.isFocused());
        drawBox(ctx, MAX_BOX_X, maxField.isFocused());
        boolean sHover = in(mx, my, SET_X, FIELD_Y, 24, 14);
        roundRect(ctx, SET_X, FIELD_Y, 24, 14, 3, sHover ? C_BTN_HOVER : C_BTN);
        ctx.drawCenteredTextWithShadow(textRenderer, "Set", SET_X + 12, FIELD_Y + 3, 0xFFFFFFFF);

        // status line
        String s = status.isEmpty() ? "Tracking " + range(d) : status;
        int sc = status.isEmpty() ? C_DIM : statusColor;
        text(ctx, textRenderer.trimToWidth(s, PW - 12), PX + 6, PY + 37, sc);

        // column headers
        text(ctx, "Player", PX + 6, PY + 49, C_LABEL);
        text(ctx, "Amount", COL_AMT, PY + 49, C_LABEL);
        text(ctx, "Sent", COL_TIME, PY + 49, C_LABEL);
        ctx.fill(PX + 4, LIST_Y - 2, PX + PW - 4, LIST_Y - 1, 0xFF4C1D95);

        // rows
        if (view.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No payments logged yet", PX + PW / 2, LIST_Y + 10, C_DIM);
        }
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            if (idx >= view.size()) break;
            TrackerData.Entry e = view.get(idx);
            int y = LIST_Y + i * ROW_H;

            boolean hover = in(mx, my, PX + 3, y, PW - 6, ROW_H);
            if (hover) roundRect(ctx, PX + 3, y, PW - 6, ROW_H, 3, C_ROW_HOVER);
            else if ((idx & 1) == 0) roundRect(ctx, PX + 3, y, PW - 6, ROW_H, 3, C_ROW_A);

            int ty = y + 3;
            text(ctx, textRenderer.trimToWidth(e.player, 68), PX + 6, ty, C_PLAYER);
            text(ctx, "$" + Money.format(e.amount), COL_AMT, ty, C_AMOUNT);
            text(ctx, Money.ago(e.time), COL_TIME, ty, C_TIME);

            boolean xHover = in(mx, my, X_BOX, y + 1, 12, 12);
            ctx.drawCenteredTextWithShadow(textRenderer, "x", X_BOX + 6, ty, xHover ? C_X_HOVER : C_X);
        }

        // footer
        text(ctx, "RShift / Esc: close", PX + 6, PY + 146, C_DIM);
        String count = view.size() + " logged";
        text(ctx, count, PX + PW - 6 - textRenderer.getWidth(count), PY + 146, C_DIM);

        super.render(ctx, mx, my, delta); // text fields on top
        ctx.getMatrices().pop();
    }

    // ---------- input ----------

    @Override
    public boolean mouseClicked(double rawX, double rawY, int button) {
        double mx = (rawX - dx) / SCALE;
        double my = (rawY - dy) / SCALE;

        // right-click the header = put the panel back in the corner
        if (button == 1 && in(mx, my, PX, PY, PW, 16)) {
            dx = 0;
            dy = 0;
            savePos();
            return true;
        }

        if (button != 0) return super.mouseClicked(mx, my, button);

        // clear all (needs a second click within 3s)
        int cw = textRenderer.getWidth(clearLabel());
        int cx = PX + PW - 6 - cw;
        if (in(mx, my, cx - 2, PY + 2, cw + 4, 12)) {
            if (clearArmed()) {
                TrackerData.INSTANCE.entries.clear();
                TrackerData.save();
                clearArmedUntil = 0;
                scroll = 0;
                setStatus("Log cleared", C_OK);
            } else {
                clearArmedUntil = System.currentTimeMillis() + 3000;
            }
            return true;
        }

        // drag the panel by its header
        if (in(mx, my, PX, PY, PW, 16)) {
            dragging = true;
            grabX = rawX - dx;
            grabY = rawY - dy;
            return true;
        }

        // set button
        if (in(mx, my, SET_X, FIELD_Y, 24, 14)) {
            apply();
            return true;
        }

        // delete a single entry with its x
        List<TrackerData.Entry> view = view();
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            if (idx >= view.size()) break;
            int y = LIST_Y + i * ROW_H;
            if (in(mx, my, X_BOX, y + 1, 12, 12)) {
                TrackerData.INSTANCE.entries.remove(view.get(idx));
                TrackerData.save();
                return true;
            }
        }

        // focus a text box (whole drawn box is clickable)
        for (TextFieldWidget f : new TextFieldWidget[]{minField, maxField}) {
            if (in(mx, my, f.getX() - 3, FIELD_Y, BOX_W, 14)) {
                setFocused(f);
                f.mouseClicked(f.getX() + 1, f.getY() + 1, button);
                return true;
            }
        }

        setFocused(null);
        return true;
    }

    private void clampPos() {
        int pw = Math.round(PW * SCALE), ph = Math.round(PH * SCALE);
        int baseX = Math.round(PX * SCALE), baseY = Math.round(PY * SCALE);
        dx = Math.max(-baseX, Math.min(dx, width - pw - baseX));
        dy = Math.max(-baseY, Math.min(dy, height - ph - baseY));
    }

    private void savePos() {
        TrackerData.INSTANCE.guiX = dx;
        TrackerData.INSTANCE.guiY = dy;
        TrackerData.save();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == 0) {
            dx = (int) (mouseX - grabX);
            dy = (int) (mouseY - grabY);
            clampPos();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            savePos();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scroll = clampScroll(scroll - (int) Math.signum(vertical), view().size());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && (minField.isFocused() || maxField.isFocused())) {
            apply();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
