package sbhelper.mod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 펫: 이름의 [Lvl N] 로 만렙까지 몇 % (1렙 0%, 만렙 100%), 경험치 기준 % 도 */
public final class Pets {
    private static final Pattern LVL = Pattern.compile("\\[Lvl (\\d+)\\]");
    /** 만렙이 200 인 펫 (위키 Pets) */
    private static final Set<String> LV200 = Set.of("GOLDEN_DRAGON", "JADE_DRAGON", "ROSE_DRAGON");
    /** 만렙(100)까지 필요한 총 경험치 (위키 Pets 의 등급별 표) */
    private static final Map<String, Double> MAX_XP = Map.of(
        "COMMON", 5_624_785d, "UNCOMMON", 8_644_220d, "RARE", 12_626_665d,
        "EPIC", 18_608_500d, "LEGENDARY", 25_353_230d, "MYTHIC", 25_353_230d);
    private static final double MAX_XP_200 = 210_255_385d;

    /** 이름의 레벨 (펫이 아니면 -1) */
    public static int level(ItemStack stack) {
        Matcher m = LVL.matcher(ItemText.name(stack));
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static JsonObject info(ItemStack stack) {
        CompoundTag t = ItemText.customData(stack);
        if (t == null) return null;
        String s = t.getStringOr("petInfo", "");
        if (s.isEmpty()) return null;
        try { return JsonParser.parseString(s).getAsJsonObject(); } catch (Exception e) { return null; }
    }

    public static int maxLevel(ItemStack stack, int level) {
        JsonObject i = info(stack);
        String type = i != null && i.has("type") ? i.get("type").getAsString() : "";
        return LV200.contains(type) || level > 100 ? 200 : 100;
    }

    /** 레벨 기준: (레벨 - 1) / (만렙 - 1) */
    public static int levelPercent(ItemStack stack) {
        int lv = level(stack);
        if (lv < 1) return -1;
        int max = maxLevel(stack, lv);
        return (int) Math.floor(Math.min(1.0, (lv - 1) / (double) (max - 1)) * 100);
    }

    /** 경험치 기준 (아이템에 경험치 기록이 있을 때만, 없으면 -1) */
    public static double xpPercent(ItemStack stack) {
        JsonObject i = info(stack);
        if (i == null || !i.has("exp") || !i.has("tier")) return -1;
        int lv = Math.max(1, level(stack));
        double max = maxLevel(stack, lv) == 200 ? MAX_XP_200 : MAX_XP.getOrDefault(i.get("tier").getAsString(), 0d);
        if (max <= 0) return -1;
        return Math.min(100, i.get("exp").getAsDouble() / max * 100);
    }

    private Pets() {}
}
