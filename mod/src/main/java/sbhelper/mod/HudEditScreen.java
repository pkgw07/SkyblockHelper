package sbhelper.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * HUD 위치·크기 정하기:
 *  - 상자를 끌면 옮기기
 *  - 상자 오른쪽 끝(노란 막대)을 끌면 너비
 *  - 상자 위에서 마우스 휠을 굴리면 크기
 *  - Esc 로 닫으면 저장
 */
public final class HudEditScreen extends Screen {
    private final Screen parent;
    private int dragging = 0; // 1 플래너 이동, 2 타이머 이동, 3 플래너 너비, 4 타이머 너비
    private double offX, offY;
    private int[] planSize = {100, 20}, timerSize = {100, 20};

    public HudEditScreen(Screen parent) { super(Component.literal("SkyblockHelper HUD")); this.parent = parent; }

    private int timerX() { Config c = Config.get(); return c.timerX < 0 ? this.width - timerSize[0] - 4 : c.timerX; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Config c = Config.get();
        planSize = Hud.drawScaled(g, this.font, c.planX, c.planY, c.planScale, Hud.planLines(), c.planWidth);
        timerSize = Hud.drawScaled(g, this.font, timerX(), c.timerY, c.timerScale, Hud.timerLines(), c.timerWidth);
        // 너비 손잡이
        g.fill(c.planX + planSize[0] - 3, c.planY, c.planX + planSize[0], c.planY + planSize[1], Hud.GOLD);
        g.fill(timerX() + timerSize[0] - 3, c.timerY, timerX() + timerSize[0], c.timerY + timerSize[1], Hud.GOLD);
        g.centeredText(this.font, "끌어서 옮기기 · 노란 막대를 끌어서 너비 · 마우스 휠로 크기 · Esc 로 저장", this.width / 2, this.height - 30, Hud.WHITE);
        g.centeredText(this.font, "크기: 제작 플래너 " + Math.round(c.planScale * 100) + "% · 타이머 " + Math.round(c.timerScale * 100) + "%",
            this.width / 2, this.height - 18, Hud.GRAY);
    }

    private boolean inside(double mx, double my, int x, int y, int[] size) {
        return mx >= x && mx <= x + size[0] && my >= y && my <= y + size[1];
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        Config c = Config.get();
        if (inside(e.x(), e.y(), c.planX + planSize[0] - 5, c.planY, new int[]{6, planSize[1]})) { dragging = 3; return true; }
        if (inside(e.x(), e.y(), timerX() + timerSize[0] - 5, c.timerY, new int[]{6, timerSize[1]})) { dragging = 4; return true; }
        if (inside(e.x(), e.y(), c.planX, c.planY, planSize)) { dragging = 1; offX = e.x() - c.planX; offY = e.y() - c.planY; return true; }
        if (inside(e.x(), e.y(), timerX(), c.timerY, timerSize)) { dragging = 2; offX = e.x() - timerX(); offY = e.y() - c.timerY; return true; }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        Config c = Config.get();
        int x = (int) Math.max(0, e.x() - offX), y = (int) Math.max(0, e.y() - offY);
        switch (dragging) {
            case 1 -> { c.planX = x; c.planY = y; return true; }
            case 2 -> { c.timerX = x; c.timerY = y; return true; }
            case 3 -> { c.planWidth = (int) Math.max(80, (e.x() - c.planX) / c.planScale); return true; }
            case 4 -> {
                int left = timerX();
                if (c.timerX < 0) c.timerX = left; // 너비를 바꾸면 위치를 고정
                c.timerWidth = (int) Math.max(80, (e.x() - left) / c.timerScale);
                return true;
            }
            default -> { return super.mouseDragged(e, dx, dy); }
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = 0;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        Config c = Config.get();
        float step = scrollY > 0 ? 0.1f : -0.1f;
        if (inside(mx, my, c.planX, c.planY, planSize)) { c.planScale = clamp(c.planScale + step); return true; }
        if (inside(mx, my, timerX(), c.timerY, timerSize)) { c.timerScale = clamp(c.timerScale + step); return true; }
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
