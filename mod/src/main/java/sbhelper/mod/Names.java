package sbhelper.mod;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** 플레이어 이름 자동완성: 지금 탭 목록에 있는 사람 */
public final class Names {
    private static final Set<String> COMMANDS = Set.of("p", "party", "msg", "w", "tell", "message", "invite", "pv", "viewprofile",
        "visit", "trade", "coopadd", "ah", "f", "friend", "ignore", "pinvite");

    /** 고를 이름이 없으면 null */
    public static Suggestions suggest(String text, int cursor) {
        if (!Config.get().nameComplete || !SkyBlock.inSkyblock) return null;
        if (!text.startsWith("/") || cursor != text.length()) return null;
        String[] parts = text.substring(1).split(" ", -1);
        if (parts.length < 2 || !COMMANDS.contains(parts[0].toLowerCase(Locale.ROOT))) return null;
        int start = text.lastIndexOf(' ') + 1;
        String prefix = text.substring(start).toLowerCase(Locale.ROOT);
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            String n = info.getProfile().name();
            if (n == null || !n.matches("[A-Za-z0-9_]{3,16}") || n.equals(mc.getUser().getName())) continue;
            if (n.toLowerCase(Locale.ROOT).startsWith(prefix)) names.add(n);
        }
        if (names.isEmpty()) return null;
        SuggestionsBuilder b = new SuggestionsBuilder(text, start);
        names.forEach(b::suggest);
        return b.build();
    }

    private Names() {}
}
