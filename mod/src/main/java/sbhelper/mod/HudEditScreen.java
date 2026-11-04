package sbhelper.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** HUD 위치 정하기: 상자를 끌어서 옮기고 Esc 로 닫으면 저장 */
public final class HudEditScreen extends Screen {
    private int dragging = 0; // 1 제작 플래너, 2 타이머
    private double offX, offY;
    private int[] planSize = {100, 20}, timerSize = {100, 20};

    public HudEditScreen() { super(Component.literal("SkyblockHelper HUD")); }

    private int timerX() { Config c = Config.get(); return c.timerX < 0 ? this.width - timerSize[0] - 4 : c.timerX; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Config c = Config.get();
        planSize = Hud.drawPlan(g, this.font, c.planX, c.planY);
        timerSize = Hud.drawTimers(g, this.font, timerX(), c.timerY);
        g.text(this.font, "상자를 끌어서 옮기고 Esc 로 닫으면 저장돼요", this.width / 2 - this.font.width("상자를 끌어서 옮기고 Esc 로 닫으면 저장돼요") / 2,
            this.height - 20, Hud.WHITE, true);
    }

    private boolean inside(double mx, double my, int x, int y, int[] size) {
        return mx >= x && mx <= x + size[0] && my >= y && my <= y + size[1];
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        Config c = Config.get();
        if (inside(e.x(), e.y(), c.planX, c.planY, planSize)) { dragging = 1; offX = e.x() - c.planX; offY = e.y() - c.planY; return true; }
        if (inside(e.x(), e.y(), timerX(), c.timerY, timerSize)) { dragging = 2; offX = e.x() - timerX(); offY = e.y() - c.timerY; return true; }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        Config c = Config.get();
        int x = (int) Math.max(0, e.x() - offX), y = (int) Math.max(0, e.y() - offY);
        if (dragging == 1) { c.planX = x; c.planY = y; return true; }
        if (dragging == 2) { c.timerX = x; c.timerY = y; return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = 0;
        return super.mouseReleased(e);
    }

    @Override
    public void onClose() {
        Config.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
