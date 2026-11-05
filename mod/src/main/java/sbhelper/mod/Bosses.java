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
 *  - Kat: Kat(Pet Sitter) 화면에서 펫 업그레이드 남은 시간을 읽어 타이머
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
            c.matriarchAt = System.currentTimeMillis() + 24 * 3_600_000L;
            Config.save();
        }
    }

    /** 모드가 직접 세는 타이머 (타이머 HUD 에 같이) */
    public static List<Link.Timer> localTimers() {
        List<Link.Timer> out = new ArrayList<>();
        Config c = Config.get();
        if (c.matriarchAt > 0) out.add(new Link.Timer("matriarch", "Heavy Pearl (Matriarch)", c.matriarchAt, false, c.matriarchAt - 24 * 3_600_000L));
        if (c.katAt > 0) out.add(new Link.Timer("kat", "Kat " + (c.katPet.isEmpty() ? "펫 업그레이드" : c.katPet), c.katAt, false, c.katStart));
        return out;
    }

    private static final Pattern KAT_TIME = Pattern.compile("(?i)(time remaining|ready in|remaining|upgrade(?:s|d)? in|ends? in|will be ready)");

    /** 0.5초마다 열린 상자 화면: Kat(Pet Sitter) 이면 업그레이드 중인 펫의 남은 시간을 읽는다 */
    public static void screen(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs) {
        if (!SkyBlock.inSkyblock) return;
        String title = SkyBlock.strip(cs.getTitle().getString());
        if (!title.contains("Pet Sitter") && !title.contains("Kat")) return;
        long best = -1;
        String pet = "";
        for (net.minecraft.world.inventory.Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory || slot.getItem().isEmpty()) continue;
            for (String line : ItemText.lore(slot.getItem())) {
                if (!KAT_TIME.matcher(line).find()) continue;
                long ms = Tooltips.duration(line);
                if (ms > best) { best = ms; pet = ItemText.name(slot.getItem()); }
            }
        }
        if (best < 60_000L) return;
        Config c = Config.get();
        long at = System.currentTimeMillis() + best;
        if (Math.abs(at - c.katAt) > 120_000L || !pet.equals(c.katPet)) {
            if (c.katAt <= System.currentTimeMillis() || !pet.equals(c.katPet)) c.katStart = System.currentTimeMillis();
            c.katAt = at;
            c.katPet = pet.replaceAll("\\[Lvl \\d+\\] ", "");
            Config.save();
        }
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
