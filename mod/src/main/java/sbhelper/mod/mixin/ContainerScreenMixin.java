package sbhelper.mod.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sbhelper.mod.ScreenInput;

/** 상자 화면 입력: Storage 미리보기 · 판매자 보기 · 가격 검색 칸 · 복사 단축키 (누른 만큼만 처리) */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void sbh$mouseClicked(MouseButtonEvent e, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ScreenInput.mouseClicked((AbstractContainerScreen<?>) (Object) this, e)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void sbh$mouseScrolled(double mx, double my, double sx, double sy, CallbackInfoReturnable<Boolean> cir) {
        if (ScreenInput.mouseScrolled((AbstractContainerScreen<?>) (Object) this, mx, my, sy)) cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void sbh$keyPressed(KeyEvent e, CallbackInfoReturnable<Boolean> cir) {
        if (ScreenInput.keyPressed((AbstractContainerScreen<?>) (Object) this, e)) cir.setReturnValue(true);
    }

    /** Storage 미리보기는 원래 상자보다 넓어서, 바깥을 눌러도 아이템을 버리지 않게 */
    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void sbh$hasClickedOutside(double mx, double my, int left, int top, CallbackInfoReturnable<Boolean> cir) {
        if (ScreenInput.coversScreen((AbstractContainerScreen<?>) (Object) this)) cir.setReturnValue(false);
    }
}
