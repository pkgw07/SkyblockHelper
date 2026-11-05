package sbhelper.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 채팅 관련
 *  - 상자 화면(엔더 상자 · 거래 · 은행 · 제작 등)을 열어도 최근 채팅을 왼쪽 아래에 보여 준다
 *  - 플레이어 채팅 끝에 [파티 초대] 를 붙여 누르면 /p invite 이름
 *  - /r 을 마지막으로 귓속말한 상대에게 (/msg 이름)
 */
public final class ChatLog {
    private record Line(Component text, long at) {}
    private static final Deque<Line> recent = new ArrayDeque<>();
    /** "[312] ❤ [MVP+] Name: 내용", "Party > [VIP] Name: 내용", "From [MVP+] Name: 내용" */
    private static final Pattern PLAYER_CHAT = Pattern.compile(
        "^(?:(?:Party|Guild|Officer|Co-op) > |From |To )?(?:\\[\\d{1,3}\\] )?(?:[^\\w\\s\\[]{1,3} )?(?:\\[[A-Z+]{2,}\\+*\\] )?([A-Za-z0-9_]{3,16})(?: \\[[^\\]]+\\])?: ");
    private static final Pattern DM = Pattern.compile("^(From|To) (?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{3,16}): ");
    /** 마지막으로 귓속말을 주고받은 상대 */
    static volatile String lastPartner = null;

    /** 받은 채팅 (게임 메시지) */
    public static void received(Component message) {
        String plain = SkyBlock.strip(message.getString());
        Matcher dm = DM.matcher(plain);
        if (dm.find()) lastPartner = dm.group(2);
        synchronized (recent) {
            recent.addLast(new Line(message, System.currentTimeMillis()));
            while (recent.size() > 50) recent.removeFirst();
        }
        Bosses.chat(plain);
    }

    /** 플레이어 채팅이면 끝에 [파티 초대] 버튼 */
    public static Component modify(Component message) {
        Config c = Config.get();
        if (!c.chatPartyInvite || !SkyBlock.inSkyblock) return message;
        String plain = SkyBlock.strip(message.getString());
        Matcher m = PLAYER_CHAT.matcher(plain);
        if (!m.find()) return message;
        String name = m.group(1);
        Minecraft mc = Minecraft.getInstance();
        if (name.equalsIgnoreCase(mc.getUser().getName())) return message;
        Component button = Component.literal(" [파티 초대]").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.GREEN)
            .withClickEvent(new ClickEvent.RunCommand("/p invite " + name))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(name + " 을(를) 파티에 초대해요"))));
        return message.copy().append(button);
    }

    /** /r 내용 → /msg 마지막상대 내용 */
    public static String modifyCommand(String command) {
        if (!Config.get().replyLast || !SkyBlock.inSkyblock) return command;
        String p = lastPartner;
        if (p == null) return command;
        if (command.equals("r") || command.startsWith("r ")) return "msg " + p + command.substring(1);
        return command;
    }

    /** 내가 보낸 귓속말 상대도 기억 (/msg 이름, /w 이름, /tell 이름) */
    public static void sentCommand(String command) {
        String[] p = command.split(" ", 3);
        if (p.length >= 3 && (p[0].equals("msg") || p[0].equals("w") || p[0].equals("tell") || p[0].equals("message")) && p[1].matches("[A-Za-z0-9_]{3,16}"))
            lastPartner = p[1];
    }

    /** 상자 화면 왼쪽 아래에 최근 채팅 (15초 동안, 끝에 흐려짐) */
    public static void drawInScreen(GuiGraphicsExtractor g, int screenW, int screenH) {
        if (!Config.get().chatInScreens) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        long now = System.currentTimeMillis();
        List<Line> lines = new ArrayList<>();
        synchronized (recent) { for (Line l : recent) if (now - l.at() < 15_000) lines.add(l); }
        if (lines.isEmpty()) return;
        int w = Math.min(320, screenW / 2 - 8);
        List<Object[]> rows = new ArrayList<>(); // [줄, 투명도]
        for (Line l : lines) {
            float a = Math.min(1, (15_000 - (now - l.at())) / 1500f);
            for (FormattedCharSequence seq : font.split(l.text(), w - 6)) rows.add(new Object[]{seq, a});
        }
        int max = 10;
        if (rows.size() > max) rows = rows.subList(rows.size() - max, rows.size());
        // 원래 채팅 자리쯤 (핫바 위), 화면 아래에 붙지 않게
        int y = screenH - 44 - rows.size() * 9;
        for (Object[] r : rows) {
            float a = (float) r[1];
            g.fill(2, y - 1, 2 + w, y + 8, Ui.alpha(0x90000000, a));
            int color = Ui.alpha(0xFFFFFFFF, a);
            if (net.minecraft.util.ARGB.alpha(color) >= 8) g.text(font, (FormattedCharSequence) r[0], 5, y, color, true);
            y += 9;
        }
    }

    private ChatLog() {}
}
