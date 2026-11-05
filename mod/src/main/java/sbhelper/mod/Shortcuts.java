package sbhelper.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import sbhelper.mod.mixin.ContainerScreenAccessor;

/** 상자 화면 옆 바로가기: 엔더 체스트 · 제작 · 옷장 · 펫 · 스카이블록 메뉴 · Storage (누르면 그 명령어 한 번) */
public final class Shortcuts {
    private record Entry(String command, String name, ItemStack icon) {}
    private static final Entry[] ENTRIES = {
        e("enderchest", "엔더 체스트", Items.ENDER_CHEST), e("craft", "제작대", Items.CRAFTING_TABLE),
        e("wardrobe", "옷장 (Wardrobe)", Items.LEATHER_CHESTPLATE), e("pets", "펫", Items.BONE),
        e("storage", "Storage", Items.CHEST), e("sbmenu", "스카이블록 메뉴", Items.NETHER_STAR),
    };
    private static final int SIZE = 20, GAP = 2;

    private static Entry e(String c, String n, Item i) { return new Entry(c, n, new ItemStack(i)); }

    private static boolean shown(AbstractContainerScreen<?> cs) {
        return Config.get().shortcuts && SkyBlock.inSkyblock && !Storage.active(cs);
    }

    /** 버튼 자리: 상자 오른쪽 (자리가 없으면 왼쪽) */
    private static int[] origin(AbstractContainerScreen<?> cs) {
        ContainerScreenAccessor acc = (ContainerScreenAccessor) cs;
        int x = acc.sbh$leftPos() + acc.sbh$imageWidth() + 4;
        if (x + SIZE > cs.width - 2) x = acc.sbh$leftPos() - SIZE - 4;
        return new int[]{x, acc.sbh$topPos()};
    }

    public static void draw(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
        if (!shown(cs)) return;
        int[] o = origin(cs);
        for (int i = 0; i < ENTRIES.length; i++) {
            int x = o[0], y = o[1] + i * (SIZE + GAP);
            boolean hov = Ui.inside(mx, my, x, y, x + SIZE, y + SIZE);
            Ui.card(g, x, y, x + SIZE, y + SIZE, hov ? 0xF0303644 : 0xE0181C24, hov ? Hud.ACCENT : 0x40FFFFFF);
            g.item(ENTRIES[i].icon(), x + 2, y + 2);
            if (hov) g.setTooltipForNextFrame(Minecraft.getInstance().font, Component.literal(ENTRIES[i].name() + "  §7/" + ENTRIES[i].command()), mx, my);
        }
    }

    public static boolean click(AbstractContainerScreen<?> cs, double mx, double my) {
        if (!shown(cs)) return false;
        int[] o = origin(cs);
        for (int i = 0; i < ENTRIES.length; i++) {
            int x = o[0], y = o[1] + i * (SIZE + GAP);
            if (!Ui.inside(mx, my, x, y, x + SIZE, y + SIZE)) continue;
            Ui.click();
            Minecraft.getInstance().getConnection().sendCommand(ENTRIES[i].command());
            return true;
        }
        return false;
    }

    private Shortcuts() {}
}
