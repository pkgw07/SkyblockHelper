package sbhelper.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Storage 미리보기의 데이터:
 *  - 엔더 상자 페이지 · 가방을 열 때마다 안의 아이템을 기억해 둔다 (config/skyblockhelper/storage-플레이어.nbt)
 *  - Storage 화면에서 본 엔더 상자 · 가방 목록 (줄이지 않고 더하기만)
 * 화면은 StorageScreen (하이픽셀이 상자를 열면 그 대신 띄우는 이 모드 전용 화면)
 */
public final class Storage {
    private static final Pattern ENDER = Pattern.compile("Ender Chest \\((\\d+)/(\\d+)\\)");
    private static final Pattern BACKPACK = Pattern.compile("Backpack.*\\(Slot #(\\d+)\\)");
    private static final Pattern MENU_ENDER = Pattern.compile("Ender Chest Page (\\d+)");
    private static final Pattern MENU_BACKPACK = Pattern.compile("Backpack Slot (\\d+)");
    private static final Pattern NAV = Pattern.compile("(?i)(previous page|next page|go back|close|back to.*|page \\d+)");

    static final class Page { int rows; List<ItemStack> items = new ArrayList<>(); String sig = ""; }

    static final Map<String, Page> pages = new HashMap<>();
    /** Storage 화면에서 본 목록: 키 → Storage 화면의 칸 번호 */
    static final LinkedHashMap<String, Integer> menu = new LinkedHashMap<>();
    private static String loadedFor = null;
    private static boolean dirty = false;

    private static Object seenScreen;
    private static long seenAt = 0;
    /** 아이템 검색: 페이지를 옮겨 다닐 때만 유지, 상자를 닫았다 다시 열면 비움 */
    static String query = "";
    static long closedAt = 0;

    // ---------- 어떤 화면인지 ----------

    static String keyOf(String title) {
        Matcher m = ENDER.matcher(title);
        if (m.find()) return "ender:" + m.group(1);
        m = BACKPACK.matcher(title);
        if (m.find()) return "backpack:" + m.group(1);
        return null;
    }

    static boolean isMenu(String title) { return title.equals("Storage") || title.startsWith("Storage "); }

    static boolean isStorageTitle(String title) { return isMenu(title) || keyOf(title) != null; }

    /** 이 상자 화면을 Storage 화면으로 바꿔 띄울지 */
    public static boolean active(AbstractContainerScreen<?> cs) {
        if (!Config.get().storagePreview || !SkyBlock.inSkyblock) return false;
        return isStorageTitle(SkyBlock.strip(cs.getTitle().getString()).trim());
    }

    static String label(String key) {
        String[] p = key.split(":");
        return (p[0].equals("ender") ? "엔더 상자 " : "가방 ") + p[1];
    }

    static int order(String key) {
        String[] p = key.split(":");
        int n = 0;
        try { n = Integer.parseInt(p[1]); } catch (Exception ignored) {}
        return (p[0].equals("ender") ? 0 : 1000) + n;
    }

    // ---------- 기억하기 ----------

    /**
     * 매 틱: 열린 페이지 내용 · Storage 목록을 기억.
     * 하이픽셀은 화면을 연 뒤 칸을 조금씩 채워 보내서, 열고 0.4초 안에는 기억하지 않는다.
     */
    public static void snapshot(Object screen, String title, AbstractContainerMenu m) {
        if (!SkyBlock.inSkyblock) return;
        load();
        long now = System.currentTimeMillis();
        if (screen != seenScreen) { seenScreen = screen; seenAt = now; }
        if (now - seenAt < 400) return;
        if (isMenu(title)) {
            LinkedHashMap<String, Integer> found = new LinkedHashMap<>();
            for (Slot s : m.slots) {
                if (s.container instanceof Inventory) continue;
                String name = ItemText.name(s.getItem());
                if (name.startsWith("Locked") || name.startsWith("Empty")) continue;
                Matcher mm = MENU_ENDER.matcher(name);
                if (mm.find()) { found.put("ender:" + mm.group(1), s.index); continue; }
                mm = MENU_BACKPACK.matcher(name);
                if (mm.find()) found.put("backpack:" + mm.group(1), s.index);
            }
            // 본 페이지 목록은 줄이지 않고 더하기만 (화면이 덜 채워졌을 때 페이지가 사라지지 않게)
            for (var e : found.entrySet()) {
                if (!e.getValue().equals(menu.get(e.getKey()))) { menu.put(e.getKey(), e.getValue()); dirty = true; }
            }
            return;
        }
        String key = keyOf(title);
        if (key == null) return;
        List<Integer> rows = contentRows(m);
        Map<Integer, Slot> byIndex = containerSlots(m);
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

    /** 화면이 닫힐 때: 충분히 열려 있었으면 마지막 모습을 한 번 더 기억하고 저장 */
    public static void closing(Object screen, String title, AbstractContainerMenu m) {
        closedAt = System.currentTimeMillis();
        if (screen == seenScreen && System.currentTimeMillis() - seenAt >= 400) snapshot(screen, title, m);
        save();
    }

    static Map<Integer, Slot> containerSlots(AbstractContainerMenu m) {
        Map<Integer, Slot> out = new HashMap<>();
        for (Slot s : m.slots) if (!(s.container instanceof Inventory)) out.put(s.getContainerSlot(), s);
        return out;
    }

    /** 아이템을 넣는 줄 (유리판 · 이동 버튼만 있는 줄은 뺀다) */
    static List<Integer> contentRows(AbstractContainerMenu m) {
        Map<Integer, Slot> byIndex = containerSlots(m);
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

    // ---------- 검색 ----------

    static boolean searching() { return !query.isBlank(); }

    /** 검색어 바꾸기 (자동 테스트용) */
    public static void search(String q) { query = q == null ? "" : q; StorageScreen.querySet(); }

    /** 이름이나 설명에 검색어가 들어 있는지 */
    static boolean matches(ItemStack st) {
        if (!searching()) return true;
        if (st == null || st.isEmpty()) return false;
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (ItemText.name(st).toLowerCase(Locale.ROOT).contains(q)) return true;
        for (String l : ItemText.lore(st)) if (l.toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }

    static boolean pageHasMatch(String key) {
        Page p = pages.get(key);
        if (p == null) return false;
        for (ItemStack st : p.items) if (matches(st)) return true;
        return false;
    }

    // ---------- 저장 ----------

    private static Path file(Minecraft mc) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("skyblockhelper").resolve("storage-" + mc.player.getUUID() + ".nbt");
    }

    private static RegistryOps<Tag> ops(Minecraft mc) { return RegistryOps.create(NbtOps.INSTANCE, mc.getConnection().registryAccess()); }

    static void load() {
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

    /** 바뀐 게 있으면 저장 */
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
            ListTag mt = new ListTag();
            menu.forEach((k, v) -> { CompoundTag ct = new CompoundTag(); ct.putString("key", k); ct.putInt("slot", v); mt.add(ct); });
            root.put("menu", mt);
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
