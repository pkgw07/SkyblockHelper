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
        if (c.planHud) drawScaled(g, mc.font, c.planX, c.planY, c.planScale, planLines(), c.planWidth);
        if (c.timerHud) {
            List<String[]> lines = timerLines();
            int[] size = measure(mc.font, lines, c.timerWidth);
            int x = c.timerX < 0 ? g.guiWidth() - Math.round(size[0] * c.timerScale) - 4 : c.timerX;
            drawScaled(g, mc.font, x, c.timerY, c.timerScale, lines, c.timerWidth);
        }
    }

    static List<String[]> planLines() {
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
        return lines;
    }

    static List<String[]> timerLines() {
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"타이머", ""});
        long now = System.currentTimeMillis();
        int n = 0;
        for (Link.Timer t : Link.timers) {
            if (n++ >= 6) break;
            lines.add(new String[]{t.label(), Fmt.left(t.at() - now)});
        }
        if (n == 0) lines.add(new String[]{"없음", ""});
        return lines;
    }

    /** 상자 크기 [폭, 높이] (배율 적용 전). width > 0 이면 그 너비로 고정 */
    static int[] measure(Font font, List<String[]> lines, int width) {
        int w = 0;
        for (String[] l : lines) w = Math.max(w, font.width(l[0]) + (l[1].isEmpty() ? 0 : font.width(l[1]) + 12));
        w = width > 0 ? width : Math.min(w + 8, 300);
        return new int[]{w, lines.size() * 10 + 6};
    }

    /** 배율을 적용해 그린다. 그린 크기 [폭, 높이] (배율 적용 후) */
    static int[] drawScaled(GuiGraphicsExtractor g, Font font, int x, int y, float scale, List<String[]> lines, int width) {
        int[] size = measure(font, lines, width);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        box(g, font, 0, 0, lines, size[0]);
        g.pose().popMatrix();
        return new int[]{Math.round(size[0] * scale), Math.round(size[1] * scale)};
    }

    /** 반투명 상자 + 줄마다 왼쪽 글자 / 오른쪽 글자 (너비를 넘으면 왼쪽 글자를 줄임) */
    static void box(GuiGraphicsExtractor g, Font font, int x, int y, List<String[]> lines, int w) {
        int h = lines.size() * 10 + 6;
        g.fill(x, y, x + w, y + h, BG);
        for (int i = 0; i < lines.size(); i++) {
            String[] l = lines.get(i);
            int ly = y + 4 + i * 10;
            int rightW = l[1].isEmpty() ? 0 : font.width(l[1]) + 8;
            g.text(font, fit(font, l[0], w - 8 - rightW), x + 4, ly, i == 0 ? GOLD : WHITE, true);
            if (!l[1].isEmpty()) {
                int color = i == 0 ? GOLD : (l[1].endsWith("100%") || l[1].equals("완료!") ? GREEN : GRAY);
                g.text(font, l[1], x + w - 4 - font.width(l[1]), ly, color, true);
            }
        }
    }

    /** 너비에 맞게 자르기 (… 붙임) */
    static String fit(Font font, String s, int max) {
        if (max <= 0) return "";
        if (font.width(s) <= max) return s;
        String out = s;
        while (!out.isEmpty() && font.width(out + "…") > max) out = out.substring(0, out.length() - 1);
        return out + "…";
    }

    private Hud() {}
}
