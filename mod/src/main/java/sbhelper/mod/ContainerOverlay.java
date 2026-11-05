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

    private static final java.util.regex.Pattern PET_LEVEL = java.util.regex.Pattern.compile("\\[Lvl (\\d+)\\]");

    /** 칸 뒤에 (아이템 그림 아래): 모자란 재료 초록 강조, 펫 레벨별 배경색 */
    public static void background(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        Config c = Config.get();
        int left = ((ContainerScreenAccessor) cs).sbh$leftPos(), top = ((ContainerScreenAccessor) cs).sbh$topPos();
        if (c.petColors && SkyBlock.inSkyblock && SkyBlock.strip(cs.getTitle().getString()).startsWith("Pets")) {
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof Inventory) continue;
                java.util.regex.Matcher m = PET_LEVEL.matcher(ItemText.name(slot.getItem()));
                if (!m.find()) continue;
                int lvl = Integer.parseInt(m.group(1));
                int color = lvl >= 100 ? 0x80FFAA00 : Ui.mix(lvl / 100f, 0x70FF5555, 0x7055FF55);
                g.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, color);
            }
        }
        Link.Plan plan = Link.plan;
        if (!c.highlightMissing || plan == null) return;
        for (Slot slot : cs.getMenu().slots) {
            String id = SkyBlock.itemId(slot.getItem());
            if (id != null && plan.needs(id)) g.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, HIGHLIGHT);
        }
    }

    /** 상자 위쪽에 이 상자의 총가치 (내 인벤토리 칸은 빼고) */
    public static void foreground(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        Config c = Config.get();
        petPercents(cs, g, c);
        if (!c.containerValue || Link.prices.isEmpty() || Storage.active(cs)) return;
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
        List<Hud.Row> lines = new ArrayList<>();
        lines.add(Hud.Row.head("상자 가치", ""));
        lines.add(new Hud.Row(Fmt.coins(sum), "", Hud.GOLD, null, -1));
        if (unpriced > 0) lines.add(Hud.Row.text("시세 없음 " + unpriced + "개", ""));
        int w = Hud.measure(font, lines, 0)[0];
        Hud.box(g, font, Math.max(2, left - w - 6), top, lines, w);
    }

    /** Pets 화면: 펫 칸 아래쪽에 만렙까지 % */
    private static void petPercents(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, Config c) {
        if (!c.petPercent || !SkyBlock.inSkyblock || !SkyBlock.strip(cs.getTitle().getString()).startsWith("Pets")) return;
        ContainerScreenAccessor acc = (ContainerScreenAccessor) cs;
        Font font = Minecraft.getInstance().font;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            int pct = Pets.levelPercent(slot.getItem());
            if (pct < 0) continue;
            String s = pct + "%";
            g.pose().pushMatrix();
            g.pose().translate(acc.sbh$leftPos() + slot.x + 16.5f - font.width(s) * 0.5f, acc.sbh$topPos() + slot.y + 12);
            g.pose().scale(0.5f, 0.5f);
            g.text(font, s, 0, 0, pct >= 100 ? Hud.GOLD : 0xFFFFFFFF, true);
            g.pose().popMatrix();
        }
    }

    private ContainerOverlay() {}
}
