package sbhelper.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.util.List;

/**
 * HUD 위치·크기 정하기:
 *  - 상자를 끌면 옮기기 (가까운 화면 가장자리에 붙는다 → 줄이 늘어도 반대쪽으로 자라서 화면 밖으로 안 나감)
 *  - 노란 막대를 끌면 너비 (가장자리 반대쪽에 있음)
 *  - 상자 위에서 마우스 휠을 굴리면 크기, 오른쪽 클릭이면 크기·너비 기본으로
 *  - Esc 로 닫으면 저장
 */
public final class HudEditScreen extends Screen {
    private final Screen parent;
    private final Ui.Anim anim = new Ui.Anim();
    private final long openedAt = System.currentTimeMillis();
    /** 0 없음, 1 플래너 이동, 2 타이머 이동, 3 플래너 너비, 4 타이머 너비 */
    private int dragging = 0;
    private double offX, offY;
    /** 화면에 그린 자리 [x, y, 폭, 높이] */
    private int[] planRect = {0, 0, 0, 0}, timerRect = {0, 0, 0, 0};

    public HudEditScreen(Screen parent) { super(Component.literal("SkyblockHelper HUD")); this.parent = parent; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        float a = Ui.easeOut((System.currentTimeMillis() - openedAt) / 200f);
        g.fill(0, 0, this.width, this.height, Ui.alpha(0x50000000, a));
        // 가운데 안내선 (가장자리 기준이 바뀌는 곳)
        int line = Ui.alpha(0x18FFFFFF, a);
        g.fill(this.width / 2, 0, this.width / 2 + 1, this.height, line);
        g.fill(0, this.height / 2, this.width, this.height / 2 + 1, line);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        anim.frame();
        Config c = Config.get();
        planRect = Hud.drawAt(g, this.font, Hud.planLines(), c.planX, c.planY, c.planRight, c.planBottom, c.planScale, c.planWidth);
        timerRect = Hud.drawAt(g, this.font, Hud.timerLines(), c.timerX, c.timerY, c.timerRight, c.timerBottom, c.timerScale, c.timerWidth);
        frame(g, mx, my, "plan", "제작 플래너", planRect, c.planRight, c.planBottom, c.planScale, dragging == 1 || dragging == 3);
        frame(g, mx, my, "timer", "타이머", timerRect, c.timerRight, c.timerBottom, c.timerScale, dragging == 2 || dragging == 4);

        // 아래 안내
        String help = "끌어서 옮기기 · 노란 막대로 너비 · 휠로 크기 · 오른쪽 클릭으로 크기 초기화 · Esc 저장";
        int w = Math.min(this.width - 16, this.font.width(help) + 16);
        int x = (this.width - w) / 2, y = this.height - 22;
        Ui.round2(g, x, y, x + w, y + 16, 0xD0141820);
        g.fill(x + 2, y, x + w - 2, y + 1, Hud.ACCENT);
        g.text(this.font, Hud.fit(this.font, help, w - 12), x + 6, y + 5, 0xFFE6E8EC, false);
    }

    /** 상자 테두리 · 이름표 · 붙은 가장자리 표시 · 너비 막대 */
    private void frame(GuiGraphicsExtractor g, int mx, int my, String key, String name, int[] r, boolean right, boolean bottom, float scale, boolean active) {
        boolean hov = Ui.inside(mx, my, r[0], r[1], r[0] + r[2], r[1] + r[3]);
        float hv = anim.to("h:" + key, hov || active ? 1 : 0, 14);
        int border = Ui.mix(hv, 0x60FFFFFF, Hud.ACCENT);
        g.outline(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, border);
        // 붙은 모서리에 점
        int cx = right ? r[0] + r[2] : r[0] - 1, cy = bottom ? r[1] + r[3] : r[1] - 1;
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, Hud.ACCENT);
        // 너비 막대 (가장자리 반대쪽)
        int hx = right ? r[0] - 1 : r[0] + r[2] - 2;
        g.fill(hx, r[1], hx + 3, r[1] + r[3], Ui.mix(hv, 0xC0FFAA00, 0xFFFFE066));
        // 이름표: 위쪽 공간이 없으면 아래에
        String label = name + " · " + Math.round(scale * 100) + "% · " + (bottom ? "아래" : "위") + (right ? " 오른쪽" : " 왼쪽");
        int lw = this.font.width(label) + 8;
        int lx = Math.max(0, Math.min(right ? r[0] + r[2] - lw : r[0], this.width - lw));
        int ly = r[1] - 13 >= 0 ? r[1] - 13 : r[1] + r[3] + 2;
        if (hv > 0.02f) {
            Ui.round(g, lx, ly, lx + lw, ly + 11, Ui.alpha(0xE0141820, hv));
            int tc = Ui.alpha(0xFFFFFFFF, hv);
            if (ARGB.alpha(tc) >= 8) g.text(this.font, label, lx + 4, ly + 2, tc, false);
        }
    }

    private static boolean onHandle(double mx, double my, int[] r, boolean right) {
        int hx = right ? r[0] - 1 : r[0] + r[2] - 2;
        return mx >= hx - 2 && mx <= hx + 4 && my >= r[1] && my <= r[1] + r[3];
    }

    private static boolean in(double mx, double my, int[] r) { return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]; }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        Config c = Config.get();
        if (e.button() == 1) {
            if (in(e.x(), e.y(), timerRect)) { c.timerScale = 1f; c.timerWidth = 0; Ui.click(); return true; }
            if (in(e.x(), e.y(), planRect)) { c.planScale = 1f; c.planWidth = 0; Ui.click(); return true; }
            return super.mouseClicked(e, doubleClick);
        }
        if (onHandle(e.x(), e.y(), timerRect, c.timerRight)) { dragging = 4; return true; }
        if (onHandle(e.x(), e.y(), planRect, c.planRight)) { dragging = 3; return true; }
        if (in(e.x(), e.y(), timerRect)) { dragging = 2; offX = e.x() - timerRect[0]; offY = e.y() - timerRect[1]; return true; }
        if (in(e.x(), e.y(), planRect)) { dragging = 1; offX = e.x() - planRect[0]; offY = e.y() - planRect[1]; return true; }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        Config c = Config.get();
        switch (dragging) {
            case 1 -> {
                int[] p = anchor((int) (e.x() - offX), (int) (e.y() - offY), planRect[2], planRect[3]);
                c.planX = p[0]; c.planY = p[1]; c.planRight = p[2] == 1; c.planBottom = p[3] == 1;
                return true;
            }
            case 2 -> {
                int[] p = anchor((int) (e.x() - offX), (int) (e.y() - offY), timerRect[2], timerRect[3]);
                c.timerX = p[0]; c.timerY = p[1]; c.timerRight = p[2] == 1; c.timerBottom = p[3] == 1;
                return true;
            }
            case 3 -> { c.planWidth = width(e.x(), planRect, c.planRight, c.planScale); return true; }
            case 4 -> { c.timerWidth = width(e.x(), timerRect, c.timerRight, c.timerScale); return true; }
            default -> { return super.mouseDragged(e, dx, dy); }
        }
    }

    /** 화면 좌표 → [가장자리에서 x, y, 오른쪽?, 아래?]. 상자 가운데가 있는 쪽 가장자리에 붙인다 (4칸 안이면 딱 붙임) */
    private int[] anchor(int sx, int sy, int w, int h) {
        sx = Math.max(0, Math.min(sx, this.width - w));
        sy = Math.max(0, Math.min(sy, this.height - h));
        boolean right = sx + w / 2 > this.width / 2, bottom = sy + h / 2 > this.height / 2;
        int x = right ? this.width - sx - w : sx, y = bottom ? this.height - sy - h : sy;
        if (x < 6) x = 4;
        if (y < 6) y = 4;
        return new int[]{x, y, right ? 1 : 0, bottom ? 1 : 0};
    }

    /** 너비 막대 위치 → 너비 (배율 적용 전). 오른쪽에 붙었으면 왼쪽으로 늘어남 */
    private static int width(double mx, int[] r, boolean right, float scale) {
        double px = right ? (r[0] + r[2]) - mx : mx - r[0];
        return (int) Math.max(80, Math.min(500, px / scale));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging != 0) Config.save();
        dragging = 0;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        Config c = Config.get();
        float step = scrollY > 0 ? 0.1f : -0.1f;
        if (in(mx, my, timerRect)) { c.timerScale = clamp(c.timerScale + step); return true; }
        if (in(mx, my, planRect)) { c.planScale = clamp(c.planScale + step); return true; }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private static float clamp(float s) { return Math.round(Math.max(0.5f, Math.min(3f, s)) * 10) / 10f; }

    @Override
    public void onClose() {
        Config.save();
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
