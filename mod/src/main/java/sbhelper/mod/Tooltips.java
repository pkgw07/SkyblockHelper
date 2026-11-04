package sbhelper.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 아이템 툴팁 아래 시세 (프로그램이 보내 준 바자·경매장·NPC 가격) */
public final class Tooltips {
    public static void add(ItemStack stack, List<Component> lines) {
        if (!Config.get().tooltipPrices) return;
        String id = SkyBlock.itemId(stack);
        if (id == null) return;
        double[] p = Link.price(id);
        if (p == null) return;
        int n = stack.getCount();
        lines.add(Component.empty());
        if (p[0] > 0 || p[1] > 0) {
            lines.add(Component.literal("바자 즉시 판매 " + Fmt.coins(p[0]) + " · 구매 " + Fmt.coins(p[1])).withStyle(ChatFormatting.GOLD));
        }
        if (p[2] > 0) lines.add(Component.literal("경매장 최저 BIN " + Fmt.coins(p[2])).withStyle(ChatFormatting.GOLD));
        if (p[3] > 0) lines.add(Component.literal("NPC 판매 " + Fmt.coins(p[3])).withStyle(ChatFormatting.YELLOW));
        double v = Link.value(id);
        if (n > 1 && v > 0) lines.add(Component.literal(n + "개 가치 " + Fmt.coins(v * n)).withStyle(ChatFormatting.GRAY));
    }

    private Tooltips() {}
}
