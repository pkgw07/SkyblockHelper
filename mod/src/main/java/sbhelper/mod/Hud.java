package sbhelper.mod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 게임 화면 HUD: 제작 플래너 진행도 + 타이머. F11 전체 화면에서도 보인다 (게임이 직접 그림) */
public final class Hud {
    static final int GOLD = 0xFFFFAA00, WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, GREEN = 0xFF55FF55, RED = 0xFFFF6B6B,
        YELLOW = 0xFFFFE066, SOFT = 0xFFDDDDDD, BG = 0xC0101318, ACCENT = 0xFFFF9F1C;
    static final int ROW = 11, HEAD = 13;

    /** 타이머 종류: 키 → 이름 (설정 화면 순서) */
    public static final Map<String, String> TIMER_KINDS = new LinkedHashMap<>();
    private static final Map<String, ItemStack> ICONS = new LinkedHashMap<>();
    static {
        kind("forge", "포지", Items.ANVIL);
        kind("composter", "컴포스터", Items.COMPOSTER);
        kind("timeTower", "Time Tower", Items.CLOCK);
        kind("hoppity", "초콜릿 알", Items.EGG);
        kind("fetchur", "Fetchur", Items.COMPASS);
        kind("experiment", "실험대", Items.ENCHANTING_TABLE);
        kind("mayor", "새 시장", Items.NAME_TAG);
    }
    private static final ItemStack OTHER = new ItemStack(Items.PAPER);

    private static void kind(String key, String name, Item icon) { TIMER_KINDS.put(key, name); ICONS.put(key, new ItemStack(icon)); }

    static ItemStack icon(String kind) { return ICONS.getOrDefault(kind, OTHER); }

    /** HUD 한 줄: 왼쪽 글자, 오른쪽 글자(색), 작은 아이템 그림, 진행 막대 (0~1, 없으면 음수) */
    public record Row(String left, String right, int color, ItemStack icon, float progress) {
        static Row head(String left, String right) { return new Row(left, right, GOLD, null, -1); }
        static Row text(String left, String right) { return new Row(left, right, GRAY, null, -1); }
    }

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Config c = Config.get();
        if (!c.hud || mc.player == null) return;
        if (c.onlyInSkyblock && !SkyBlock.inSkyblock) return;
        if (!Link.connected && !SkyBlock.TEST) return;
        if (c.planHud) drawAt(g, mc.font, planLines(), c.planX, c.planY, c.planRight, c.planBottom, c.planScale, c.planWidth);
        if (c.timerHud) drawAt(g, mc.font, timerLines(), c.timerX, c.timerY, c.timerRight, c.timerBottom, c.timerScale, c.timerWidth);
    }

    /** 가장자리 기준 위치로 그린다. 그린 자리 [x, y, 폭, 높이] */
    static int[] drawAt(GuiGraphicsExtractor g, Font font, List<Row> rows, int x, int y, boolean right, boolean bottom, float scale, int width) {
        int[] size = scaled(font, rows, width, scale);
        int[] p = place(g.guiWidth(), g.guiHeight(), x, y, right, bottom, size[0], size[1]);
        drawScaled(g, font, p[0], p[1], scale, rows, width);
        return new int[]{p[0], p[1], size[0], size[1]};
    }

    /** 가장자리에서의 거리 → 화면 좌표 (화면 밖으로 나가지 않게) */
    static int[] place(int sw, int sh, int x, int y, boolean right, boolean bottom, int w, int h) {
        int sx = right ? sw - x - w : x, sy = bottom ? sh - y - h : y;
        sx = Math.max(0, Math.min(sx, sw - w));
        sy = Math.max(0, Math.min(sy, sh - h));
        return new int[]{sx, sy};
    }

    static List<Row> planLines() {
        Link.Plan p = Link.plan;
        List<Row> rows = new ArrayList<>();
        if (p == null) { rows.add(Row.head("제작 플래너", "")); rows.add(Row.text("목표 없음", "")); return rows; }
        rows.add(Row.head(p.title(), Math.round(p.pct() * 100) + "%"));
        for (Link.PlanRow r : p.rows()) {
            float pct = r.need() > 0 ? (float) Math.min(1, r.have() / r.need()) : 1f;
            rows.add(new Row(r.name(), Fmt.count(r.have()) + "/" + Fmt.count(r.need()), pct >= 1 ? GREEN : SOFT, null, pct));
        }
        if (p.more() > 0) rows.add(Row.text("+ " + p.more() + "종 더", ""));
        return rows;
    }

    static List<Row> timerLines() {
        Config c = Config.get();
        List<Row> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        List<Link.Timer> shown = new ArrayList<>();
        for (Link.Timer t : Link.timers) if (c.showTimer(t.kind())) shown.add(t);
        rows.add(Row.head("타이머", shown.isEmpty() ? "" : String.valueOf(shown.size())));
        int n = 0;
        for (Link.Timer t : shown) {
            if (n++ >= 10) { rows.add(Row.text("+ " + (shown.size() - 10) + "개 더", "")); break; }
            long left = t.at() - now;
            boolean done = t.done() || left <= 0;
            String right;
            int color;
            if (done) {
                right = t.kind().equals("composter") ? "멈춤!" : t.kind().equals("forge") ? "완료!" : "지금";
                color = t.kind().equals("composter") ? RED : GREEN;
            } else {
                right = Fmt.left(left);
                color = left < 10 * 60_000L ? YELLOW : SOFT;
            }
            float progress = -1;
            if (t.start() > 0 && t.at() > t.start()) progress = done ? 1f : (float) Math.max(0, Math.min(1, (now - t.start()) / (double) (t.at() - t.start())));
            rows.add(new Row(t.label(), right, color, icon(t.kind()), progress));
        }
        if (shown.isEmpty()) rows.add(Row.text(Link.timers.isEmpty() ? "없음" : "숨긴 종류만 있어요", ""));
        return rows;
    }

    /** 상자 크기 [폭, 높이] (배율 적용 전). width > 0 이면 그 너비로 고정 */
    static int[] measure(Font font, List<Row> rows, int width) {
        int w = 0;
        for (Row r : rows) {
            int iw = r.icon() != null ? 11 : 0;
            w = Math.max(w, iw + font.width(r.left()) + (r.right().isEmpty() ? 0 : font.width(r.right()) + 12));
        }
        w = width > 0 ? width : Math.min(w + 10, 300);
        return new int[]{w, height(rows)};
    }

    static int height(List<Row> rows) { return rows.isEmpty() ? 0 : HEAD + (rows.size() - 1) * ROW + 4; }

    static int[] scaled(Font font, List<Row> rows, int width, float scale) {
        int[] s = measure(font, rows, width);
        return new int[]{Math.round(s[0] * scale), Math.round(s[1] * scale)};
    }

    /** 배율을 적용해 그린다. 그린 크기 [폭, 높이] (배율 적용 후) */
    static int[] drawScaled(GuiGraphicsExtractor g, Font font, int x, int y, float scale, List<Row> rows, int width) {
        int[] size = measure(font, rows, width);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        box(g, font, 0, 0, rows, size[0]);
        g.pose().popMatrix();
        return new int[]{Math.round(size[0] * scale), Math.round(size[1] * scale)};
    }

    /** 모서리가 둥근 반투명 상자 + 첫 줄은 제목, 나머지는 왼쪽/오른쪽 글자 (너비를 넘으면 왼쪽 글자를 줄임) */
    static void box(GuiGraphicsExtractor g, Font font, int x, int y, List<Row> rows, int w) {
        int h = height(rows);
        Ui.round(g, x, y, x + w, y + h, BG);
        g.fill(x + 2, y, x + w - 2, y + 1, ACCENT);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            boolean head = i == 0;
            int ly = head ? y + 3 : y + HEAD + 1 + (i - 1) * ROW;
            int lx = x + 5;
            if (r.icon() != null) {
                g.pose().pushMatrix();
                g.pose().translate(lx, ly - 0.5f);
                g.pose().scale(0.5f, 0.5f);
                g.item(r.icon(), 0, 0);
                g.pose().popMatrix();
                lx += 11;
            }
            int rightW = r.right().isEmpty() ? 0 : font.width(r.right()) + 8;
            g.text(font, fit(font, r.left(), x + w - 5 - rightW - lx), lx, ly, head ? GOLD : WHITE, true);
            if (!r.right().isEmpty()) g.text(font, r.right(), x + w - 5 - font.width(r.right()), ly, r.color(), true);
            if (r.progress() >= 0) {
                int bx = lx, bw = x + w - 5 - lx, by = ly + 9;
                g.fill(bx, by, bx + bw, by + 1, 0x40FFFFFF);
                g.fill(bx, by, bx + Math.round(bw * r.progress()), by + 1, r.progress() >= 1 ? GREEN : ACCENT);
            }
            if (head && rows.size() > 1) g.fill(x + 4, y + HEAD - 1, x + w - 4, y + HEAD, 0x30FFFFFF);
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
