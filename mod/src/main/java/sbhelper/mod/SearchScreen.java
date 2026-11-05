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

/** 검색 칸 + 결과 목록 창의 공통 모양 (경매장 검색 · 로그 검색). 설정 창과 같은 디자인 · 애니메이션 */
public abstract class SearchScreen extends Screen {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("skyblockhelper", "icon.png");
    /** 결과 한 줄: 왼쪽 · 오른쪽 글자, 오른쪽 색, 누르면 할 일(없으면 null), 마우스를 올리면 설명 */
    public record Row(String left, String right, int color, Runnable action, String tooltip) {}

    private final Screen parent;
    private final Ui.Anim anim = new Ui.Anim();
    private final long openedAt = System.currentTimeMillis();
    private long closingAt = 0;
    protected EditBox box;
    private double scroll = 0;
    private String lastQuery = null;
    private long typedAt = 0;
    private final List<Object[]> hits = new ArrayList<>();
    private String tooltip;
    protected int px, py, pw, ph;

    protected SearchScreen(String title, Screen parent) { super(Component.literal(title)); this.parent = parent; }

    protected abstract String subtitle();
    protected abstract String hint();
    /** 검색어가 바뀌고 잠깐 뒤 (입력이 멈추면) */
    protected abstract void search(String query);
    protected abstract List<Row> rows();
    /** 오른쪽 위 켜고 끄기 (없으면 null): [이름, 켜짐?] */
    protected String[] option() { return null; }
    protected void toggleOption() {}
    protected String footer() { return ""; }

    @Override
    protected void init() {
        pw = Math.min(430, this.width - 24);
        ph = Math.min(260, this.height - 12);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        String old = box == null ? "" : box.getValue();
        box = new EditBox(this.font, px + 10, py + 40, pw - 20, 16, Component.literal("검색"));
        box.setHint(Component.literal(hint()));
        box.setMaxLength(60);
        box.setValue(old);
        box.setResponder(v -> { typedAt = System.currentTimeMillis(); scroll = 0; });
        this.addRenderableWidget(box);
        this.setInitialFocus(box);
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
    public void tick() {
        String q = box.getValue().trim();
        if (!q.equals(lastQuery) && System.currentTimeMillis() - typedAt > 250) { lastQuery = q; search(q); }
        if (closingAt > 0 && System.currentTimeMillis() - closingAt >= 170) { closingAt = 0; this.minecraft.gui.setScreen(parent); }
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
        text(g, this.title.getString(), px + 34, py + oy + 10, Ui.alpha(Hud.GOLD, a));
        text(g, Hud.fit(this.font, subtitle(), pw - 150), px + 34, py + oy + 21, Ui.alpha(0xFF8A8F99, a));
        String[] opt = option();
        if (opt != null) {
            boolean on = opt[1].equals("1");
            int w = this.font.width(opt[0]) + 18, x2 = px + pw - 10, x1 = x2 - w, y = py + oy + 12;
            boolean hov = Ui.inside(mx, my, x1, y, x2, y + 14);
            float sv = anim.to("opt", on ? 1 : 0, 14);
            Ui.round(g, x1, y, x2, y + 14, Ui.alpha(Ui.mix(sv, hov ? 0x30FFFFFF : 0x18FFFFFF, 0x50FF9F1C), a));
            Ui.round(g, x1 + 4, y + 5, x1 + 8, y + 9, Ui.alpha(on ? Hud.ACCENT : 0xFF6F7580, a));
            text(g, opt[0], x1 + 11, y + 3, Ui.alpha(on ? 0xFFFFFFFF : 0xFF8A8F99, a));
            hits.add(new Object[]{new int[]{x1, y, x2, y + 14}, (Runnable) () -> { Ui.click(); toggleOption(); lastQuery = null; }});
        }
        g.fill(px + 8, py + oy + 34, px + pw - 8, py + oy + 35, Ui.alpha(0x22FFFFFF, a));
        box.setY(py + 40 + oy);
        super.extractRenderState(g, mx, my, delta);

        int top = py + 62 + oy, bottom = py + ph - 26 + oy, x = px + 10, w = pw - 20, rowH = 13;
        List<Row> rows = rows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() * rowH - (bottom - top))));
        g.enableScissor(x - 2, top, x + w + 2, bottom);
        int ry = top - (int) scroll;
        for (int i = 0; i < rows.size(); i++, ry += rowH) {
            if (ry + rowH < top || ry > bottom) continue;
            Row r = rows.get(i);
            boolean hov = Ui.inside(mx, my, x, Math.max(ry, top), x + w, Math.min(ry + rowH, bottom));
            float hv = anim.to("r" + i, hov && r.action() != null ? 1 : 0, 18);
            if (i % 2 == 1) g.fill(x, ry, x + w, ry + rowH, Ui.alpha(0x08FFFFFF, a));
            if (hv > 0.01f) Ui.round(g, x, ry, x + w, ry + rowH, Ui.alpha(0x26FF9F1C, a * hv));
            int rw = r.right().isEmpty() ? 0 : this.font.width(r.right()) + 8;
            text(g, Hud.fit(this.font, r.left(), w - rw - 6), x + 3, ry + 3, Ui.alpha(0xFFE6E8EC, a));
            if (!r.right().isEmpty()) text(g, r.right(), x + w - 3 - this.font.width(r.right()), ry + 3, Ui.alpha(r.color(), a));
            if (hov && r.tooltip() != null) tooltip = r.tooltip();
            if (r.action() != null) {
                Runnable act = r.action();
                hits.add(new Object[]{new int[]{x, Math.max(ry, top), x + w, Math.min(ry + rowH, bottom)}, (Runnable) () -> { Ui.click(); act.run(); }});
            }
        }
        g.disableScissor();
        if (rows.size() * rowH > bottom - top) {
            int track = bottom - top, bar = Math.max(16, track * track / (rows.size() * rowH));
            int maxS = rows.size() * rowH - track, by = top + (int) ((track - bar) * (scroll / Math.max(1, maxS)));
            g.fill(px + pw - 6, by, px + pw - 4, by + bar, Ui.alpha(0x80FFFFFF, a));
        }
        text(g, Hud.fit(this.font, footer(), pw - 20), px + 12, py + ph - 18 + oy, Ui.alpha(0xFF6F7580, a));
        if (tooltip != null && a > 0.9f) g.setTooltipForNextFrame(this.font, Component.literal(tooltip), mx, my);
    }

    protected void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        if (ARGB.alpha(color) >= 8) g.text(this.font, s, x, y, color, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (closingAt > 0) return true;
        if (super.mouseClicked(e, doubleClick)) return true;
        if (e.button() == 0) for (Object[] h : List.copyOf(hits)) {
            int[] r = (int[]) h[0];
            if (Ui.inside(e.x(), e.y(), r[0], r[1], r[2], r[3])) { ((Runnable) h[1]).run(); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll - sy * 26);
        return true;
    }

    @Override
    public void onClose() { if (closingAt == 0) closingAt = System.currentTimeMillis(); }

    /** 창을 바로 닫고 다른 일 (명령어 보내기 등) */
    protected void closeNow() { this.minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
