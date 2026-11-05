package sbhelper.mod.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Storage 미리보기에서 칸을 화면의 다른 자리로 옮길 때 */
@Mixin(Slot.class)
public interface SlotAccessor {
    @Mutable @Accessor("x") void sbh$setX(int x);
    @Mutable @Accessor("y") void sbh$setY(int y);
}
