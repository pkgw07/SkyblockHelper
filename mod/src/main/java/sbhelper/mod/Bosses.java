package sbhelper.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 보스 · 미니게임 도우미 (몹 이름표 · 채팅 · 지역 이름만 읽음)
 *  - Blaze 슬레이어 불기둥: "3s 8 hits" 이름표를 찾아 화면 가운데에 크게 경고
 *  - Ashfang: 전투 시간 · 체력, 근처 중력 구(Gravity) 위치 표시
 *  - Dojo (Discipline): 좀비 투구에 맞는 칼을 머리 위에
 *  - Kuudra: 들어간 뒤 지난 시간
 *  - Heavy Pearl (Matriarch): Heavy Pearl 을 받으면 24시간 타이머
 *  - Kat · 펫 레벨링 NPC: 펫을 맡긴 화면에서 남은 시간을 읽어 타이머 (프로그램에도 보내 휴대폰 · 알림)
 */
public final class Bosses {
    private static final Pattern PILLAR = Pattern.compile("(\\d+)s\\s+(\\d+)\\s*hits?", Pattern.CASE_INSENSITIVE);
    private static volatile String pillar = null;
    private static volatile int pillarSeconds = 99;
    private static volatile long ashfangStart = 0, ashfangSeen = 0;
    private static volatile String ashfangName = "";
    private static volatile int gravityCount = 0;
    private static volatile double gravityNearest = -1;
    private static volatile long kuudraStart = 0;

    public static void tick(Minecraft mc) {
        Config c = Config.get();
        if (!SkyBlock.inSkyblock) { pillar = null; return; }
        String newPillar = null;
        int bestSec = 99;
        boolean ashfang = false;
        int gravity = 0;
        double nearest = -1;
        boolean dojo = c.dojoHelper && SkyBlock.area.contains("Dojo");
        for (Entity e : mc.level.entitiesForRendering()) {
            double dist = e.distanceTo(mc.player);
            if (dist > 48) continue;
            String name = e.hasCustomName() ? SkyBlock.strip(e.getCustomName().getString()) : "";
            if (c.blazePillar && !name.isEmpty()) {
                Matcher m = PILLAR.matcher(name);
                if (m.find()) {
                    int sec = Integer.parseInt(m.group(1));
                    if (sec < bestSec) { bestSec = sec; newPillar = sec + "초 뒤 폭발 · " + m.group(2) + "번 치기"; }
                }
            }
            if (c.ashfang && !name.isEmpty()) {
                if (name.contains("Ashfang")) { ashfang = true; ashfangName = name; }
                if (name.toLowerCase(Locale.ROOT).contains("gravity")) {
                    gravity++;
                    if (nearest < 0 || dist < nearest) nearest = dist;
                    Gizmos.circle(e.position().add(0, 0.1, 0), 3f, GizmoStyle.stroke(0xFFAA55FF, 2f));
                }
            }
            if (dojo && e instanceof LivingEntity z && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("zombie")) {
                String sword = swordFor(z.getItemBySlot(EquipmentSlot.HEAD));
                if (sword != null) Gizmos.billboardTextOverMob(z, 0, sword, 0xFFFFFF55, 0.6f);
            }
        }
        pillar = newPillar;
        pillarSeconds = bestSec;
        long now = System.currentTimeMillis();
        if (ashfang) { if (ashfangStart == 0 || now - ashfangSeen > 15_000) ashfangStart = now; ashfangSeen = now; }
        else if (now - ashfangSeen > 15_000) ashfangStart = 0;
        gravityCount = gravity;
        gravityNearest = nearest;
        boolean inKuudra = c.kuudraTimer && SkyBlock.area.toLowerCase(Locale.ROOT).contains("kuudra");
        if (inKuudra && kuudraStart == 0) kuudraStart = now;
        if (!inKuudra) kuudraStart = 0;
    }

    /** Dojo Discipline: 가죽 → 나무 칼, 철 → 철 칼, 금 → 금 칼, 다이아 → 다이아 칼 (위키 Dojo) */
    private static String swordFor(ItemStack helmet) {
        if (helmet.isEmpty()) return null;
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(helmet.getItem()).getPath();
        if (path.startsWith("leather")) return "나무 칼";
        if (path.startsWith("iron")) return "철 칼";
        if (path.startsWith("golden")) return "금 칼";
        if (path.startsWith("diamond")) return "다이아 칼";
        return null;
    }

    /** 채팅: Matriarch 에게서 Heavy Pearl 을 받으면 24시간 타이머 */
    public static void chat(String plain) {
        Config c = Config.get();
        String low = plain.toLowerCase(Locale.ROOT);
        if (plain.contains("Heavy Pearl") && !plain.contains(":") && !low.contains("bazaar") && !low.contains("auction") && !low.contains("bought") && !low.contains("sold")
            && (low.contains("found") || low.contains("collected") || low.contains("received") || low.contains("got") || low.contains("matriarch"))) {
            setHeavyPearl(System.currentTimeMillis());
        }
    }

    /** Heavy Pearl 을 받은 시각 → 하루 뒤까지 타이머 (프로그램에도 알려서 휴대폰 · 알림까지) */
    public static void setHeavyPearl(long gotAt) {
        Config c = Config.get();
        c.matriarchAt = gotAt + 24 * 3_600_000L;
        Config.save();
        sendTimer("matriarch", "matriarch", "Heavy Pearl 다시 받기", c.matriarchAt, gotAt);
    }

    private static void sendTimer(String key, String kind, String label, long at, long start) {
        com.google.gson.JsonObject m = new com.google.gson.JsonObject();
        m.addProperty("t", "timer.set");
        m.addProperty("key", key);
        m.addProperty("kind", kind);
        m.addProperty("label", label);
        m.addProperty("at", at);
        m.addProperty("start", start);
        Link.send(m);
    }

    /** /sbh kat 2d 4h: Kat 타이머를 직접 맞추기 (자동으로 안 잡힐 때) */
    public static boolean setKatManually(String text) {
        long ms = Tooltips.duration(text);
        if (ms < 60_000L) return false;
        long now = System.currentTimeMillis();
        Config c = Config.get();
        Config.PetTimer t = new Config.PetTimer();
        t.label = "Kat · 펫 업그레이드";
        t.at = now + ms;
        t.start = now;
        c.petTimers.put("pet:Kat", t);
        Config.save();
        sendTimer("pet:Kat", "kat", t.label, t.at, t.start);
        return true;
    }

    /** 프로그램과 다시 연결되면 게임에서 본 타이머를 다시 보낸다 */
    public static void resend() {
        Config c = Config.get();
        long now = System.currentTimeMillis();
        if (c.matriarchAt > now) sendTimer("matriarch", "matriarch", "Heavy Pearl 다시 받기", c.matriarchAt, c.matriarchAt - 24 * 3_600_000L);
        c.petTimers.forEach((k, t) -> { if (now - t.at < 86_400_000L) sendTimer(k, "kat", t.label, t.at, t.start); });
    }

    /** 모드가 직접 세는 타이머 (타이머 HUD 에 같이) */
    public static List<Link.Timer> localTimers() {
        List<Link.Timer> out = new ArrayList<>();
        // 프로그램과 연결돼 있으면 프로그램이 API · 게임 정보를 합쳐서 보내 주므로 겹치지 않게 뺀다
        if (Link.connected) return out;
        Config c = Config.get();
        long now = System.currentTimeMillis();
        if (c.matriarchAt > 0 && now - c.matriarchAt < 12 * 3_600_000L)
            out.add(new Link.Timer("matriarch", "Heavy Pearl 다시 받기", c.matriarchAt, c.matriarchAt <= now, c.matriarchAt - 24 * 3_600_000L));
        c.petTimers.forEach((k, t) -> { if (now - t.at < 86_400_000L) out.add(new Link.Timer("kat", t.label, t.at, t.at <= now, t.start)); });
        return out;
    }

    private static final Pattern KAT_TIME = Pattern.compile("(?i)(time remaining|ready in|remaining|upgrad|training|leveling|levelling|will be ready|complete[sd]? in|done in|ends? in|time left|left:|in:|ready:|until)");

    /**
     * 0.5초마다 열린 상자 화면: Kat(Pet Sitter) 나 펫 레벨링 NPC 처럼 펫을 맡기는 화면이면
     * 펫 아이템 설명의 남은 시간을 읽어 타이머로 (경매장 · 바자 화면은 제외)
     */
    public static void screen(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs) {
        if (!SkyBlock.inSkyblock) return;
        String title = SkyBlock.strip(cs.getTitle().getString());
        String low = title.toLowerCase(Locale.ROOT);
        if (low.contains("auction") || low.contains("bazaar") || low.contains("bids") || low.contains("trade")) return;
        long best = -1;
        String pet = "";
        // Kat(Pet Sitter) 화면이면 아무 칸의 남은 시간이라도, 다른 화면이면 펫 아이템의 남은 시간만
        boolean katScreen = title.contains("Pet Sitter") || title.contains("Kat") || low.contains("pet care") || low.contains("sitter");
        for (net.minecraft.world.inventory.Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory || slot.getItem().isEmpty()) continue;
            String name = ItemText.name(slot.getItem());
            boolean isPet = Pets.level(slot.getItem()) > 0 || name.contains("Pet");
            if (!isPet && !katScreen) continue;
            java.util.List<String> lines = new java.util.ArrayList<>(ItemText.lore(slot.getItem()));
            lines.add(name);
            for (String line : lines) {
                if (!katScreen && !KAT_TIME.matcher(line).find()) continue;
                long ms = Tooltips.duration(line);
                if (ms > best) { best = ms; pet = isPet ? name : pet; }
            }
        }
        if (best < 60_000L) return;
        String npc = katScreen ? "Kat" : title.replaceAll("\\s*\\(.*\\)$", "");
        String key = "pet:" + npc;
        String label = npc + " · " + (pet.isEmpty() ? "펫 업그레이드" : pet.replaceAll("\\[Lvl \\d+\\] ", ""));
        long now = System.currentTimeMillis(), at = now + best;
        Config c = Config.get();
        Config.PetTimer old = c.petTimers.get(key);
        if (old != null && Math.abs(at - old.at) <= 120_000L && old.label.equals(label)) return;
        Config.PetTimer t = new Config.PetTimer();
        t.label = label;
        t.at = at;
        t.start = old != null && old.label.equals(label) && old.at > now ? old.start : now;
        c.petTimers.put(key, t);
        Config.save();
        sendTimer(key, "kat", label, t.at, t.start);
    }

    /** 화면 가운데 위: 불기둥 경고, Ashfang · Kuudra 정보 */
    public static void hud(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        String p = pillar;
        if (p != null) {
            float pulse = 0.6f + 0.4f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 150.0));
            int color = pillarSeconds <= 2 ? Ui.alpha(0xFFFF5555, pulse) : 0xFFFFAA00;
            g.pose().pushMatrix();
            g.pose().translate(cx, g.guiHeight() / 2f - 40);
            g.pose().scale(2f, 2f);
            g.text(font, p, -font.width(p) / 2, 0, color, true);
            g.pose().popMatrix();
        }
        List<Hud.Row> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (ashfangStart > 0) {
            rows.add(Hud.Row.head("Ashfang", Fmt.left(now - ashfangStart).replace("완료!", "0초")));
            rows.add(Hud.Row.text(ashfangName, ""));
            if (gravityCount > 0) rows.add(new Hud.Row("중력 구 " + gravityCount + "개", String.format("%.1f칸", gravityNearest), gravityNearest < 4 ? Hud.RED : Hud.SOFT, null, -1));
        }
        if (kuudraStart > 0) rows.add(Hud.Row.head("Kuudra", Fmt.left(now - kuudraStart).replace("완료!", "0초")));
        if (!rows.isEmpty()) {
            int w = Hud.measure(font, rows, 0)[0];
            Hud.box(g, font, cx - w / 2, 22, rows, w);
        }
    }

    private Bosses() {}
}
