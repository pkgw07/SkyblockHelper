package sbhelper.mod;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 개발 · 기록용 복사 (클립보드)
 *  - 상자 화면에서 Ctrl+C: 마우스 아래 아이템, Ctrl+Shift+C: 화면 전체
 *  - /sbh copy: 내 인벤토리 전체, /sbh entity: 보고 있는 NPC · 몹 · 아머 스탠드 정보
 */
public final class Copy {
    private static String pretty(JsonElement e) { return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(e); }

    public static JsonObject item(ItemStack st) {
        JsonObject o = new JsonObject();
        if (st == null || st.isEmpty()) return o;
        Minecraft mc = Minecraft.getInstance();
        o.addProperty("item", BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
        o.addProperty("count", st.getCount());
        o.addProperty("name", ItemText.name(st));
        JsonArray lore = new JsonArray();
        ItemText.lore(st).forEach(lore::add);
        o.add("lore", lore);
        String id = SkyBlock.itemId(st);
        if (id != null) o.addProperty("skyblockId", id);
        CompoundTag data = ItemText.customData(st);
        if (data != null) o.addProperty("customData", data.toString());
        if (mc.getConnection() != null) {
            ItemStack.OPTIONAL_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, mc.getConnection().registryAccess()), st)
                .result().ifPresent(full -> o.add("full", full));
        }
        return o;
    }

    public static void hoveredItem(Slot s) {
        if (s == null || s.getItem().isEmpty()) return;
        done("아이템", pretty(item(s.getItem())));
    }

    public static void screen(AbstractContainerScreen<?> cs) {
        JsonObject o = new JsonObject();
        o.addProperty("title", SkyBlock.strip(cs.getTitle().getString()));
        JsonArray slots = new JsonArray();
        for (Slot s : cs.getMenu().slots) {
            if (s.getItem().isEmpty()) continue;
            JsonObject so = item(s.getItem());
            so.addProperty("slot", s.index);
            so.addProperty("playerInventory", s.container instanceof Inventory);
            slots.add(so);
        }
        o.add("slots", slots);
        done("화면 전체", pretty(o));
    }

    public static void inventory() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Inventory inv = mc.player.getInventory();
        JsonArray arr = new JsonArray();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            JsonObject so = item(st);
            so.addProperty("slot", i);
            arr.add(so);
        }
        done("인벤토리", pretty(arr));
    }

    /** 보고 있는 엔티티 + 근처 아머 스탠드 이름 (하이픽셀 NPC 이름은 따로 떠 있는 아머 스탠드) */
    public static void entity() {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.crosshairPickEntity;
        if (e == null || mc.level == null) { msg("§c보고 있는 엔티티가 없어요"); return; }
        JsonObject o = entityJson(e);
        JsonArray near = new JsonArray();
        for (Entity n : mc.level.getEntities(e, e.getBoundingBox().inflate(1.5, 3, 1.5))) {
            if (n.hasCustomName()) near.add(SkyBlock.strip(n.getCustomName().getString()));
        }
        o.add("nearbyNames", near);
        done("엔티티", pretty(o));
    }

    private static JsonObject entityJson(Entity e) {
        JsonObject o = new JsonObject();
        o.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
        o.addProperty("name", SkyBlock.strip(e.getName().getString()));
        if (e.hasCustomName()) o.addProperty("customName", SkyBlock.strip(e.getCustomName().getString()));
        o.addProperty("x", Math.round(e.getX() * 100) / 100.0);
        o.addProperty("y", Math.round(e.getY() * 100) / 100.0);
        o.addProperty("z", Math.round(e.getZ() * 100) / 100.0);
        o.addProperty("invisible", e.isInvisible());
        if (e instanceof LivingEntity le) {
            o.addProperty("health", le.getHealth());
            JsonObject eq = new JsonObject();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack st = le.getItemBySlot(slot);
                if (!st.isEmpty()) eq.add(slot.getName(), item(st));
            }
            o.add("equipment", eq);
        }
        return o;
    }

    private static void done(String what, String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        msg("§a" + what + " 정보를 클립보드에 복사했어요");
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6[SkyblockHelper] §f" + s));
    }

    private Copy() {}
}
