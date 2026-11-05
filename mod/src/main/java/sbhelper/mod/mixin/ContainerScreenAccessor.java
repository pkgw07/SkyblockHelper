package sbhelper.mod.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 상자 화면의 위치·크기·이름 위치 (칸·글자 위치 계산용) */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos") int sbh$leftPos();
    @Accessor("topPos") int sbh$topPos();
    @Accessor("imageWidth") int sbh$imageWidth();
    @Accessor("titleLabelX") int sbh$titleLabelX();
    @Accessor("titleLabelY") int sbh$titleLabelY();
    @Accessor("titleLabelX") void sbh$setTitleLabelX(int x);
    @Accessor("inventoryLabelX") void sbh$setInventoryLabelX(int x);
    @Accessor("hoveredSlot") Slot sbh$hoveredSlot();
}
