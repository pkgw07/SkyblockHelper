package sbhelper.mod.mixin;

import net.minecraft.client.renderer.entity.FishingHookRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import sbhelper.mod.World;

/** 낚싯줄 색 (설정에서 고른 색) */
@Mixin(FishingHookRenderer.class)
public abstract class FishingLineMixin {
    @ModifyArg(method = "stringVertex", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;"), index = 0, require = 0)
    private static int sbh$lineColor(int color) {
        return World.lineColor(color);
    }
}
