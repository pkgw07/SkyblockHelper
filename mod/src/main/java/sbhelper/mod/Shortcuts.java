package sbhelper.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import sbhelper.mod.mixin.ContainerScreenAccessor;

/**
 * 상자 화면 바로가기 바: 엔더 체스트 · 제작 · 옷장 · 펫 · Storage · 스카이블록 메뉴 (누르면 그 명령어 한 번).
 * 위치: 오른쪽 · 왼쪽 · 위 · 아래 · 자유 (Ctrl 을 누른 채 끌어서 옮김). 아이템 설명(툴팁)보다 아래에 그린다.
 */
public final class Shortcuts {
    private record Entry(String command, String name, ItemStack icon) {}
    private static final Entry[] ENTRIES = {
        e("enderchest", "엔더 체스트", Items.ENDER_CHEST), e("craft", "제작대", Items.CRAFTING_TABLE),
        e("wardrobe", "옷장 (Wardrobe)", Items.LEATHER_CHESTPLATE), e("pets", "펫", Items.BONE),
        e("storage", "Storage", Items.CHEST), e("sbmenu", "스카이블록 메뉴", Items.NETHER_STAR),
    };
    public static final String[] POSITIONS = {"오른쪽", "왼쪽", "위", "아래", "자유"};
    private static final int SIZE = 20, GAP = 2;
    private static boolean dragging = false;
    private static int dragOffX, dragOffY;

    private static Entry e(String c, String n, Item i) { return new Entry(c, n, new ItemStack(i)); }

    private static boolean shown(AbstractContainerScreen<?> cs) {
        return Config.get().shortcuts && SkyBlock.inSkyblock && !Storage.active(cs);
    }

    private static boolean vertical() {
        Config c = Config.get();
        return c.shortcutPos == 0 || c.shortcutPos == 1 || (c.shortcutPos == 4 && c.shortcutVertical);
    }

    private static int length() { return ENTRIES.length * (SIZE + GAP) - GAP; }

    /** 바의 왼쪽 위 (화면 밖으로 나가지 않게) */
    private static int[] origin(AbstractContainerScreen<?> cs) {
        Config c = Config.get();
        ContainerScreenAccessor acc = (ContainerScreenAccessor) cs;
        int left = acc.sbh$leftPos(), top = acc.sbh$topPos(), w = acc.sbh$imageWidth(), h = acc.sbh$imageHeight();
        int x, y;
        switch (c.shortcutPos) {
            case 1 -> { x = left - SIZE - 4; y = top; }
            case 2 -> { x = left + (w - length()) / 2; y = top - SIZE - 4; }
            case 3 -> { x = left + (w - length()) / 2; y = top + h + 4; }
            case 4 -> {
                x = c.shortcutX < 0 ? left + w + 4 : c.shortcutX;
                y = c.shortcutY < 0 ? top : c.shortcutY;
            }
            default -> {
                x = left + w + 4;
                y = top;
                if (x + SIZE > cs.width - 2) x = left - SIZE - 4;
            }
        }
        int bw = vertical() ? SIZE : length(), bh = vertical() ? length() : SIZE;
        x = Math.max(2, Math.min(x, cs.width - bw - 2));
        y = Math.max(2, Math.min(y, cs.height - bh - 2));
        return new int[]{x, y};
    }

    private static int[] cell(int[] o, int i) {
        return vertical() ? new int[]{o[0], o[1] + i * (SIZE + GAP)} : new int[]{o[0] + i * (SIZE + GAP), o[1]};
    }

    public static void draw(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
        if (!shown(cs)) return;
        int[] o = origin(cs);
        for (int i = 0; i < ENTRIES.length; i++) {
            int[] p = cell(o, i);
            boolean hov = Ui.inside(mx, my, p[0], p[1], p[0] + SIZE, p[1] + SIZE);
            int border = dragging ? Hud.ACCENT : hov ? Hud.ACCENT : 0x40FFFFFF;
            Ui.card(g, p[0], p[1], p[0] + SIZE, p[1] + SIZE, hov ? 0xF0303644 : 0xE0181C24, border);
            g.item(ENTRIES[i].icon(), p[0] + 2, p[1] + 2);
            if (hov && !dragging) g.setTooltipForNextFrame(Minecraft.getInstance().font,
                Component.literal(ENTRIES[i].name() + "  §7/" + ENTRIES[i].command() + (Config.get().shortcutPos == 4 ? "  §8Ctrl+끌기로 옮기기" : "")), mx, my);
        }
    }

    private static boolean ctrl() {
        Minecraft mc = Minecraft.getInstance();
        return InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    public static boolean click(AbstractContainerScreen<?> cs, double mx, double my) {
        if (!shown(cs)) return false;
        int[] o = origin(cs);
        int bw = vertical() ? SIZE : length(), bh = vertical() ? length() : SIZE;
        // Ctrl + 누르기: 바를 끌어서 옮기기 (자유 위치로 바뀜)
        if (ctrl() && Ui.inside(mx, my, o[0], o[1], o[0] + bw, o[1] + bh)) {
            Config c = Config.get();
            if (c.shortcutPos != 4) { c.shortcutVertical = vertical(); c.shortcutPos = 4; }
            dragging = true;
            dragOffX = (int) mx - o[0];
            dragOffY = (int) my - o[1];
            return true;
        }
        for (int i = 0; i < ENTRIES.length; i++) {
            int[] p = cell(o, i);
            if (!Ui.inside(mx, my, p[0], p[1], p[0] + SIZE, p[1] + SIZE)) continue;
            Ui.click();
            Minecraft.getInstance().getConnection().sendCommand(ENTRIES[i].command());
            return true;
        }
        return false;
    }

    public static boolean drag(AbstractContainerScreen<?> cs, double mx, double my) {
        if (!dragging) return false;
        Config c = Config.get();
        c.shortcutX = (int) mx - dragOffX;
        c.shortcutY = (int) my - dragOffY;
        return true;
    }

    public static boolean release() {
        if (!dragging) return false;
        dragging = false;
        Config.save();
        return true;
    }

    private Shortcuts() {}
}
