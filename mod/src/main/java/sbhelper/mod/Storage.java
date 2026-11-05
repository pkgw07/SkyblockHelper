package sbhelper.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import sbhelper.mod.mixin.ContainerScreenAccessor;
import sbhelper.mod.mixin.SlotAccessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Storage 미리보기.
 *  - 엔더 체스트 페이지 · 백팩을 열 때마다 안의 아이템을 기억해 둔다 (config/skyblockhelper/storage-플레이어.nbt)
 *  - Storage 화면이나 페이지 · 백팩 화면을 열면 전부를 한 화면에 어둡게 펼친다
 *  - 다른 페이지를 누르면 원래 화면의 그 칸을 한 번 눌러 주거나 (/ec N, /backpack N) 그 페이지를 연다.
 *    열린 페이지는 원래 밝기로, 진짜 칸이라 아이템을 넣고 뺄 수 있다.
 */
public final class Storage {
    private static final Pattern ENDER = Pattern.compile("Ender Chest \\((\\d+)/(\\d+)\\)");
    private static final Pattern BACKPACK = Pattern.compile("Backpack.*\\(Slot #(\\d+)\\)");
    private static final Pattern MENU_ENDER = Pattern.compile("Ender Chest Page (\\d+)");
    private static final Pattern MENU_BACKPACK = Pattern.compile("Backpack Slot (\\d+)");
    private static final Pattern NAV = Pattern.compile("(?i)(previous page|next page|go back|close|back to.*|page \\d+)");
    private static final int S = 18, PAGE_W = 9 * S + 10, HEAD = 14, GAP = 6, OFF = -10000;

    private static final class Page { int rows; List<ItemStack> items = new ArrayList<>(); String sig = ""; }

    private static final Map<String, Page> pages = new HashMap<>();
    /** Storage 화면에서 본 목록: 키 → Storage 화면의 칸 번호 */
    private static final LinkedHashMap<String, Integer> menu = new LinkedHashMap<>();
    private static String loadedFor = null;
    private static boolean dirty = false;

    private record Box(String key, int x, int y, int rows, boolean active, boolean known) {
        int w() { return PAGE_W; }
        int h() { return HEAD + Math.max(1, rows) * S + 6; }
    }

    private static AbstractContainerScreen<?> layoutFor;
    private static final List<Box> boxes = new ArrayList<>();
    private static List<Integer> liveRows = List.of();
    private static int areaTop, areaBottom, invX, invY, maxScroll;
    private static double scroll;

    // ---------- 어떤 화면인지 ----------

    static String keyOf(String title) {
        Matcher m = ENDER.matcher(title);
        if (m.find()) return "ender:" + m.group(1);
        m = BACKPACK.matcher(title);
        if (m.find()) return "backpack:" + m.group(1);
        return null;
    }

    static boolean isMenu(String title) { return title.equals("Storage") || title.startsWith("Storage "); }

    private static String title(AbstractContainerScreen<?> cs) { return SkyBlock.strip(cs.getTitle().getString()).trim(); }

    /** 이 상자 화면을 Storage 미리보기로 그리는지 */
    public static boolean active(AbstractContainerScreen<?> cs) {
        if (!Config.get().storagePreview || !SkyBlock.inSkyblock) return false;
        String t = title(cs);
        return isMenu(t) || keyOf(t) != null;
    }

    private static String label(String key) {
        String[] p = key.split(":");
        return (p[0].equals("ender") ? "엔더 체스트 " : "백팩 ") + p[1];
    }

    private static int order(String key) {
        String[] p = key.split(":");
        int n = 0;
        try { n = Integer.parseInt(p[1]); } catch (Exception ignored) {}
        return (p[0].equals("ender") ? 0 : 1000) + n;
    }

    // ---------- 기억하기 ----------

    /** 0.5초마다: 열린 페이지 내용 · Storage 목록을 기억 */
    public static void tick(AbstractContainerScreen<?> cs) {
        if (!SkyBlock.inSkyblock) return;
        load();
        String t = title(cs);
        if (isMenu(t)) {
            LinkedHashMap<String, Integer> found = new LinkedHashMap<>();
            for (Slot s : cs.getMenu().slots) {
                if (s.container instanceof Inventory) continue;
                String name = ItemText.name(s.getItem());
                if (name.startsWith("Locked") || name.startsWith("Empty")) continue;
                Matcher m = MENU_ENDER.matcher(name);
                if (m.find()) { found.put("ender:" + m.group(1), s.index); continue; }
                m = MENU_BACKPACK.matcher(name);
                if (m.find()) found.put("backpack:" + m.group(1), s.index);
            }
            if (!found.isEmpty() && !found.equals(menu)) { menu.clear(); menu.putAll(found); dirty = true; }
            return;
        }
        String key = keyOf(t);
        if (key == null) return;
        List<Integer> rows = contentRows(cs);
        Map<Integer, Slot> byIndex = containerSlots(cs);
        // 막 열려서 아직 아무것도 안 들어온 화면이면 (이동 버튼 줄도 없음) 기억하지 않는다
        if (rows.size() * 9 >= byIndex.size() && byIndex.values().stream().allMatch(s -> s.getItem().isEmpty())) return;
        List<ItemStack> items = new ArrayList<>();
        StringBuilder sig = new StringBuilder();
        for (int r : rows) {
            for (int c = 0; c < 9; c++) {
                Slot s = byIndex.get(r * 9 + c);
                ItemStack st = s == null ? ItemStack.EMPTY : s.getItem().copy();
                items.add(st);
                sig.append(st.isEmpty() ? "-" : st.getItem() + "x" + st.getCount() + ItemText.name(st)).append('|');
            }
        }
        Page p = pages.computeIfAbsent(key, k -> new Page());
        if (!sig.toString().equals(p.sig)) { p.rows = rows.size(); p.items = items; p.sig = sig.toString(); dirty = true; }
    }

    private static Map<Integer, Slot> containerSlots(AbstractContainerScreen<?> cs) {
        Map<Integer, Slot> out = new HashMap<>();
        for (Slot s : cs.getMenu().slots) if (!(s.container instanceof Inventory)) out.put(s.getContainerSlot(), s);
        return out;
    }

    /** 아이템을 넣는 줄 (유리판 · 이동 버튼만 있는 줄은 뺀다) */
    private static List<Integer> contentRows(AbstractContainerScreen<?> cs) {
        Map<Integer, Slot> byIndex = containerSlots(cs);
        int rows = (byIndex.size() + 8) / 9;
        List<Integer> out = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            boolean ui = true;
            for (int c = 0; c < 9 && ui; c++) {
                Slot s = byIndex.get(r * 9 + c);
                ItemStack st = s == null ? ItemStack.EMPTY : s.getItem();
                if (st.isEmpty() || !(ItemText.isFiller(st) || NAV.matcher(ItemText.name(st)).matches())) ui = false;
            }
            if (!ui) out.add(r);
        }
        return out;
    }

    // ---------- 그리기 ----------

    /** 원래 상자 배경 위에 전체 미리보기를 그리고, 진짜 칸을 그 자리로 옮긴다 */
    public static void background(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
        if (!active(cs)) return;
        load();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        String t = title(cs);
        String cur = keyOf(t);
        boolean first = layoutFor != cs;
        if (first) { layoutFor = cs; liveRows = List.of(); }
        if (cur != null) liveRows = contentRows(cs);
        layout(cs, cur, first);

        int W = cs.width, H = cs.height;
        g.fill(0, 0, W, H, 0xFF0C0E13);

        // 1층: 상자 · 칸 · 기억한 아이템
        g.enableScissor(0, areaTop - 2, W, areaBottom + 2);
        for (Box b : boxes) {
            if (b.y() + b.h() < areaTop || b.y() > areaBottom) continue;
            boolean hov = !b.active() && Ui.inside(mx, my, b.x(), b.y(), b.x() + b.w(), b.y() + b.h()) && my >= areaTop && my <= areaBottom;
            int border = b.active() ? Hud.ACCENT : hov ? 0x90FFFFFF : 0x30FFFFFF;
            Ui.card(g, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), b.active() ? 0xF0222733 : 0xF0171A22, border);
            slotGrid(g, b.x() + 5, b.y() + HEAD, b.rows());
            Page p = pages.get(b.key());
            if (!b.active() && p != null) {
                for (int i = 0; i < p.items.size(); i++) {
                    ItemStack st = p.items.get(i);
                    if (st.isEmpty()) continue;
                    int sx = b.x() + 5 + (i % 9) * S + 1, sy = b.y() + HEAD + (i / 9) * S + 1;
                    g.item(st, sx, sy);
                    g.itemDecorations(font, st, sx, sy);
                }
            }
        }
        g.disableScissor();
        // 내 인벤토리
        Ui.card(g, invX - 6, invY - 6, invX + 9 * S + 6, invY + 4 * S + 4 + 6, 0xF0171A22, 0x30FFFFFF);
        slotGrid(g, invX, invY, 3);
        slotGrid(g, invX, invY + 3 * S + 4, 1);

        // 2층: 어둡게 · 이름
        g.nextStratum();
        g.enableScissor(0, areaTop - 2, W, areaBottom + 2);
        for (Box b : boxes) {
            if (b.y() + b.h() < areaTop || b.y() > areaBottom) continue;
            boolean hov = !b.active() && Ui.inside(mx, my, b.x(), b.y(), b.x() + b.w(), b.y() + b.h()) && my >= areaTop && my <= areaBottom;
            if (!b.active()) g.fill(b.x() + 5, b.y() + HEAD, b.x() + 5 + 9 * S, b.y() + HEAD + Math.max(1, b.rows()) * S, hov ? 0x60000000 : 0xA0000000);
            g.text(font, label(b.key()), b.x() + 6, b.y() + 4, b.active() ? Hud.GOLD : hov ? 0xFFFFFFFF : 0xFFB8BCC4, true);
            String right = b.active() ? "열림" : b.known() ? (hov ? "눌러서 열기" : "") : "한 번 열어 주세요";
            if (!right.isEmpty()) g.text(font, right, b.x() + b.w() - 6 - font.width(right), b.y() + 4, b.active() ? Hud.ACCENT : 0xFF8A8F99, true);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int trackH = areaBottom - areaTop, barH = Math.max(20, trackH * trackH / (trackH + maxScroll));
            int barY = areaTop + (int) ((trackH - barH) * (scroll / maxScroll));
            g.fill(W - 5, areaTop, W - 3, areaBottom, 0x20FFFFFF);
            g.fill(W - 5, barY, W - 3, barY + barH, 0x90FFFFFF);
        }
        if (boxes.isEmpty()) g.centeredText(font, "Storage 를 한 번 열면 엔더 체스트 · 백팩 목록이 생겨요", W / 2, areaTop + 20, 0xFFB8BCC4);

        placeSlots(cs, cur);
    }

    /** 진한 칸 격자 */
    private static void slotGrid(GuiGraphicsExtractor g, int x, int y, int rows) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) {
                int sx = x + c * S, sy = y + r * S;
                g.fill(sx, sy, sx + S, sy + S, 0x50000000);
                g.fill(sx + 1, sy + 1, sx + S - 1, sy + S - 1, 0x1CFFFFFF);
            }
        }
    }

    /**
     * 페이지 상자를 가로 3개씩(화면이 좁으면 더 적게) 줄 맞춰 배치하고,
     * 페이지들 + 내 인벤토리 묶음을 화면 가운데에 둔다. 다 안 들어가면 페이지 쪽을 스크롤.
     */
    private static void layout(AbstractContainerScreen<?> cs, String cur, boolean first) {
        int W = cs.width, H = cs.height;
        List<String> keys = new ArrayList<>(menu.keySet());
        if (keys.isEmpty()) keys.addAll(pages.keySet());
        if (cur != null && !keys.contains(cur)) keys.add(cur);
        keys.sort(Comparator.comparingInt(Storage::order));

        int cols = Math.max(1, Math.min(3, Math.min(Math.max(1, keys.size()), (W - 16 + GAP) / (PAGE_W + GAP))));
        int gridW = cols * PAGE_W + (cols - 1) * GAP;
        int startX = (W - gridW) / 2;
        List<Box> raw = new ArrayList<>();
        int y = 0, rowH = 0;
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            if (i % cols == 0 && i > 0) { y += rowH + GAP; rowH = 0; }
            boolean active = k.equals(cur);
            Page p = pages.get(k);
            int rows = active ? liveRows.size() : p != null ? p.rows : k.startsWith("ender") ? 5 : 3;
            Box b = new Box(k, startX + (i % cols) * (PAGE_W + GAP), y, rows, active, p != null || active);
            raw.add(b);
            rowH = Math.max(rowH, b.h());
        }
        int gridH = keys.isEmpty() ? 30 : y + rowH;
        int invH = 4 * S + 4;
        int total = gridH + 16 + invH;
        if (total <= H - 16) {
            // 다 들어가면 묶음째 가운데
            areaTop = (H - total) / 2;
            areaBottom = areaTop + gridH;
            invY = areaBottom + 16;
        } else {
            invY = H - invH - 10;
            areaTop = 8;
            areaBottom = invY - 16;
        }
        invX = (W - 9 * S) / 2;
        maxScroll = Math.max(0, gridH - (areaBottom - areaTop));
        if (first && cur == null) scroll = 0;
        // 새로 연 페이지가 보이게
        if (first && cur != null) {
            for (Box b : raw) if (b.active()) {
                if (b.y() < scroll) scroll = b.y();
                else if (b.y() + b.h() > scroll + (areaBottom - areaTop)) scroll = b.y() + b.h() - (areaBottom - areaTop);
            }
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        boxes.clear();
        for (Box b : raw) boxes.add(new Box(b.key(), b.x(), areaTop + b.y() - (int) scroll, b.rows(), b.active(), b.known()));
    }

    /** 진짜 칸을 미리보기의 자리로: 열린 페이지 → 그 상자 안, 내 인벤토리 → 아래, 나머지 → 화면 밖 */
    private static void placeSlots(AbstractContainerScreen<?> cs, String cur) {
        ContainerScreenAccessor acc = (ContainerScreenAccessor) cs;
        int left = acc.sbh$leftPos(), top = acc.sbh$topPos();
        acc.sbh$setTitleLabelX(OFF);
        acc.sbh$setInventoryLabelX(OFF);
        Box open = null;
        for (Box b : boxes) if (b.active()) open = b;
        for (Slot s : cs.getMenu().slots) {
            int x = OFF, y = OFF;
            int i = s.getContainerSlot();
            if (s.container instanceof Inventory) {
                if (i < 9) { x = invX + i * S + 1; y = invY + 3 * S + 4 + 1; }
                else if (i < 36) { x = invX + ((i - 9) % 9) * S + 1; y = invY + ((i - 9) / 9) * S + 1; }
            } else if (open != null) {
                int ri = liveRows.indexOf(i / 9);
                if (ri >= 0) {
                    int sy = open.y() + HEAD + ri * S + 1;
                    if (sy >= areaTop && sy + 16 <= areaBottom) { x = open.x() + 5 + (i % 9) * S + 1; y = sy; }
                }
            }
            ((SlotAccessor) s).sbh$setX(x == OFF ? OFF : x - left);
            ((SlotAccessor) s).sbh$setY(y == OFF ? OFF : y - top);
        }
    }

    /** 기억한 아이템 위에 마우스를 올리면 툴팁 */
    public static void foreground(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
        if (!active(cs) || layoutFor != cs || my < areaTop || my > areaBottom) return;
        for (Box b : boxes) {
            if (b.active()) continue;
            int gx = mx - (b.x() + 5), gy = my - (b.y() + HEAD);
            if (gx < 0 || gy < 0 || gx >= 9 * S || gy >= b.rows() * S) continue;
            Page p = pages.get(b.key());
            int idx = (gy / S) * 9 + gx / S;
            if (p != null && idx < p.items.size() && !p.items.get(idx).isEmpty())
                g.setTooltipForNextFrame(Minecraft.getInstance().font, p.items.get(idx), mx, my);
        }
    }

    // ---------- 입력 ----------

    /** 다른 페이지를 누르면 연다 (한 번 누르면 한 번만 보냄) */
    public static boolean mouseClicked(AbstractContainerScreen<?> cs, MouseButtonEvent e) {
        if (!active(cs) || layoutFor != cs || e.button() != 0) return false;
        if (e.y() < areaTop || e.y() > areaBottom) return false;
        for (Box b : boxes) {
            if (b.active() || !Ui.inside(e.x(), e.y(), b.x(), b.y(), b.x() + b.w(), b.y() + b.h())) continue;
            open(cs, b.key());
            return true;
        }
        return false;
    }

    private static void open(AbstractContainerScreen<?> cs, String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        Ui.click();
        Integer slot = menu.get(key);
        if (isMenu(title(cs)) && slot != null) {
            mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot, 0, ContainerInput.PICKUP, mc.player);
            return;
        }
        String[] p = key.split(":");
        mc.getConnection().sendCommand((p[0].equals("ender") ? "enderchest " : "backpack ") + p[1]);
    }

    public static boolean mouseScrolled(AbstractContainerScreen<?> cs, double sy) {
        if (!active(cs) || layoutFor != cs) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll - sy * 24));
        return true;
    }

    // ---------- 저장 ----------

    private static Path file(Minecraft mc) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("skyblockhelper").resolve("storage-" + mc.player.getUUID() + ".nbt");
    }

    private static RegistryOps<Tag> ops(Minecraft mc) { return RegistryOps.create(NbtOps.INSTANCE, mc.getConnection().registryAccess()); }

    private static void load() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        String who = mc.player.getUUID().toString();
        if (who.equals(loadedFor)) return;
        loadedFor = who;
        pages.clear();
        menu.clear();
        try {
            Path f = file(mc);
            if (!Files.exists(f)) return;
            CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            RegistryOps<Tag> ops = ops(mc);
            CompoundTag ps = root.getCompoundOrEmpty("pages");
            for (String key : ps.keySet()) {
                CompoundTag pt = ps.getCompoundOrEmpty(key);
                Page p = new Page();
                p.rows = pt.getIntOr("rows", 0);
                for (Tag it : pt.getListOrEmpty("items")) p.items.add(ItemStack.OPTIONAL_CODEC.parse(ops, it).result().orElse(ItemStack.EMPTY));
                pages.put(key, p);
            }
            for (Tag t : root.getListOrEmpty("menu")) {
                if (t instanceof CompoundTag ct) menu.put(ct.getStringOr("key", ""), ct.getIntOr("slot", 0));
            }
            menu.remove("");
        } catch (Exception ignored) {}
    }

    /** 상자를 닫을 때 바뀐 게 있으면 저장 */
    public static void save() {
        Minecraft mc = Minecraft.getInstance();
        if (!dirty || mc.player == null || mc.getConnection() == null) return;
        dirty = false;
        try {
            RegistryOps<Tag> ops = ops(mc);
            CompoundTag root = new CompoundTag();
            CompoundTag ps = new CompoundTag();
            pages.forEach((key, p) -> {
                CompoundTag pt = new CompoundTag();
                pt.putInt("rows", p.rows);
                ListTag items = new ListTag();
                for (ItemStack st : p.items) items.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, st).result().orElse(new CompoundTag()));
                pt.put("items", items);
                ps.put(key, pt);
            });
            root.put("pages", ps);
            ListTag m = new ListTag();
            menu.forEach((k, v) -> { CompoundTag ct = new CompoundTag(); ct.putString("key", k); ct.putInt("slot", v); m.add(ct); });
            root.put("menu", m);
            Path f = file(mc);
            Files.createDirectories(f.getParent());
            NbtIo.writeCompressed(root, f);
        } catch (Exception ignored) {}
    }

    /** /sbh storage clear */
    public static void clear() {
        pages.clear();
        menu.clear();
        dirty = true;
        save();
    }

    private Storage() {}
}
