package sbhelper.mod.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sbhelper.mod.Names;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** /p · /msg · /invite 같은 명령어에서 탭 목록의 플레이어 이름을 자동완성에 더한다 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow @Final private EditBox input;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;

    @Inject(method = "showSuggestions", at = @At("HEAD"), require = 0)
    private void sbh$addNames(boolean narrate, CallbackInfo ci) {
        String text = input.getValue();
        Suggestions extra = Names.suggest(text, input.getCursorPosition());
        if (extra == null || extra.isEmpty()) return;
        Suggestions cur = pendingSuggestions != null && pendingSuggestions.isDone() && !pendingSuggestions.isCompletedExceptionally() ? pendingSuggestions.getNow(null) : null;
        pendingSuggestions = CompletableFuture.completedFuture(cur == null || cur.isEmpty() ? extra : Suggestions.merge(text, List.of(cur, extra)));
    }
}
