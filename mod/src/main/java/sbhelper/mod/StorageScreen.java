package sbhelper.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Storage 화면 (이 모드 전용). 하이픽셀이 Storage · 엔더 상자 페이지 · 가방을 열면 그 상자 화면 대신 이 화면을 띄운다.
 * 상자 화면이 아니라서 다른 모드가 버튼을 붙이지 않는다.
 *  - 엔더 상자 · 가방 전부를 가로 3개씩, 열린 페이지만 밝게 (나머지는 기억한 내용을 어둡게)
 *  - 다른 페이지를 누르면 그 페이지를 연다 (Storage 화면이면 그 칸을 한 번, 아니면 /enderchest N · /backpack N)
 *  - 열린 페이지와 내 인벤토리 칸은 진짜 칸: 집기 · 놓기 · 오른쪽 클릭 · Shift 이동 · 더블 클릭 · 숫자키 · Q 버리기
 *  - 내 인벤토리 오른쪽 위 검색 칸: 찾는 아이템이 든 칸 · 페이지만
 */
public final class StorageScreen extends Screen {
    private static final int S = 18, PAGE_W = 9 * S + 10, HEAD = 14, GAP = 6;
    private static StorageScreen current;

    private final AbstractContainerMenu menu;
    private final String rawTitle;
    private final String cur;
    private EditBox search;
    private double scroll;
    private boolean scrolledToCurrent = false;
    private int areaTop, areaBottom, invX, invY, maxScroll;
    private final List<Box> boxes = new ArrayList<>();
    private final List<SlotPos> slots = new ArrayList<>();
    private List<Integer> liveRows = List.of();
    private boolean closingByUs = false;
    /** 서버가 연 진짜 상자인지 (테스트에서 만든 가짜 화면은 아님) */
    private final boolean live;

    private record Box(String key, int x, int y, int rows, boolean active, boolean known) {
        int w() { return PAGE_W; }
        int h() { return HEAD + Math.max(1, rows) * S + 6; }
    }

    private record SlotPos(Slot slot, int x, int y) {}

    public StorageScreen(AbstractContainerMenu menu, Component title) {
        super(title);
        this.menu = menu;
        this.rawTitle = SkyBlock.strip(title.getString()).trim();
        this.cur = Storage.keyOf(rawTitle);
        var p = net.minecraft.client.Minecraft.getInstance().player;
        this.live = p != null && p.containerMenu == menu;
    }

    static void querySet() { StorageScreen s = current; if (s != null && s.search != null) s.search.setValue(Storage.query); }

    @Override
    protected void init() {
        current = this;
        // 상자를 닫았다 다시 열면 검색어를 비운다 (페이지를 옮길 때는 유지)
        if (System.currentTimeMillis() - Storage.closedAt > 1500) Storage.query = "";
        search = new EditBox(this.font, 0, 0, 110, 16, Component.literal("아이템 검색"));
        search.setHint(Component.literal("아이템 검색…"));
        search.setMaxLength(40);
        search.setValue(Storage.query);
        search.setResponder(v -> Storage.query = v);
        this.addRenderableWidget(search);
    }

    // ---------- 배치 ----------

    private void layout() {
        Storage.load();
        int W = this.width, H = this.height;
        if (cur != null) liveRows = Storage.contentRows(menu);
        List<String> keys = new ArrayList<>(Storage.menu.keySet());
        if (keys.isEmpty()) keys.addAll(Storage.pages.keySet());
        if (cur != null && !keys.contains(cur)) keys.add(cur);
        keys.sort(Comparator.comparingInt(Storage::order));
        if (Storage.searching()) keys.removeIf(k -> !k.equals(cur) && !Storage.pageHasMatch(k));

        int cols = Math.max(1, Math.min(3, Math.min(Math.max(1, keys.size()), (W - 16 + GAP) / (PAGE_W + GAP))));
        int startX = (W - (cols * PAGE_W + (cols - 1) * GAP)) / 2;
        List<Box> raw = new ArrayList<>();
        int y = 0, rowH = 0;
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            if (i % cols == 0 && i > 0) { y += rowH + GAP; rowH = 0; }
            boolean active = k.equals(cur);
            Storage.Page p = Storage.pages.get(k);
            int rows = active ? liveRows.size() : p != null ? p.rows : k.startsWith("ender") ? 5 : 3;
            Box b = new Box(k, startX + (i % cols) * (PAGE_W + GAP), y, rows, active, p != null || active);
            raw.add(b);
            rowH = Math.max(rowH, b.h());
        }
        int gridH = keys.isEmpty() ? 30 : y + rowH;
        int invH = 4 * S + 4, gap = 34;
        int total = gridH + gap + invH;
        if (total <= H - 16) {
            areaTop = (H - total) / 2;
            areaBottom = areaTop + gridH;
            invY = areaBottom + gap;
        } else {
            invY = H - invH - 10;
            areaTop = 8;
            areaBottom = invY - gap;
        }
        invX = (W - 9 * S) / 2;
        maxScroll = Math.max(0, gridH - (areaBottom - areaTop));
        if (!scrolledToCurrent && cur != null) {
            for (Box b : raw) if (b.active() && b.y() + b.h() > areaBottom - areaTop) scroll = b.y() + b.h() - (areaBottom - areaTop);
            scrolledToCurrent = true;
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        boxes.clear();
        for (Box b : raw) boxes.add(new Box(b.key(), b.x(), areaTop + b.y() - (int) scroll, b.rows(), b.active(), b.known()));

        // 진짜 칸 자리: 열린 페이지 → 그 상자 안, 내 인벤토리 → 아래
        slots.clear();
        Box open = null;
        for (Box b : boxes) if (b.active()) open = b;
        for (Slot s : menu.slots) {
            int i = s.getContainerSlot();
            if (s.container instanceof Inventory) {
                if (i < 9) slots.add(new SlotPos(s, invX + i * S + 1, invY + 3 * S + 4 + 1));
                else if (i < 36) slots.add(new SlotPos(s, invX + ((i - 9) % 9) * S + 1, invY + ((i - 9) / 9) * S + 1));
            } else if (open != null) {
                int ri = liveRows.indexOf(i / 9);
                int sy = open.y() + HEAD + ri * S + 1;
                if (ri >= 0 && sy >= areaTop && sy + 16 <= areaBottom && (!Storage.searching() || Storage.matches(s.getItem())))
                    slots.add(new SlotPos(s, open.x() + 5 + (i % 9) * S + 1, sy));
            }
        }
        search.setX(invX + 9 * S + 6 - search.getWidth());
        search.setY(invY - 6 - search.getHeight() - 3);
    }

    // ---------- 그리기 ----------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float d) {
        g.fill(0, 0, this.width, this.height, 0xFF0C0E13);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        layout();
        var font = this.font;
        // 1층: 상자 · 칸 · 기억한 아이템 · 진짜 칸 아이템
        g.enableScissor(0, areaTop - 2, this.width, areaBottom + 2);
        for (Box b : boxes) {
            if (b.y() + b.h() < areaTop || b.y() > areaBottom) continue;
            boolean hov = !b.active() && overBox(b, mx, my);
            int border = b.active() ? Hud.ACCENT : hov ? 0x90FFFFFF : 0x30FFFFFF;
            Ui.card(g, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), b.active() ? 0xF0222733 : 0xF0171A22, border);
            slotGrid(g, b.x() + 5, b.y() + HEAD, b.rows());
            Storage.Page p = Storage.pages.get(b.key());
            if (!b.active() && p != null) {
                for (int i = 0; i < p.items.size(); i++) {
                    ItemStack st = p.items.get(i);
                    if (st.isEmpty() || !Storage.matches(st)) continue;
                    int sx = b.x() + 5 + (i % 9) * S + 1, sy = b.y() + HEAD + (i / 9) * S + 1;
                    if (Storage.searching()) g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0x60FF9F1C);
                    g.item(st, sx, sy);
                    g.itemDecorations(font, st, sx, sy);
                }
            }
        }
        g.disableScissor();
        Ui.card(g, invX - 6, invY - 6, invX + 9 * S + 6, invY + 4 * S + 4 + 6, 0xF0171A22, 0x30FFFFFF);
        slotGrid(g, invX, invY, 3);
        slotGrid(g, invX, invY + 3 * S + 4, 1);
        SlotPos hovered = hoveredSlot(mx, my);
        for (SlotPos sp : slots) {
            ItemStack st = sp.slot().getItem();
            if (sp == hovered) g.fill(sp.x(), sp.y(), sp.x() + 16, sp.y() + 16, 0x50FFFFFF);
            if (st.isEmpty()) continue;
            if (Storage.searching() && !(sp.slot().container instanceof Inventory)) g.fill(sp.x() - 1, sp.y() - 1, sp.x() + 17, sp.y() + 17, 0x60FF9F1C);
            g.item(st, sp.x(), sp.y());
            g.itemDecorations(font, st, sp.x(), sp.y());
        }

        // 2층: 다른 페이지 어둡게 · 이름
        g.nextStratum();
        g.enableScissor(0, areaTop - 2, this.width, areaBottom + 2);
        for (Box b : boxes) {
            if (b.y() + b.h() < areaTop || b.y() > areaBottom) continue;
            boolean hov = !b.active() && overBox(b, mx, my);
            if (!b.active() && !Storage.searching())
                g.fill(b.x() + 5, b.y() + HEAD, b.x() + 5 + 9 * S, b.y() + HEAD + Math.max(1, b.rows()) * S, hov ? 0x60000000 : 0xA0000000);
            g.text(font, Storage.label(b.key()), b.x() + 6, b.y() + 4, b.active() ? Hud.GOLD : hov ? 0xFFFFFFFF : 0xFFB8BCC4, true);
            String right = b.active() ? "열림" : b.known() ? (hov ? "눌러서 열기" : "") : "한 번 열어 주세요";
            if (!right.isEmpty()) g.text(font, right, b.x() + b.w() - 6 - font.width(right), b.y() + 4, b.active() ? Hud.ACCENT : 0xFF8A8F99, true);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int trackH = areaBottom - areaTop, barH = Math.max(20, trackH * trackH / (trackH + maxScroll));
            int barY = areaTop + (int) ((trackH - barH) * (scroll / maxScroll));
            g.fill(this.width - 5, areaTop, this.width - 3, areaBottom, 0x20FFFFFF);
            g.fill(this.width - 5, barY, this.width - 3, barY + barH, 0x90FFFFFF);
        }
        if (boxes.isEmpty()) g.centeredText(font, Storage.searching() ? "'" + Storage.query.trim() + "' 이(가) 든 엔더 상자 · 가방이 없어요"
            : "Storage 를 한 번 열면 엔더 상자 · 가방 목록이 생겨요", this.width / 2, areaTop + 20, 0xFFB8BCC4);

        super.extractRenderState(g, mx, my, delta); // 검색 칸

        // 툴팁 · 들고 있는 아이템
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            if (hovered != null && !hovered.slot().getItem().isEmpty()) g.setTooltipForNextFrame(font, hovered.slot().getItem(), mx, my);
            else {
                ItemStack cached = cachedAt(mx, my);
                if (cached != null) g.setTooltipForNextFrame(font, cached, mx, my);
            }
        } else {
            g.nextStratum();
            g.item(carried, mx - 8, my - 8);
            g.itemDecorations(font, carried, mx - 8, my - 8);
        }
    }

    private static void slotGrid(GuiGraphicsExtractor g, int x, int y, int rows) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) {
                int sx = x + c * S, sy = y + r * S;
                g.fill(sx, sy, sx + S, sy + S, 0x50000000);
                g.fill(sx + 1, sy + 1, sx + S - 1, sy + S - 1, 0x1CFFFFFF);
            }
        }
    }

    private boolean overBox(Box b, double mx, double my) {
        return Ui.inside(mx, my, b.x(), b.y(), b.x() + b.w(), b.y() + b.h()) && my >= areaTop && my <= areaBottom;
    }

    private SlotPos hoveredSlot(double mx, double my) {
        for (SlotPos sp : slots) if (Ui.inside(mx, my, sp.x() - 1, sp.y() - 1, sp.x() + 17, sp.y() + 17)) return sp;
        return null;
    }

    /** 기억한(어두운) 페이지에서 마우스 아래 아이템 */
    private ItemStack cachedAt(double mx, double my) {
        if (my < areaTop || my > areaBottom) return null;
        for (Box b : boxes) {
            if (b.active()) continue;
            int gx = (int) mx - (b.x() + 5), gy = (int) my - (b.y() + HEAD);
            if (gx < 0 || gy < 0 || gx >= 9 * S || gy >= b.rows() * S) continue;
            Storage.Page p = Storage.pages.get(b.key());
            int idx = (gy / S) * 9 + gx / S;
            if (p != null && idx < p.items.size() && !p.items.get(idx).isEmpty() && Storage.matches(p.items.get(idx))) return p.items.get(idx);
        }
        return null;
    }

    // ---------- 입력 ----------

    private void send(Slot slot, int button, ContainerInput input) {
        if (this.minecraft.gameMode == null || this.minecraft.player == null) return;
        this.minecraft.gameMode.handleContainerInput(menu.containerId, slot == null ? -999 : slot.index, button, input, this.minecraft.player);
    }

    private boolean shiftDown() {
        var w = this.minecraft.getWindow();
        return InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true; // 검색 칸
        search.setFocused(false);
        SlotPos sp = hoveredSlot(e.x(), e.y());
        if (sp != null) {
            int b = e.button();
            if (b == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return true;
            if (shiftDown()) send(sp.slot(), b, ContainerInput.QUICK_MOVE);
            else if (doubleClick && b == 0 && !menu.getCarried().isEmpty()) send(sp.slot(), 0, ContainerInput.PICKUP_ALL);
            else send(sp.slot(), b, ContainerInput.PICKUP);
            return true;
        }
        if (e.button() == 0) {
            for (Box b : boxes) {
                if (b.active() || !overBox(b, e.x(), e.y())) continue;
                open(b.key());
                return true;
            }
        }
        return true; // 빈 곳: 아무 일도 없음
    }

    private void open(String key) {
        Ui.click();
        Integer slot = Storage.menu.get(key);
        if (Storage.isMenu(rawTitle) && slot != null && slot < menu.slots.size()) {
            send(menu.slots.get(slot), 0, ContainerInput.PICKUP);
            return;
        }
        String[] p = key.split(":");
        this.minecraft.getConnection().sendCommand((p[0].equals("ender") ? "enderchest " : "backpack ") + p[1]);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - sy * 24));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (search.isFocused()) {
            if (e.key() == GLFW.GLFW_KEY_ESCAPE) { search.setFocused(false); return true; }
            return super.keyPressed(e);
        }
        if (e.key() == GLFW.GLFW_KEY_ESCAPE || e.key() == GLFW.GLFW_KEY_E) { onClose(); return true; }
        var mouse = this.minecraft.mouseHandler;
        double scale = this.minecraft.getWindow().getGuiScale();
        SlotPos sp = hoveredSlot(mouse.xpos() / scale, mouse.ypos() / scale);
        if (sp != null) {
            if (e.key() >= GLFW.GLFW_KEY_1 && e.key() <= GLFW.GLFW_KEY_9) { send(sp.slot(), e.key() - GLFW.GLFW_KEY_1, ContainerInput.SWAP); return true; }
            if (e.key() == GLFW.GLFW_KEY_F) { send(sp.slot(), 40, ContainerInput.SWAP); return true; }
            if (e.key() == GLFW.GLFW_KEY_Q) { send(sp.slot(), (e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 ? 1 : 0, ContainerInput.THROW); return true; }
            boolean ctrl = (e.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
            if (ctrl && e.key() == GLFW.GLFW_KEY_C && Config.get().copyKeys) { Copy.hoveredItem(sp.slot()); return true; }
        }
        return super.keyPressed(e);
    }

    @Override
    public void tick() {
        Storage.snapshot(this, rawTitle, menu);
        // 서버가 상자를 닫았으면 (다른 화면으로 바뀌지 않고) 이 화면도 닫는다
        if (live && this.minecraft.player != null && this.minecraft.player.containerMenu != menu && this.minecraft.gui.screen() == this) this.minecraft.gui.setScreen(null);
    }

    @Override
    public void removed() {
        // 다른 페이지로 옮겨 갈 때 마우스가 가운데로 가지 않게 지금 위치를 기억 (상자 화면과 같은 방식)
        if (live && this.minecraft != null) Cursor.save(this.minecraft.mouseHandler.xpos(), this.minecraft.mouseHandler.ypos());
        Storage.closing(this, rawTitle, menu);
        if (current == this) current = null;
    }

    /** Esc · E: 서버에 상자를 닫는다고 알리고 닫기 */
    @Override
    public void onClose() {
        if (closingByUs) return;
        closingByUs = true;
        if (live && this.minecraft.player != null) this.minecraft.player.closeContainer();
        else this.minecraft.gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
