package sbhelper.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import sbhelper.mod.mixin.ContainerScreenAccessor;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 경매장 화면 도우미
 *  - 입찰 상태: 내가 최고 입찰이면 초록 테두리, 밀렸으면 빨간 테두리
 *  - 판매자 보기: 아이템을 가운데 클릭하면 /ah 판매자 (한 번 누르면 한 번)
 *  - 가격 검색: 경매 목록 왼쪽 칸에 "1m-5m" 처럼 적으면 그 가격대 아이템을 초록으로
 */
public final class Auction {
    private static final Pattern COINS = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*coins", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)([kmb]?)$");
    /** 마지막으로 적은 가격대 (화면을 넘겨도 유지) */
    private static String range = "";
    private static EditBox box;

    static boolean isAuction(String title) {
        String t = title.toLowerCase(Locale.ROOT);
        return t.contains("auction") || t.contains("bids");
    }

    static boolean isBrowser(String title) { return title.startsWith("Auctions Browser") || title.startsWith("Auctions:"); }

    // ---------- 입찰 상태 · 가격 검색 강조 (칸 뒤에) ----------

    public static void background(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        Config c = Config.get();
        String title = SkyBlock.strip(cs.getTitle().getString());
        if (!isAuction(title) || !SkyBlock.inSkyblock) return;
        Minecraft mc = Minecraft.getInstance();
        String me = mc.getUser().getName();
        boolean yourBids = title.toLowerCase(Locale.ROOT).contains("your bids");
        double[] r = c.ahPriceSearch && isBrowser(title) ? parseRange(range) : null;
        ContainerScreenAccessor acc = (ContainerScreenAccessor) cs;
        int left = acc.sbh$leftPos(), top = acc.sbh$topPos();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            List<String> lore = ItemText.lore(st);
            int x = left + slot.x, y = top + slot.y;
            if (r != null) {
                double price = price(lore);
                if (price >= 0 && price >= r[0] && price <= r[1]) g.fill(x, y, x + 16, y + 16, 0x7055FF55);
            }
            if (c.ahBidStatus) {
                String bidder = ItemText.playerName(ItemText.field(lore, "Bidder"));
                boolean previous = lore.stream().anyMatch(l -> l.trim().toLowerCase(Locale.ROOT).startsWith("your previous bid"));
                if (bidder != null && bidder.equalsIgnoreCase(me)) frame(g, x, y, Hud.GREEN);
                else if (previous || (yourBids && bidder != null)) frame(g, x, y, Hud.RED);
            }
        }
    }

    private static void frame(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x - 1, y - 1, x + 17, y, color);
        g.fill(x - 1, y + 16, x + 17, y + 17, color);
        g.fill(x - 1, y, x, y + 16, color);
        g.fill(x + 16, y, x + 17, y + 16, color);
    }

    /** BIN 가격 → 최고 입찰 → 시작 입찰 순서 */
    static double price(List<String> lore) {
        for (String label : new String[]{"Buy it now", "Top bid", "Starting bid"}) {
            String v = ItemText.field(lore, label);
            if (v == null) continue;
            Matcher m = COINS.matcher(v);
            if (m.find()) {
                try { return Double.parseDouble(m.group(1).replace(",", "")); } catch (Exception ignored) {}
            }
        }
        return -1;
    }

    /** "1m-5m", "-5m", "1m-", "<5m", ">1m", "5m"(이하) → [최소, 최대] */
    static double[] parseRange(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace(",", "");
        if (t.isEmpty()) return null;
        try {
            if (t.startsWith("<")) return new double[]{0, amount(t.substring(1))};
            if (t.startsWith(">")) return new double[]{amount(t.substring(1)), Double.MAX_VALUE};
            int dash = t.indexOf('-');
            if (dash < 0) return new double[]{0, amount(t)};
            String a = t.substring(0, dash), b = t.substring(dash + 1);
            return new double[]{a.isEmpty() ? 0 : amount(a), b.isEmpty() ? Double.MAX_VALUE : amount(b)};
        } catch (Exception e) { return null; }
    }

    private static double amount(String s) {
        Matcher m = AMOUNT.matcher(s);
        if (!m.matches()) throw new IllegalArgumentException(s);
        double v = Double.parseDouble(m.group(1));
        return switch (m.group(2)) { case "k" -> v * 1e3; case "m" -> v * 1e6; case "b" -> v * 1e9; default -> v; };
    }

    // ---------- 가격 검색 칸 ----------

    /** 경매 목록 화면이 열릴 때 왼쪽에 입력 칸 */
    public static void addSearchBox(AbstractContainerScreen<?> cs) {
        box = null;
        if (!Config.get().ahPriceSearch || !SkyBlock.inSkyblock) return;
        String title = SkyBlock.strip(cs.getTitle().getString());
        if (!isBrowser(title)) return;
        ContainerScreenAccessor acc = (ContainerScreenAccessor) cs;
        int w = 100;
        int x = acc.sbh$leftPos() - w - 8, y = acc.sbh$topPos() + 14;
        if (x < 4) { x = 4; y = 4; }
        Minecraft mc = Minecraft.getInstance();
        EditBox b = new EditBox(mc.font, x, y, w, 16, Component.literal("가격 검색"));
        b.setMaxLength(24);
        b.setHint(Component.literal("가격 예: 1m-5m"));
        b.setValue(range);
        b.setResponder(v -> range = v);
        net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(cs).add(b);
        box = b;
    }

    /** 가격 칸에 글을 쓰는 중이면 상자 단축키(E, 숫자 키)보다 먼저 받는다 */
    public static boolean keyPressed(AbstractContainerScreen<?> cs, net.minecraft.client.input.KeyEvent e) {
        EditBox b = box;
        if (b == null || !b.isFocused() || !net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(cs).contains(b)) return false;
        if (e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { b.setFocused(false); return true; }
        b.keyPressed(e);
        return true;
    }

    /** 가격 칸 설명 */
    public static void foreground(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        EditBox b = box;
        if (b == null || !net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(cs).contains(b)) return;
        g.text(Minecraft.getInstance().font, "가격 검색", b.getX(), b.getY() - 10, Hud.GOLD, true);
    }

    // ---------- 판매자 보기 ----------

    /** 가운데 클릭: 그 아이템 판매자의 경매 목록 */
    public static boolean middleClick(AbstractContainerScreen<?> cs) {
        if (!Config.get().ahSellerView || !SkyBlock.inSkyblock) return false;
        String title = SkyBlock.strip(cs.getTitle().getString());
        if (!isAuction(title)) return false;
        Slot s = ((ContainerScreenAccessor) cs).sbh$hoveredSlot();
        if (s == null || s.container instanceof Inventory) return false;
        String seller = ItemText.playerName(ItemText.field(ItemText.lore(s.getItem()), "Seller"));
        if (seller == null || seller.isEmpty()) return false;
        Ui.click();
        Minecraft.getInstance().getConnection().sendCommand("ah " + seller);
        return true;
    }

    private Auction() {}
}
