package sbhelper.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import sbhelper.mod.mixin.ContainerScreenAccessor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 마법 부여 화면 도우미 (하이픽셀 "Enchant Item" 화면): 왼쪽에 이 페이지의 마법 목록을 이름순으로 + 검색 칸.
 * 줄에 마우스를 올리면 그 칸을 표시하고, 누르면 그 칸을 한 번 눌러 준다 (원래 화면과 똑같이 동작).
 */
public final class Enchant {
    private static EditBox box;
    private static String filter = "";
    private record Entry(Slot slot, String name) {}

    static boolean isEnchantScreen(AbstractContainerScreen<?> cs) {
        if (!Config.get().enchantPanel || !SkyBlock.inSkyblock) return false;
        String t = SkyBlock.strip(cs.getTitle().getString());
        return t.startsWith("Enchant Item") || t.startsWith("Enchantment Table") || t.startsWith("Apply Enchantment");
    }

    private static List<Entry> entries(AbstractContainerScreen<?> cs) {
        List<Entry> out = new ArrayList<>();
        String f = filter.trim().toLowerCase(Locale.ROOT);
        for (Slot s : cs.getMenu().slots) {
            if (s.container instanceof Inventory || s.getItem().isEmpty()) continue;
            if (!BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).getPath().equals("enchanted_book")) continue;
            String name = ItemText.name(s.getItem());
            if (!f.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(f)) continue;
            out.add(new Entry(s, name));
        }
        out.sort(Comparator.comparing(Entry::name));
        return out;
    }

    private static int[] panel(AbstractContainerScreen<?> cs) {
        ContainerScreenAccessor acc = (ContainerScreenAccessor) cs;
        int w = 120, x = Math.max(4, acc.sbh$leftPos() - w - 8);
        return new int[]{x, acc.sbh$topPos(), w};
    }

    public static void init(AbstractContainerScreen<?> cs) {
        box = null;
        if (!isEnchantScreen(cs)) return;
        int[] p = panel(cs);
        EditBox b = new EditBox(Minecraft.getInstance().font, p[0], p[1], p[2], 14, Component.literal("마법 검색"));
        b.setHint(Component.literal("마법 검색…"));
        b.setValue(filter);
        b.setResponder(v -> filter = v);
        net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(cs).add(b);
        box = b;
    }

    public static void draw(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
        if (!isEnchantScreen(cs)) return;
        Font font = Minecraft.getInstance().font;
        int[] p = panel(cs);
        List<Entry> list = entries(cs);
        int y = p[1] + 18, rowH = 11;
        int h = Math.max(1, list.size()) * rowH + 6;
        Ui.card(g, p[0], y, p[0] + p[2], y + h, 0xE8141820, 0x40FFFFFF);
        if (list.isEmpty()) g.text(font, filter.isEmpty() ? "이 페이지에 마법이 없어요" : "찾는 마법이 없어요", p[0] + 4, y + 4, 0xFF8A8F99, true);
        ContainerScreenAccessor acc = (ContainerScreenAccessor) cs;
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            int ry = y + 3 + i * rowH;
            boolean hov = Ui.inside(mx, my, p[0], ry, p[0] + p[2], ry + rowH);
            if (hov) {
                Ui.round(g, p[0] + 1, ry, p[0] + p[2] - 1, ry + rowH, 0x40FF9F1C);
                int sx = acc.sbh$leftPos() + e.slot().x, sy = acc.sbh$topPos() + e.slot().y;
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0x80FF9F1C);
                g.setTooltipForNextFrame(font, e.slot().getItem(), mx, my);
            }
            g.text(font, Hud.fit(font, e.name(), p[2] - 8), p[0] + 4, ry + 2, hov ? 0xFFFFFFFF : 0xFFD6D8DC, true);
        }
    }

    public static boolean click(AbstractContainerScreen<?> cs, double mx, double my) {
        if (!isEnchantScreen(cs)) return false;
        int[] p = panel(cs);
        List<Entry> list = entries(cs);
        int y = p[1] + 18, rowH = 11;
        for (int i = 0; i < list.size(); i++) {
            int ry = y + 3 + i * rowH;
            if (!Ui.inside(mx, my, p[0], ry, p[0] + p[2], ry + rowH)) continue;
            Minecraft mc = Minecraft.getInstance();
            Ui.click();
            mc.gameMode.handleContainerInput(cs.getMenu().containerId, list.get(i).slot().index, 0, ContainerInput.PICKUP, mc.player);
            return true;
        }
        return false;
    }

    public static boolean keyPressed(AbstractContainerScreen<?> cs, net.minecraft.client.input.KeyEvent e) {
        EditBox b = box;
        if (b == null || !b.isFocused() || !net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(cs).contains(b)) return false;
        if (e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { b.setFocused(false); return true; }
        b.keyPressed(e);
        return true;
    }

    private Enchant() {}
}
