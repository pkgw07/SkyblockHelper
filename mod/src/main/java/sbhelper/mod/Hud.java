package sbhelper.mod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** 게임 화면 HUD: 제작 플래너 진행도 + 타이머. F11 전체 화면에서도 보인다 (게임이 직접 그림) */
public final class Hud {
    static final int GOLD = 0xFFFFAA00, WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, GREEN = 0xFF55FF55, BG = 0x90000000;

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Config c = Config.get();
        if (!c.hud || mc.player == null) return;
        if (c.onlyInSkyblock && !SkyBlock.inSkyblock) return;
        if (!Link.connected) return;
        if (c.planHud) drawPlan(g, mc.font, c.planX, c.planY);
        if (c.timerHud) {
            int w = timerWidth(mc.font);
            drawTimers(g, mc.font, c.timerX < 0 ? g.guiWidth() - w - 4 : c.timerX, c.timerY);
        }
    }

    /** 제작 플래너 상자. 그린 크기 [폭, 높이] */
    static int[] drawPlan(GuiGraphicsExtractor g, Font font, int x, int y) {
        Link.Plan p = Link.plan;
        List<String[]> lines = new ArrayList<>();
        if (p == null) lines.add(new String[]{"제작 플래너: 목표 없음", ""});
        else {
            lines.add(new String[]{p.title(), Math.round(p.pct() * 100) + "%"});
            for (Link.PlanRow r : p.rows()) {
                double pct = r.need() > 0 ? Math.min(1, r.have() / r.need()) : 1;
                lines.add(new String[]{r.name(), Fmt.count(r.have()) + "/" + Fmt.count(r.need()) + " " + Math.round(pct * 100) + "%"});
            }
            if (p.more() > 0) lines.add(new String[]{"+ " + p.more() + "종 더", ""});
        }
        return box(g, font, x, y, lines, true);
    }

    static int timerWidth(Font font) {
        int w = 120;
        for (Link.Timer t : Link.timers) w = Math.max(w, font.width(t.label()) + 60);
        return Math.min(w, 260);
    }

    static int[] drawTimers(GuiGraphicsExtractor g, Font font, int x, int y) {
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"타이머", ""});
        long now = System.currentTimeMillis();
        int n = 0;
        for (Link.Timer t : Link.timers) {
            if (n++ >= 6) break;
            lines.add(new String[]{t.label(), Fmt.left(t.at() - now)});
        }
        if (n == 0) lines.add(new String[]{"없음", ""});
        return box(g, font, x, y, lines, false);
    }

    /** 반투명 상자 + 줄마다 왼쪽 글자 / 오른쪽 글자 */
    static int[] box(GuiGraphicsExtractor g, Font font, int x, int y, List<String[]> lines, boolean plan) {
        int w = 0;
        for (String[] l : lines) w = Math.max(w, font.width(l[0]) + (l[1].isEmpty() ? 0 : font.width(l[1]) + 12));
        w = Math.min(w + 8, 300);
        int h = lines.size() * 10 + 6;
        g.fill(x, y, x + w, y + h, BG);
        for (int i = 0; i < lines.size(); i++) {
            String[] l = lines.get(i);
            int ly = y + 4 + i * 10;
            int left = i == 0 ? GOLD : WHITE;
            g.text(font, l[0], x + 4, ly, left, true);
            if (!l[1].isEmpty()) {
                int color = i == 0 ? GOLD : (l[1].endsWith("100%") || l[1].equals("완료!") ? GREEN : GRAY);
                g.text(font, l[1], x + w - 4 - font.width(l[1]), ly, color, true);
            }
        }
        return new int[]{w, h};
    }

    private Hud() {}
}
