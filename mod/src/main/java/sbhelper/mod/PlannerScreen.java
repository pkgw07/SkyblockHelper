package sbhelper.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 게임 안 제작 플래너 (/sbh plan). 프로그램의 제작 플래너와 같은 설정을 바꾼다.
 *  - 왼쪽: 목표 목록 (누르면 그 목표, × 로 지우기) · 검색해서 새 목표 추가
 *  - 오른쪽: 수량 (−/+, Shift 는 10씩) · 기본 재료 (스위치로 끄기/켜기) · 자루 · 인벤토리 포함
 */
public final class PlannerScreen extends Screen {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("skyblockhelper", "icon.png");
    private final Screen parent;
    private final Ui.Anim anim = new Ui.Anim();
    private final long openedAt = System.currentTimeMillis();
    private long closingAt = 0;
    private EditBox search;
    private double leftScroll = 0, rightScroll = 0;
    private final List<Hit> hits = new ArrayList<>();
    private String tooltip;
    private int px, py, pw, ph;

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {}

    public PlannerScreen(Screen parent) { super(Component.literal("제작 플래너")); this.parent = parent; }

    @Override
    protected void init() {
        pw = Math.min(430, this.width - 24);
        ph = Math.min(250, this.height - 12);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        String old = search == null ? "" : search.getValue();
        search = new EditBox(this.font, px + 10, py + 40, 136, 16, Component.literal("검색"));
        search.setHint(Component.literal("새 목표 검색…"));
        search.setMaxLength(40);
        search.setValue(old);
        search.setResponder(v -> leftScroll = 0);
        this.addRenderableWidget(search);
    }

    private float openness() {
        long now = System.currentTimeMillis();
        float a = Ui.easeOut((now - openedAt) / 260f);
        if (closingAt > 0) a = Math.min(a, 1 - Ui.easeOut((now - closingAt) / 160f));
        return Math.max(0, a);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float d) {
        float a = openness();
        g.fillGradient(0, 0, this.width, this.height, Ui.alpha(0x88000000, a), Ui.alpha(0xB0000000, a));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        anim.frame();
        hits.clear();
        tooltip = null;
        float a = openness();
        int oy = Math.round((1 - a) * 14);
        Ui.shadow(g, px, py + oy, px + pw, py + ph + oy, a);
        Ui.card(g, px, py + oy, px + pw, py + ph + oy, Ui.alpha(0xF0141820, a), Ui.alpha(0x38FFFFFF, a));
        g.fill(px + 2, py + oy, px + pw - 2, py + oy + 1, Ui.alpha(Hud.ACCENT, a));
        g.blit(RenderPipelines.GUI_TEXTURED, ICON, px + 10, py + oy + 9, 0f, 0f, 18, 18, 128, 128, 128, 128, ARGB.white(a));
        text(g, "제작 플래너", px + 34, py + oy + 10, Ui.alpha(Hud.GOLD, a));
        text(g, Link.connected ? "프로그램과 같이 바뀌어요" : "프로그램을 켜 주세요", px + 34, py + oy + 21, Ui.alpha(Link.connected ? 0xFF8A8F99 : Hud.RED, a));
        // 옵션: 자루 · 인벤토리
        int ox = px + pw - 10;
        ox = pill(g, mx, my, ox, py + oy + 12, "inv", "인벤토리", Link.useInventory, () -> Link.planOption(Link.useSacks, !Link.useInventory), a);
        pill(g, mx, my, ox - 4, py + oy + 12, "sacks", "자루", Link.useSacks, () -> Link.planOption(!Link.useSacks, Link.useInventory), a);
        g.fill(px + 8, py + oy + 34, px + pw - 8, py + oy + 35, Ui.alpha(0x22FFFFFF, a));

        search.setY(py + 40 + oy);
        super.extractRenderState(g, mx, my, delta);
        left(g, mx, my, px + 10, py + 60 + oy, 136, py + ph - 30 + oy, a);
        g.fill(px + 152, py + oy + 40, px + 153, py + ph - 30 + oy, Ui.alpha(0x22FFFFFF, a));
        right(g, mx, my, px + 160, py + 40 + oy, pw - 170, py + ph - 30 + oy, a);

        int fy = py + ph - 24 + oy;
        button(g, mx, my, px + pw - 70, fy, 60, 16, "done", "완료", true, () -> { Ui.click(); onClose(); }, a);
        text(g, Hud.fit(this.font, "상자에서 아이템에 마우스를 올리고 Ctrl+P 로도 목표 추가", pw - 96), px + 12, fy + 4, Ui.alpha(0xFF6F7580, a));
        if (tooltip != null && a > 0.9f) g.setTooltipForNextFrame(this.font, Component.literal(tooltip), mx, my);
    }

    /** 왼쪽: 검색 결과 또는 목표 목록 */
    private void left(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int bottom, float a) {
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        g.enableScissor(x - 2, y, x + w + 2, bottom);
        int rowH = 16;
        if (!q.isEmpty()) {
            List<String[]> found = new ArrayList<>();
            for (String[] c : Link.craftables) if (c[1].toLowerCase(Locale.ROOT).contains(q) || c[0].toLowerCase(Locale.ROOT).contains(q)) found.add(c);
            leftScroll = Math.max(0, Math.min(leftScroll, Math.max(0, found.size() * rowH - (bottom - y))));
            int ry = y - (int) leftScroll;
            if (found.isEmpty()) text(g, Link.craftables.isEmpty() ? "목록을 받는 중…" : "찾는 아이템이 없어요", x + 2, y + 4, Ui.alpha(0xFF8A8F99, a));
            for (String[] c : found) {
                if (ry + rowH >= y && ry <= bottom) {
                    boolean hov = Ui.inside(mx, my, x, Math.max(ry, y), x + w, Math.min(ry + rowH, bottom));
                    if (hov) Ui.round(g, x, ry, x + w, ry + rowH - 1, Ui.alpha(0x24FFFFFF, a));
                    text(g, Hud.fit(this.font, c[1], w - 14), x + 4, ry + 4, Ui.alpha(0xFFE6E8EC, a));
                    text(g, "+", x + w - 9, ry + 4, Ui.alpha(Hud.GREEN, a));
                    String id = c[0];
                    if (ry >= y - rowH / 2) hits.add(new Hit(x, Math.max(ry, y), x + w, Math.min(ry + rowH, bottom), () -> {
                        Ui.click(); Link.planCommand("add", id, 1); search.setValue("");
                    }));
                }
                ry += rowH;
            }
        } else {
            List<Link.Goal> goals = Link.goals;
            text(g, "목표", x + 2, y, Ui.alpha(0xFF8A8F99, a));
            int ry = y + 12 - (int) leftScroll;
            if (goals.isEmpty()) text(g, "위에서 검색해 추가하세요", x + 2, ry + 4, Ui.alpha(0xFF6F7580, a));
            for (Link.Goal goal : goals) {
                boolean cur = goal.id().equals(Link.currentGoal);
                boolean hov = Ui.inside(mx, my, x, ry, x + w, ry + rowH);
                float hv = anim.to("g:" + goal.id(), cur ? 1 : hov ? 0.5f : 0, 14);
                if (hv > 0.01f) Ui.round(g, x, ry, x + w, ry + rowH - 1, Ui.alpha(Ui.mix(hv, 0x10FFFFFF, 0x30FF9F1C), a));
                if (cur) g.fill(x, ry + 2, x + 2, ry + rowH - 3, Ui.alpha(Hud.ACCENT, a));
                String label = goal.name() + (goal.amount() > 1 ? " ×" + goal.amount() : "");
                text(g, Hud.fit(this.font, label, w - 18), x + 6, ry + 4, Ui.alpha(cur ? 0xFFFFFFFF : 0xFFB8BCC4, a));
                boolean xh = Ui.inside(mx, my, x + w - 12, ry, x + w, ry + rowH);
                text(g, "×", x + w - 9, ry + 4, Ui.alpha(xh ? Hud.RED : 0xFF6F7580, a));
                String id = goal.id();
                hits.add(new Hit(x + w - 12, ry, x + w, ry + rowH, () -> { Ui.click(); Link.planCommand("remove", id, 1); }));
                hits.add(new Hit(x, ry, x + w - 12, ry + rowH, () -> { Ui.click(); Link.planCommand("select", id, 1); }));
                ry += rowH;
            }
        }
        g.disableScissor();
    }

    /** 오른쪽: 지금 목표 · 수량 · 재료 */
    private void right(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int bottom, float a) {
        String cur = Link.currentGoal;
        Link.Plan plan = Link.plan;
        if (cur == null) {
            text(g, "목표를 고르세요", x + 2, y + 4, Ui.alpha(0xFF8A8F99, a));
            return;
        }
        String name = cur;
        for (Link.Goal goal : Link.goals) if (goal.id().equals(cur)) name = goal.name();
        text(g, Hud.fit(this.font, name, w - 90), x, y + 4, Ui.alpha(0xFFFFFFFF, a));
        int amount = Link.currentAmount;
        // 수량 −/+
        int bx = x + w - 78;
        button(g, mx, my, bx, y, 18, 16, "minus", "−", false, () -> { Ui.click(); Link.planCommand("amount", cur, Math.max(1, amount - step())); }, a);
        String n = String.valueOf(amount);
        text(g, n, bx + 20 + (36 - this.font.width(n)) / 2, y + 4, Ui.alpha(Hud.GOLD, a));
        button(g, mx, my, bx + 58, y, 18, 16, "plus", "+", false, () -> { Ui.click(); Link.planCommand("amount", cur, amount + step()); }, a);
        if (Ui.inside(mx, my, bx, y, bx + 76, y + 16)) tooltip = "Shift 를 누르고 누르면 10개씩";
        // 진행도
        double pct = plan == null ? 0 : plan.pct();
        float pv = anim.to("pct", (float) pct, 6);
        int barY = y + 20;
        Ui.round(g, x, barY, x + w, barY + 4, Ui.alpha(0x30FFFFFF, a));
        Ui.round(g, x, barY, x + Math.max(3, Math.round(w * pv)), barY + 4, Ui.alpha(pct >= 1 ? Hud.GREEN : Hud.ACCENT, a));
        text(g, Math.round(pct * 100) + "%", x + w - this.font.width(Math.round(pct * 100) + "%"), barY + 7, Ui.alpha(0xFF8A8F99, a));
        text(g, "기본 재료 (스위치로 빼기)", x, barY + 7, Ui.alpha(0xFF8A8F99, a));
        // 재료
        int top = barY + 19;
        List<Link.Material> ms = Link.materials;
        int rowH = 15;
        rightScroll = Math.max(0, Math.min(rightScroll, Math.max(0, ms.size() * rowH - (bottom - top))));
        g.enableScissor(x - 2, top, x + w + 2, bottom);
        int ry = top - (int) rightScroll;
        for (Link.Material m : ms) {
            if (ry + rowH >= top && ry <= bottom) {
                boolean on = !m.off();
                float sv = anim.to("m:" + m.id(), on ? 1 : 0, 14);
                int tx = x, ty = ry + 3;
                Ui.round(g, tx, ty, tx + 16, ty + 8, Ui.alpha(Ui.mix(sv, 0xFF3A404C, Hud.ACCENT), a));
                int kx = tx + 1 + Math.round(sv * 8);
                Ui.round(g, kx, ty + 1, kx + 6, ty + 7, Ui.alpha(0xFFFFFFFF, a));
                boolean done = m.need() > 0 && m.have() >= m.need();
                String right = on ? Fmt.count(m.have()) + "/" + Fmt.count(m.need()) : "뺌";
                int rw = this.font.width(right);
                text(g, Hud.fit(this.font, m.name(), w - 26 - rw - 6), x + 21, ry + 3, Ui.alpha(on ? 0xFFE6E8EC : 0xFF6F7580, a));
                text(g, right, x + w - rw, ry + 3, Ui.alpha(!on ? 0xFF6F7580 : done ? Hud.GREEN : 0xFFB8BCC4, a));
                if (on && m.need() > 0) {
                    int bw = w - 21;
                    g.fill(x + 21, ry + 12, x + 21 + bw, ry + 13, Ui.alpha(0x20FFFFFF, a));
                    g.fill(x + 21, ry + 12, x + 21 + (int) Math.round(bw * Math.min(1, m.have() / m.need())), ry + 13, Ui.alpha(done ? Hud.GREEN : Hud.ACCENT, a));
                }
                String id = m.id();
                hits.add(new Hit(x, Math.max(ry, top), x + w, Math.min(ry + rowH, bottom), () -> { Ui.click(); Link.planExclude(cur, id, on); }));
            }
            ry += rowH;
        }
        if (ms.isEmpty()) text(g, Link.connected ? "재료를 계산하는 중…" : "-", x, top + 2, Ui.alpha(0xFF6F7580, a));
        g.disableScissor();
    }

    private int step() { return hasShiftDownSafe() ? 10 : 1; }

    private boolean hasShiftDownSafe() {
        try {
            var win = this.minecraft.getWindow();
            return com.mojang.blaze3d.platform.InputConstants.isKeyDown(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
        } catch (Exception e) { return false; }
    }

    /** 오른쪽 위 작은 켜고 끄기 (오른쪽 끝 x 를 받아 왼쪽 끝 x 를 돌려줌) */
    private int pill(GuiGraphicsExtractor g, int mx, int my, int right, int y, String key, String label, boolean on, Runnable action, float a) {
        int w = this.font.width(label) + 18, x = right - w;
        boolean hov = Ui.inside(mx, my, x, y, right, y + 14);
        float sv = anim.to("p:" + key, on ? 1 : 0, 14);
        Ui.round(g, x, y, right, y + 14, Ui.alpha(Ui.mix(sv, hov ? 0x30FFFFFF : 0x18FFFFFF, 0x50FF9F1C), a));
        Ui.round(g, x + 4, y + 5, x + 8, y + 9, Ui.alpha(on ? Hud.ACCENT : 0xFF6F7580, a));
        text(g, label, x + 11, y + 3, Ui.alpha(on ? 0xFFFFFFFF : 0xFF8A8F99, a));
        hits.add(new Hit(x, y, right, y + 14, () -> { Ui.click(); action.run(); }));
        if (hov) tooltip = label + " 안의 재료도 가진 것으로 세기";
        return x;
    }

    private void button(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String key, String label, boolean primary, Runnable action, float a) {
        boolean hov = Ui.inside(mx, my, x, y, x + w, y + h);
        float hv = anim.to("b:" + key, hov ? 1 : 0, 16);
        int bg = primary ? Ui.mix(hv, Hud.ACCENT, 0xFFFFBE5C) : Ui.mix(hv, 0x1CFFFFFF, 0x34FFFFFF);
        Ui.round2(g, x, y, x + w, y + h, Ui.alpha(bg, a));
        int c = Ui.alpha(primary ? 0xFF1B1405 : 0xFFFFFFFF, a);
        if (ARGB.alpha(c) >= 8) g.text(this.font, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2, c, !primary);
        hits.add(new Hit(x, y, x + w, y + h, action));
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        if (ARGB.alpha(color) >= 8) g.text(this.font, s, x, y, color, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (closingAt > 0) return true;
        if (super.mouseClicked(e, doubleClick)) return true;
        if (e.button() == 0) for (Hit h : List.copyOf(hits)) {
            if (Ui.inside(e.x(), e.y(), h.x1(), h.y1(), h.x2(), h.y2())) { h.action().run(); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < px + 152) leftScroll = Math.max(0, leftScroll - sy * 16);
        else rightScroll = Math.max(0, rightScroll - sy * 15);
        return true;
    }

    @Override
    public void tick() {
        if (closingAt > 0 && System.currentTimeMillis() - closingAt >= 170) { closingAt = 0; this.minecraft.gui.setScreen(parent); }
    }

    @Override
    public void onClose() { if (closingAt == 0) closingAt = System.currentTimeMillis(); }

    @Override
    public boolean isPauseScreen() { return false; }
}
