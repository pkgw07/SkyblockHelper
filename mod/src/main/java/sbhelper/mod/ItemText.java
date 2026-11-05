package sbhelper.mod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** 아이템 이름 · 설명(lore) 을 색 코드 없이 읽기 */
public final class ItemText {
    public static String name(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return SkyBlock.strip(stack.getHoverName().getString()).trim();
    }

    public static List<String> lore(ItemStack stack) {
        List<String> out = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return out;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return out;
        for (Component c : lore.lines()) out.add(SkyBlock.strip(c.getString()));
        return out;
    }

    /** "Seller: [MVP+] Name" 처럼 '이름: 값' 줄의 값 (없으면 null) */
    public static String field(List<String> lore, String label) {
        for (String l : lore) {
            String t = l.trim();
            if (t.regionMatches(true, 0, label + ":", 0, label.length() + 1)) return t.substring(label.length() + 1).trim();
        }
        return null;
    }

    /** "[MVP+] Name" → "Name" */
    public static String playerName(String s) {
        if (s == null) return null;
        String t = s.replaceAll("\\[[^\\]]*\\]", "").trim();
        int sp = t.indexOf(' ');
        return sp > 0 ? t.substring(0, sp) : t;
    }

    public static CompoundTag customData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    /** 빈 이름의 유리판 같은 칸 채우기용 아이템 */
    public static boolean isFiller(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return path.endsWith("stained_glass_pane") && name(stack).isBlank();
    }

    private ItemText() {}
}
