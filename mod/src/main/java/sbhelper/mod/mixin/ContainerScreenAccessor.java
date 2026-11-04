package sbhelper.mod.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 상자 화면의 왼쪽 위 위치 (칸 위치 계산용) */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos") int sbh$leftPos();
    @Accessor("topPos") int sbh$topPos();
}
