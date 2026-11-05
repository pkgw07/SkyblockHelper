package sbhelper.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * 구버전이면 뜨는 업데이트 창 (타이틀 화면, 또는 서버에 들어온 뒤 · /sbh update).
 * '업데이트' 를 누르면 받아 두고, 게임을 다시 시작하면 적용된다.
 */
public final class UpdateScreen extends Screen {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("skyblockhelper", "icon.png");
    private final Screen parent;
    private final Ui.Anim anim = new Ui.Anim();
    private final long openedAt = System.currentTimeMillis();
    private volatile String message;
    private volatile int state = 0; // 0 물어봄, 1 받는 중, 2 끝
    private int[] yes = new int[4], no = new int[4];

    public UpdateScreen(Screen parent) {
        super(Component.literal("SkyblockHelper 모드 업데이트"));
        this.parent = parent;
        this.message = "새 버전 " + Updater.latest + " 이 있어요 (지금 " + SbhMod.VERSION + ")";
    }

    private void update() {
        if (state != 0) return;
        state = 1;
        message = "받는 중…";
        Thread t = new Thread(() -> {
            boolean ok = Updater.download();
            message = ok ? "다 받았어요. 게임을 다시 시작하면 적용돼요." : "받지 못했어요. /sbh update 로 다시 해 볼 수 있어요.";
            state = 2;
        }, "sbh-update-download");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float d) {
        float a = Ui.easeOut((System.currentTimeMillis() - openedAt) / 240f);
        g.fillGradient(0, 0, this.width, this.height, Ui.alpha(0x88000000, a), Ui.alpha(0xB0000000, a));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        anim.frame();
        float a = Ui.easeOut((System.currentTimeMillis() - openedAt) / 260f);
        int pw = Math.min(260, this.width - 24), ph = 108;
        int px = (this.width - pw) / 2, py = (this.height - ph) / 2 + Math.round((1 - a) * 14);
        Ui.shadow(g, px, py, px + pw, py + ph, a);
        Ui.card(g, px, py, px + pw, py + ph, Ui.alpha(0xF0141820, a), Ui.alpha(0x38FFFFFF, a));
        g.fill(px + 2, py, px + pw - 2, py + 1, Ui.alpha(Hud.ACCENT, a));
        g.blit(RenderPipelines.GUI_TEXTURED, ICON, px + 10, py + 10, 0f, 0f, 20, 20, 128, 128, 128, 128, ARGB.white(a));
        text(g, this.title.getString(), px + 36, py + 11, Ui.alpha(Hud.GOLD, a));
        text(g, Hud.fit(this.font, message, pw - 46), px + 36, py + 23, Ui.alpha(state == 2 ? Hud.GREEN : 0xFFE6E8EC, a));
        text(g, Hud.fit(this.font, "받은 뒤 게임을 다시 시작하면 적용돼요", pw - 20), px + 10, py + 44, Ui.alpha(0xFF8A8F99, a));
        text(g, Hud.fit(this.font, "언제든 /sbh update 로 다시 열 수 있어요", pw - 20), px + 10, py + 56, Ui.alpha(0xFF6F7580, a));
        int bw = (pw - 26) / 2, by = py + ph - 28;
        if (state == 0) {
            yes = button(g, mx, my, px + 10, by, bw, "yes", "업데이트", true, a);
            no = button(g, mx, my, px + 16 + bw, by, bw, "no", "나중에", false, a);
        } else if (state == 1) {
            float spin = (System.currentTimeMillis() % 1000) / 1000f;
            int bx = px + 10, bwid = pw - 20;
            Ui.round(g, bx, by + 7, bx + bwid, by + 11, Ui.alpha(0x30FFFFFF, a));
            int seg = bwid / 3, sx = bx + Math.round((bwid - seg) * (0.5f - 0.5f * (float) Math.cos(spin * Math.PI * 2)));
            Ui.round(g, sx, by + 7, sx + seg, by + 11, Ui.alpha(Hud.ACCENT, a));
            yes = new int[4]; no = new int[4];
        } else {
            yes = new int[4];
            no = button(g, mx, my, px + 10, by, pw - 20, "ok", "확인", true, a);
        }
    }

    private int[] button(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String key, String label, boolean primary, float a) {
        int h = 18;
        float hv = anim.to(key, Ui.inside(mx, my, x, y, x + w, y + h) ? 1 : 0, 16);
        int bg = primary ? Ui.mix(hv, Hud.ACCENT, 0xFFFFBE5C) : Ui.mix(hv, 0x1CFFFFFF, 0x34FFFFFF);
        Ui.round2(g, x, y, x + w, y + h, Ui.alpha(bg, a));
        int c = Ui.alpha(primary ? 0xFF1B1405 : 0xFFFFFFFF, a);
        if (ARGB.alpha(c) >= 8) g.text(this.font, label, x + (w - this.font.width(label)) / 2, y + 5, c, !primary);
        return new int[]{x, y, x + w, y + h};
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        if (ARGB.alpha(color) >= 8) g.text(this.font, s, x, y, color, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() == 0) {
            if (state == 0 && Ui.inside(e.x(), e.y(), yes[0], yes[1], yes[2], yes[3])) { Ui.click(); update(); return true; }
            if (state != 1 && Ui.inside(e.x(), e.y(), no[0], no[1], no[2], no[3])) { Ui.click(); onClose(); return true; }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public void onClose() { if (state != 1) this.minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
