package sbhelper.mod;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import sbhelper.mod.mixin.SignEditAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * 표지판 입력 목록: 경매 가격 · 검색어 · 은행 금액 · 바자 주문처럼 표지판에 적는 곳에서
 * 전에 적었던 값을 왼쪽에 보여 주고, 누르면 첫 줄에 채운다 (확인은 직접).
 * 표지판 아래 줄(예: "Enter price")이 같은 곳끼리 기록을 따로 모은다.
 */
public final class Signs {
    private static final int MAX = 8;

    private static String key(String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < lines.length; i++) sb.append(SkyBlock.strip(lines[i] == null ? "" : lines[i]).trim()).append('|');
        return sb.toString();
    }

    public static void init(AbstractSignEditScreen screen) {
        if (!Config.get().signHistory || !SkyBlock.inSkyblock) return;
        SignEditAccessor acc = (SignEditAccessor) screen;
        String key = key(acc.sbh$messages());
        if (key.replace("|", "").isBlank()) return;
        List<String> values = Config.get().signValues.getOrDefault(key, List.of());
        int y = 30;
        for (String v : values) {
            Button b = Button.builder(Component.literal(v), btn -> {
                acc.sbh$setLine(0);
                acc.sbh$setMessage(v);
                Ui.click();
            }).pos(8, y).size(110, 18).build();
            net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(b);
            y += 20;
        }
    }

    /** 표지판을 닫을 때 첫 줄을 기록 */
    public static void removed(AbstractSignEditScreen screen) {
        Config c = Config.get();
        if (!c.signHistory || !SkyBlock.inSkyblock) return;
        String[] lines = ((SignEditAccessor) screen).sbh$messages();
        String key = key(lines);
        String v = lines.length > 0 && lines[0] != null ? lines[0].trim() : "";
        if (v.isEmpty() || key.replace("|", "").isBlank()) return;
        List<String> list = new ArrayList<>(c.signValues.getOrDefault(key, List.of()));
        list.remove(v);
        list.add(0, v);
        while (list.size() > MAX) list.remove(list.size() - 1);
        c.signValues.put(key, list);
        Config.save();
    }

    private Signs() {}
}
