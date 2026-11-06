package sbhelper.mod.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sbhelper.mod.World;

/** 낚싯줄 색 (설정에서 고른 색) — 내 낚싯대의 줄만 */
@Mixin(FishingHookRenderer.class)
public abstract class FishingLineMixin {
    /** 찌의 렌더 상태에 '내 것' 표시 */
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/projectile/FishingHook;Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;F)V",
        at = @At("TAIL"), require = 0)
    private void sbh$markMine(FishingHook hook, FishingHookRenderState state, float partial, CallbackInfo ci) {
        ((sbhelper.mod.HookMine) state).sbh$setMine(hook.getPlayerOwner() != null && hook.getPlayerOwner() == Minecraft.getInstance().player);
    }

    /** 줄을 그리는 동안만 '내 줄' 표시 (줄은 나중에 한꺼번에 그려져서 그리는 순간에 알려 준다) */
    @ModifyArg(method = "submit(Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V", ordinal = 1),
        index = 2, require = 0)
    private SubmitNodeCollector.CustomGeometryRenderer sbh$wrapLine(SubmitNodeCollector.CustomGeometryRenderer original, @Local(argsOnly = true) FishingHookRenderState state) {
        boolean mine = ((sbhelper.mod.HookMine) state).sbh$mine();
        return (pose, consumer) -> {
            World.lineMine = mine;
            try { original.render(pose, consumer); } finally { World.lineMine = false; }
        };
    }

    @ModifyArg(method = "stringVertex", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;"), index = 0, require = 0)
    private static int sbh$lineColor(int color) {
        return World.lineColor(color);
    }
}
