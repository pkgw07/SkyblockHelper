package sbhelper.mod;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * SkyblockHelper 모드: 프로그램과 실시간 연동 (같은 컴퓨터 안에서만).
 * 화면에 정보를 보여 주기만 하고 게임을 대신 조작하지 않는다.
 */
public final class SbhMod implements ClientModInitializer {
    /** fabric.mod.json 의 버전 (빌드할 때 채워짐) */
    public static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("skyblockhelper")
        .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("0.0.0");
    private static KeyMapping toggleHud;
    private static boolean openEditor = false;

    @Override
    public void onInitializeClient() {
        Link.start();
        Updater.start();
        // 게임을 끌 때 받아 둔 새 버전으로 바꾼다
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Updater.applyOnExit());

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("skyblockhelper", "main"));
        toggleHud = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skyblockhelper.hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            SkyBlock.tick(mc);
            while (toggleHud.consumeClick()) {
                Config c = Config.get();
                c.hud = !c.hud;
                Config.save();
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[SkyblockHelper] §fHUD " + (c.hud ? "켜짐" : "꺼짐")));
            }
            // 타이틀 화면에서 구버전이면 업데이트 창 (확인이 끝나는 대로, 한 번만)
            if (!Updater.asked && Updater.available() && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen title) {
                Updater.asked = true;
                mc.gui.setScreen(new UpdateScreen(title));
            }
            // 명령어로 연 편집 화면은 채팅 화면이 닫힌 다음 틱에 연다
            if (openEditor) { openEditor = false; mc.gui.setScreen(new HudEditScreen()); }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skyblockhelper", "hud"), Hud::extract);
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> Tooltips.add(stack, lines));

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?> cs) {
                ScreenEvents.afterBackground(screen).register((s, g, mx, my, d) -> ContainerOverlay.background(cs, g));
                ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> ContainerOverlay.foreground(cs, g));
            }
        });

        // /sbh: 상태 · /sbh hud: HUD 위치 정하기
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
            ClientCommands.literal("sbh")
                .executes(c -> {
                    c.getSource().sendFeedback(Component.literal("§6[SkyblockHelper] §f프로그램 연결: " + (Link.connected ? "§a됨" : "§c안 됨 (프로그램을 켜 주세요)")
                        + " §f· 스카이블록: " + (SkyBlock.inSkyblock ? "§a" + (SkyBlock.area.isEmpty() ? "예" : SkyBlock.area) : "§7아님")
                        + " §f· HUD 켜기/끄기: O · 위치: /sbh hud"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(ClientCommands.literal("hud").executes(c -> { openEditor = true; return Command.SINGLE_SUCCESS; }))
        ));
    }
}
