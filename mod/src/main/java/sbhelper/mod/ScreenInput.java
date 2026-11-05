package sbhelper.mod;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import sbhelper.mod.mixin.ContainerScreenAccessor;

/** 상자 화면 입력을 기능별로 나눠 준다 (mixin 에서 부름). true 면 원래 동작을 막는다 */
public final class ScreenInput {
    public static boolean mouseClicked(AbstractContainerScreen<?> cs, MouseButtonEvent e) {
        if (Storage.mouseClicked(cs, e)) return true;
        if (e.button() == 0 && Shortcuts.click(cs, e.x(), e.y())) return true;
        return e.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && Auction.middleClick(cs);
    }

    public static boolean mouseScrolled(AbstractContainerScreen<?> cs, double mx, double my, double sy) {
        return Storage.mouseScrolled(cs, sy);
    }

    public static boolean keyPressed(AbstractContainerScreen<?> cs, KeyEvent e) {
        if (Auction.keyPressed(cs, e)) return true;
        boolean ctrl = (e.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        // Ctrl+P: 마우스 아래 아이템을 제작 플래너 목표로
        if (ctrl && e.key() == GLFW.GLFW_KEY_P) {
            var slot = ((ContainerScreenAccessor) cs).sbh$hoveredSlot();
            String id = slot == null ? null : SkyBlock.itemId(slot.getItem());
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (id == null) return true;
            boolean craftable = Link.craftables.stream().anyMatch(c -> c[0].equals(id));
            if (craftable && Link.connected) {
                Link.planCommand("add", id, 1);
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6[SkyblockHelper] §f제작 플래너 목표: §e" + ItemText.name(slot.getItem())));
            } else {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6[SkyblockHelper] §7" + (Link.connected ? "제작 · 포지 레시피가 없는 아이템이에요" : "프로그램을 켜 주세요")));
            }
            return true;
        }
        if (ctrl && e.key() == GLFW.GLFW_KEY_C && Config.get().copyKeys) {
            if ((e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) Copy.screen(cs);
            else Copy.hoveredItem(((ContainerScreenAccessor) cs).sbh$hoveredSlot());
            return true;
        }
        return false;
    }

    public static boolean coversScreen(AbstractContainerScreen<?> cs) { return Storage.active(cs); }

    private ScreenInput() {}
}
