package sbhelper.mod.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sbhelper.mod.World;

/** 드래곤 전투 중 마우스 휠로 핫바가 넘어가지 않게 (화면이 열려 있을 땐 그대로) */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @org.spongepowered.asm.mixin.Shadow private double xpos;
    @org.spongepowered.asm.mixin.Shadow private double ypos;

    /** 상자 화면끼리 옮겨 갈 때 마우스를 가운데로 보내지 않고 원래 자리로 */
    @Inject(method = "releaseMouse", at = @At("TAIL"), require = 0)
    private void sbh$keepCursor(CallbackInfo ci) {
        double[] p = sbhelper.mod.Cursor.restore();
        if (p == null) return;
        this.xpos = p[0];
        this.ypos = p[1];
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(net.minecraft.client.Minecraft.getInstance().getWindow().handle(), p[0], p[1]);
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbh$onScroll(long window, double dx, double dy, CallbackInfo ci) {
        if (World.lockScroll()) ci.cancel();
    }
}
