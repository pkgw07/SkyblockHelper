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
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbh$onScroll(long window, double dx, double dy, CallbackInfo ci) {
        if (World.lockScroll()) ci.cancel();
    }
}
