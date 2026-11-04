package sbhelper.mod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 게임 화면에 보이는 정보만 읽는다 (스코어보드, 내 인벤토리, 내가 연 상자). 아무것도 조작하지 않는다.
 */
public final class SkyBlock {
    /** 스코어보드 제목에 SKYBLOCK 이 있으면 스카이블록 안 */
    public static volatile boolean inSkyblock = false;
    public static volatile String area = "";
    private static String lastLoc = "";
    private static String lastInv = "";
    private static String lastContainer = "";
    private static int tick = 0;

    /** 스카이블록 아이템 ID (ExtraAttributes 의 id). 펫은 PET_종류. 스카이블록 아이템이 아니면 null */
    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        String id = tag.getStringOr("id", "");
        if (id.isEmpty()) return null;
        if (id.equals("PET")) {
            try {
                String info = tag.getStringOr("petInfo", "");
                String type = JsonParser.parseString(info).getAsJsonObject().get("type").getAsString();
                return "PET_" + type;
            } catch (Exception e) { return null; }
        }
        return id;
    }

    /** 매 틱: 2초마다 위치·인벤토리, 0.5초마다 열린 상자 */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { inSkyblock = false; return; }
        tick++;
        if (tick % 40 == 0) { readScoreboard(mc); syncInventory(mc); }
        if (tick % 10 == 0) syncContainer(mc);
    }

    private static void readScoreboard(Minecraft mc) {
        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        String title = obj == null ? "" : strip(obj.getDisplayName().getString());
        boolean in = title.toUpperCase(Locale.ROOT).contains("SKYBLOCK");
        String ar = "";
        String purse = "";
        if (obj != null) {
            for (PlayerScoreEntry e : sb.listPlayerScores(obj)) {
                PlayerTeam team = sb.getPlayersTeam(e.owner());
                String line = strip(PlayerTeam.formatNameForTeam(team, Component.literal(e.owner())).getString()).trim();
                // 지역: "⏣ Hub" 같은 줄
                if (line.startsWith("⏣") || line.startsWith("ф")) ar = line.substring(1).trim();
                if (line.startsWith("Purse:") || line.startsWith("Piggy:")) purse = line.substring(line.indexOf(':') + 1).trim();
            }
        }
        inSkyblock = in;
        area = ar;
        String loc = in + "|" + ar + "|" + purse;
        if (!loc.equals(lastLoc)) {
            lastLoc = loc;
            JsonObject m = new JsonObject();
            m.addProperty("t", "loc");
            m.addProperty("sb", in);
            m.addProperty("area", ar);
            m.addProperty("purse", purse);
            Link.send(m);
        }
    }

    private static void syncInventory(Minecraft mc) {
        Inventory inv = mc.player.getInventory();
        Map<String, Long> counts = new TreeMap<>();
        for (int i = 0; i < inv.getContainerSize(); i++) add(counts, inv.getItem(i));
        String key = counts.toString();
        if (key.equals(lastInv)) return;
        lastInv = key;
        JsonObject m = new JsonObject();
        m.addProperty("t", "inv");
        m.add("items", toJson(counts));
        Link.send(m);
    }

    /** 열어 둔 상자(엔더 상자·백팩·창고 등)의 내용: 내 인벤토리 칸은 빼고 */
    private static void syncContainer(Minecraft mc) {
        Screen screen = mc.screen;
        if (!(screen instanceof AbstractContainerScreen<?> cs)) { lastContainer = ""; return; }
        Map<String, Long> counts = new TreeMap<>();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            add(counts, slot.getItem());
        }
        String title = strip(screen.getTitle().getString());
        String key = title + counts;
        if (key.equals(lastContainer)) return;
        lastContainer = key;
        JsonObject m = new JsonObject();
        m.addProperty("t", "container");
        m.addProperty("title", title);
        m.add("items", toJson(counts));
        Link.send(m);
    }

    /** 다시 연결되면 지금 상태를 다시 보낸다 */
    public static void resend() { lastLoc = ""; lastInv = ""; lastContainer = ""; }

    private static void add(Map<String, Long> counts, ItemStack stack) {
        String id = itemId(stack);
        if (id != null) counts.merge(id, (long) stack.getCount(), Long::sum);
    }

    private static JsonObject toJson(Map<String, Long> counts) {
        JsonObject o = new JsonObject();
        counts.forEach(o::addProperty);
        return o;
    }

    static String strip(String s) {
        String t = ChatFormatting.stripFormatting(s);
        return t == null ? "" : t;
    }

    private SkyBlock() {}
}
