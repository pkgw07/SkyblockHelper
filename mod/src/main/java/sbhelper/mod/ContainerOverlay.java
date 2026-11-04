package sbhelper.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import sbhelper.mod.mixin.ContainerScreenAccessor;

import java.util.ArrayList;
import java.util.List;

/** 상자 화면: 상자 총가치 (위에 글자), 제작 플래너에 모자란 재료 칸 강조 */
public final class ContainerOverlay {
    private static final int HIGHLIGHT = 0x6655FF55;

    /** 칸 뒤에 (아이템 그림 아래) 초록 강조 */
    public static void background(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        Config c = Config.get();
        Link.Plan plan = Link.plan;
        if (!c.highlightMissing || plan == null) return;
        int left = ((ContainerScreenAccessor) cs).sbh$leftPos(), top = ((ContainerScreenAccessor) cs).sbh$topPos();
        for (Slot slot : cs.getMenu().slots) {
            String id = SkyBlock.itemId(slot.getItem());
            if (id != null && plan.needs(id)) g.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, HIGHLIGHT);
        }
    }

    /** 상자 위쪽에 이 상자의 총가치 (내 인벤토리 칸은 빼고) */
    public static void foreground(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        Config c = Config.get();
        if (!c.containerValue || Link.prices.isEmpty()) return;
        double sum = 0;
        int unpriced = 0;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            String id = SkyBlock.itemId(stack);
            if (id == null) continue;
            double v = Link.value(id);
            if (v < 0) unpriced++; else sum += v * stack.getCount();
        }
        if (sum <= 0 && unpriced == 0) return;
        ContainerScreenAccessor acc = (ContainerScreenAccessor) cs;
        int left = acc.sbh$leftPos(), top = acc.sbh$topPos();
        Font font = Minecraft.getInstance().font;
        String value = Fmt.coins(sum) + (unpriced > 0 ? " (+" + unpriced + ")" : "");
        if (c.valuePosition == 0) {
            // 상자 이름 옆 (예: Ender Chest (1/9)  12.3M). 칸이 모자라면 왼쪽 빈 곳으로
            int x = left + acc.sbh$titleLabelX() + font.width(cs.getTitle()) + 6;
            if (x + font.width(value) <= left + acc.sbh$imageWidth() - 6) {
                g.text(font, value, x, top + acc.sbh$titleLabelY(), Hud.GOLD, false);
                return;
            }
        }
        // 상자 왼쪽 빈 곳에 작은 상자
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"상자 가치", ""});
        lines.add(new String[]{Fmt.coins(sum), ""});
        if (unpriced > 0) lines.add(new String[]{"시세 없음 " + unpriced + "개", ""});
        int w = Hud.measure(font, lines, 0)[0];
        Hud.box(g, font, Math.max(2, left - w - 6), top, lines, w);
    }

    private ContainerOverlay() {}
}
