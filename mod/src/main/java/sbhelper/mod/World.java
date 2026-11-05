package sbhelper.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 월드에 그리는 것 (게임의 디버그 그리기 Gizmos 사용, 보이는 것만 · 판정은 그대로)
 *  - 드래곤 히트박스 (드래곤이 나타나면 자동), Dragon's Nest 에서 크리스탈 히트박스
 *  - 드래곤 전투 중 마우스 휠로 핫바가 넘어가지 않게
 *  - Builder's Wand · Block Zapper · Bonemerang 범위 미리보기
 *  - 낚싯줄 색
 *  - /sbh island: 섬의 미니언 · 상자 · 호퍼 개수
 */
public final class World {
    private static volatile boolean dragonNear = false;
    /** 낚싯줄 색 고르기 (0 = 원래 색) */
    public static final int[] LINE_COLORS = {0, 0xFFFFFFFF, 0xFFFF5555, 0xFF55FF55, 0xFF5599FF, 0xFFFFFF55, 0xFFFF9F1C, 0xFFFF55FF, 1};
    public static final String[] LINE_NAMES = {"기본", "흰색", "빨강", "초록", "파랑", "노랑", "주황", "분홍", "무지개"};
    private static final int RAINBOW = 8;
    private static int lineVertex = 0;
    private static long lineFrame = 0;
    private static final int LIMIT = 164;

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { dragonNear = false; return; }
        Config c = Config.get();
        boolean dragon = false;
        try (var ignored = mc.collectPerTickGizmos()) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof EnderDragon d) {
                    dragon = true;
                    if (c.dragonHitbox) for (EnderDragonPart p : d.getSubEntities()) Gizmos.cuboid(p.getBoundingBox(), GizmoStyle.stroke(0xFFFF55FF, 2f));
                } else if (e instanceof EndCrystal && c.crystalHitbox && SkyBlock.inSkyblock && SkyBlock.area.contains("Dragon")) {
                    Gizmos.cuboid(e.getBoundingBox(), GizmoStyle.stroke(0xFF55FFFF, 2f));
                }
            }
            if (c.toolRanges && SkyBlock.inSkyblock) tools(mc);
            Bosses.tick(mc);
        } catch (Exception ignored) {}
        dragonNear = dragon;
    }

    /** 드래곤 전투 중엔 휠로 핫바 칸을 바꾸지 않음 */
    public static boolean lockScroll() {
        Minecraft mc = Minecraft.getInstance();
        return Config.get().dragonScrollLock && dragonNear && mc.gui.screen() == null;
    }

    /** 낚싯줄 색. 무지개는 시간에 따라 흐르고, 줄을 따라 색이 이어진다 */
    public static int lineColor(int original) {
        int i = Config.get().fishingLineColor;
        if (i == RAINBOW) {
            long now = System.currentTimeMillis();
            if (now != lineFrame) { lineFrame = now; lineVertex = 0; }
            float hue = ((now % 3000) / 3000f + (lineVertex++) * 0.02f) % 1f;
            return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.85f, 1f) & 0xFFFFFF);
        }
        return i > 0 && i < LINE_COLORS.length ? LINE_COLORS[i] : original;
    }

    // ---------- 도구 범위 ----------

    private static void tools(Minecraft mc) {
        String id = SkyBlock.itemId(mc.player.getMainHandItem());
        if (id == null) return;
        switch (id) {
            case "BUILDERS_WAND" -> wand(mc);
            case "BLOCK_ZAPPER" -> zapper(mc);
            case "BONE_BOOMERANG" -> bonemerang(mc);
            default -> {}
        }
    }

    private static BlockHitResult lookedBlock(Minecraft mc) {
        HitResult h = mc.hitResult;
        return h instanceof BlockHitResult b && h.getType() == HitResult.Type.BLOCK ? b : null;
    }

    /** Builder's Wand: 보고 있는 면과 이어진 같은 블록들 앞에 놓일 자리 */
    private static void wand(Minecraft mc) {
        BlockHitResult hit = lookedBlock(mc);
        if (hit == null) return;
        Direction face = hit.getDirection();
        BlockPos start = hit.getBlockPos();
        Block block = mc.level.getBlockState(start).getBlock();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        int n = 0;
        while (!queue.isEmpty() && n < LIMIT) {
            BlockPos p = queue.poll();
            if (mc.level.getBlockState(p).getBlock() != block || !mc.level.getBlockState(p.relative(face)).isAir()) continue;
            Gizmos.cuboid(p.relative(face), GizmoStyle.stroke(0xFF55FF55, 1.5f));
            n++;
            for (Direction d : Direction.values()) {
                if (d.getAxis() == face.getAxis()) continue;
                BlockPos q = p.relative(d);
                if (seen.add(q)) queue.add(q);
            }
        }
    }

    /** Block Zapper: 이어진 같은 블록 (지워질 것) */
    private static void zapper(Minecraft mc) {
        BlockHitResult hit = lookedBlock(mc);
        if (hit == null) return;
        BlockPos start = hit.getBlockPos();
        Block block = mc.level.getBlockState(start).getBlock();
        if (mc.level.getBlockState(start).isAir()) return;
        Set<BlockPos> seen = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        int n = 0;
        while (!queue.isEmpty() && n < LIMIT) {
            BlockPos p = queue.poll();
            if (mc.level.getBlockState(p).getBlock() != block) continue;
            Gizmos.cuboid(p, GizmoStyle.stroke(0xFFFF5555, 1.5f));
            n++;
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (seen.add(q)) queue.add(q);
            }
        }
    }

    /** Bonemerang: 던졌을 때 지나갈 길과 맞을 몹 */
    private static void bonemerang(Minecraft mc) {
        Player me = mc.player;
        Vec3 start = me.getEyePosition();
        Vec3 end = start.add(me.getLookAngle().scale(15));
        Gizmos.line(start.add(0, -0.3, 0), end, 0xC0FFAA00, 2f);
        AABB area = me.getBoundingBox().expandTowards(me.getLookAngle().scale(15)).inflate(1.5);
        for (Entity e : mc.level.getEntities(me, area)) {
            if (!(e instanceof LivingEntity) || e instanceof ArmorStand || e instanceof Player) continue;
            if (e.getBoundingBox().inflate(0.5).clip(start, end).isPresent()) Gizmos.cuboid(e.getBoundingBox(), GizmoStyle.stroke(0xFFFFAA00, 2f));
        }
    }

    // ---------- 섬 분석 ----------

    /** 불러온 범위 안의 미니언(작은 아머 스탠드 + 머리) · 상자 · 호퍼 */
    public static void island() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int minions = 0;
        Set<String> kinds = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand as) || !as.isSmall()) continue;
            var head = as.getItemBySlot(EquipmentSlot.HEAD);
            if (head.isEmpty() || !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(head.getItem()).getPath().equals("player_head")) continue;
            if (as.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) continue;
            minions++;
            var profile = head.get(net.minecraft.core.component.DataComponents.PROFILE);
            kinds.add(profile == null ? "?" : String.valueOf(profile.hashCode()));
        }
        int chests = 0, hoppers = 0;
        int r = mc.options.renderDistance().get();
        int cx = mc.player.getBlockX() >> 4, cz = mc.player.getBlockZ() >> 4;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (BlockEntity be : mc.level.getChunk(x, z).getBlockEntities().values()) {
                    if (be instanceof ChestBlockEntity) chests++;
                    else if (be instanceof HopperBlockEntity) hoppers++;
                }
            }
        }
        mc.player.sendSystemMessage(Component.literal("§6[SkyblockHelper] §f섬 분석 (불러온 범위): 미니언 §e" + minions + "개§f (생김새 " + kinds.size()
            + "종류) · 상자 §e" + chests + "개§f · 호퍼 §e" + hoppers + "개"));
    }

    private World() {}
}
