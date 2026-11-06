package sbhelper.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 아이템 툴팁
 *  - 시세 (프로그램이 보내 준 바자 · 경매장 · NPC 가격)
 *  - "Ends in: 5h 3m" 같은 남은 시간 → 내 컴퓨터 시각 (경매 종료, 이벤트, 은행 이자, Pet Care 등)
 *  - 아이템을 얻은 날 (아이템에 기록이 있을 때)
 *  - 실험대 · Melody's Harp 화면에서는 툴팁 숨기기
 */
public final class Tooltips {
    /** 남은 시간이 적힌 줄로 볼 말 */
    private static final Pattern WHEN = Pattern.compile("(?i)(ends? in|ending in|starts? in|starting in|interest in|ready in|available in|expires? in|time left|remaining|in:)");
    private static final Pattern UNIT = Pattern.compile("(\\d+)\\s*(d|h|m|s)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOCK = Pattern.compile("\\b(\\d{1,2}):(\\d{2}):(\\d{2})\\b");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN);

    public static void add(ItemStack stack, List<Component> lines) {
        Config c = Config.get();
        if (hidden(c)) { lines.clear(); return; }
        if (c.realTimes && SkyBlock.inSkyblock) realTimes(lines);
        if (c.itemAge && SkyBlock.inSkyblock) itemAge(stack, lines);
        if (c.petPercent && SkyBlock.inSkyblock) petPercent(stack, lines);
        if (c.tooltipPrices) prices(stack, lines);
    }

    /** 실험대 게임 · Melody's Harp 화면에서는 툴팁이 판을 가리지 않게 */
    private static boolean hidden(Config c) {
        Screen s = Minecraft.getInstance().gui.screen();
        if (s == null || !SkyBlock.inSkyblock) return false;
        String t = SkyBlock.strip(s.getTitle().getString());
        if (c.hideExperimentTips && (t.startsWith("Chronomatron (") || t.startsWith("Ultrasequencer (") || t.startsWith("Superpairs ("))) return true;
        return c.hideHarpTips && t.startsWith("Harp -");
    }

    private static void prices(ItemStack stack, List<Component> lines) {
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

    /** 남은 시간 줄 바로 아래에 실제 시각 */
    private static void realTimes(List<Component> lines) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < lines.size(); i++) {
            String t = SkyBlock.strip(lines.get(i).getString());
            if (!WHEN.matcher(t).find() || t.toLowerCase(Locale.ROOT).contains("cooldown")) continue;
            long ms = duration(t);
            if (ms < 60_000L) continue;
            lines.add(i + 1, Component.literal("  → " + clock(now + ms)).withStyle(ChatFormatting.DARK_GRAY));
            i++;
        }
    }

    /** "1d 2h 3m 4s" 또는 "12:34:56" → 밀리초 (없으면 -1) */
    static long duration(String s) {
        Matcher c = CLOCK.matcher(s);
        if (c.find()) return ((Long.parseLong(c.group(1)) * 60 + Long.parseLong(c.group(2))) * 60 + Long.parseLong(c.group(3))) * 1000L;
        Matcher m = UNIT.matcher(s);
        long total = 0;
        boolean any = false;
        while (m.find()) {
            long v = Long.parseLong(m.group(1));
            total += switch (m.group(2).toLowerCase(Locale.ROOT)) { case "d" -> v * 86_400_000L; case "h" -> v * 3_600_000L; case "m" -> v * 60_000L; default -> v * 1000L; };
            any = true;
        }
        return any ? total : -1;
    }

    /** "오늘 오후 3:42", "내일 오전 9:05", "11/8 (토) 오후 2:00" */
    static String clock(long at) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), zone);
        LocalDate today = LocalDate.now(zone);
        String day;
        if (t.toLocalDate().equals(today)) day = "오늘";
        else if (t.toLocalDate().equals(today.plusDays(1))) day = "내일";
        else day = t.getMonthValue() + "/" + t.getDayOfMonth() + " (" + t.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
        return day + " " + t.format(HM);
    }

    /** 펫 이름 옆에 만렙까지 % , 아래에 경험치 기준 % */
    private static void petPercent(ItemStack stack, List<Component> lines) {
        int pct = Pets.levelPercent(stack);
        if (pct < 0 || lines.isEmpty()) return;
        lines.set(0, lines.get(0).copy().append(Component.literal(" " + pct + "%").withStyle(pct >= 100 ? ChatFormatting.GOLD : ChatFormatting.GRAY)));
        double[] xp = Pets.xp(stack);
        if (xp != null && pct < 100) lines.add(1, Component.literal("만렙까지 경험치 " + Fmt.count(xp[0]) + " / " + Fmt.count(xp[1])).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 아이템에 남아 있는 얻은 시각 (ExtraAttributes timestamp) */
    private static void itemAge(ItemStack stack, List<Component> lines) {
        CompoundTag data = ItemText.customData(stack);
        if (data == null || !data.contains("timestamp")) return;
        long at = data.getLongOr("timestamp", 0);
        if (at <= 0) {
            String s = data.getStringOr("timestamp", "");
            try {
                at = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("M/d/yy h:mm a", Locale.US)).atZone(ZoneId.of("America/New_York")).toInstant().toEpochMilli();
            } catch (Exception e) { return; }
        }
        if (at <= 0) return;
        long days = (System.currentTimeMillis() - at) / 86_400_000L;
        LocalDate d = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate();
        String ago = days >= 365 ? (days / 365) + "년 " + ((days % 365) / 30) + "개월 전" : days >= 30 ? (days / 30) + "개월 전" : days + "일 전";
        lines.add(Component.literal("얻은 날 " + d + " (" + ago + ")").withStyle(ChatFormatting.DARK_GRAY));
    }

    private Tooltips() {}
}
