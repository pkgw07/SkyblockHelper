package sbhelper.mod;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Player Stalking: /sbh stalk 닉네임 → 접속 중인지 · 어느 게임 · 어디인지 (프로그램이 공식 status API 로 확인) */
public final class Stalk {
    public static void ask(String name) {
        if (!Link.connected) { msg("§c프로그램을 켜 주세요 (내 API 키로 확인해요)"); return; }
        JsonObject m = new JsonObject();
        m.addProperty("t", "player.status");
        m.addProperty("name", name);
        Link.send(m);
        msg("§7" + name + " 확인하는 중…");
    }

    static void result(JsonObject m) {
        String name = m.get("name").getAsString();
        if (m.has("error")) { msg("§c" + name + ": " + m.get("error").getAsString()); return; }
        if (!m.get("online").getAsBoolean()) { msg("§7○ §f" + name + " §7접속 안 함"); return; }
        String game = m.get("gameType").getAsString(), mode = m.get("mode").getAsString(), map = m.get("map").getAsString();
        StringBuilder where = new StringBuilder(pretty(game));
        if (!mode.isEmpty()) where.append(" · ").append(pretty(mode));
        if (!map.isEmpty()) where.append(" · ").append(map);
        msg("§a● §f" + name + " §a접속 중 §f" + where);
    }

    private static String pretty(String s) {
        if (s.isEmpty()) return s;
        String t = s.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        StringBuilder b = new StringBuilder();
        for (String w : t.split(" ")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return b.toString().trim();
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[SkyblockHelper] §f" + s)); });
    }

    private Stalk() {}
}
