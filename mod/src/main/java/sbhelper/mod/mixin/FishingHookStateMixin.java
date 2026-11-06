package sbhelper.mod.mixin;

import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(FishingHookRenderState.class)
public abstract class FishingHookStateMixin implements sbhelper.mod.HookMine {
    @Unique private boolean sbh$mine;

    @Override public boolean sbh$mine() { return sbh$mine; }
    @Override public void sbh$setMine(boolean mine) { this.sbh$mine = mine; }
}
