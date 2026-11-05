package sbhelper.mod.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 표지판 입력 목록: 줄 내용 읽기 · 첫 줄 채우기 */
@Mixin(AbstractSignEditScreen.class)
public interface SignEditAccessor {
    @Accessor("messages") String[] sbh$messages();
    @Accessor("line") void sbh$setLine(int line);
    @Invoker("setMessage") void sbh$setMessage(String message);
}
