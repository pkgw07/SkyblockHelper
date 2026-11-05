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
    /** 채팅 화면이 닫힌 다음 틱에 열 화면: 1 설정, 2 HUD 편집 */
    private static int open = 0;
    private static int screenTick = 0;

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
            if (++screenTick % 10 == 0 && mc.gui.screen() instanceof AbstractContainerScreen<?> shown) Storage.tick(shown);
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
            if (open == 1) { open = 0; mc.gui.setScreen(new SettingsScreen(null)); }
            if (open == 2) { open = 0; mc.gui.setScreen(new HudEditScreen(null)); }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skyblockhelper", "hud"), Hud::extract);
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> Tooltips.add(stack, lines));

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?> cs) {
                ScreenEvents.afterBackground(screen).register((s, g, mx, my, d) -> {
                    Storage.background(cs, g, mx, my);
                    ContainerOverlay.background(cs, g);
                    Auction.background(cs, g);
                });
                ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> {
                    ContainerOverlay.foreground(cs, g);
                    Auction.foreground(cs, g);
                    Storage.foreground(cs, g, mx, my);
                });
                ScreenEvents.remove(screen).register(s -> Storage.save());
                Auction.addSearchBox(cs);
            }
            if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen sign) {
                Signs.init(sign);
                ScreenEvents.remove(screen).register(s -> Signs.removed(sign));
            }
        });

        // /sbh: 설정 창 · /sbh hud: HUD 위치·크기 · /sbh status: 연결 상태 · /sbh <기능>: 켜기/끄기
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> {
            var root = ClientCommands.literal("sbh")
                .executes(c -> { open = 1; return Command.SINGLE_SUCCESS; })
                .then(ClientCommands.literal("hud").executes(c -> { open = 2; return Command.SINGLE_SUCCESS; }))
                .then(ClientCommands.literal("copy").executes(c -> { Copy.inventory(); return Command.SINGLE_SUCCESS; }))
                .then(ClientCommands.literal("entity").executes(c -> { Copy.entity(); return Command.SINGLE_SUCCESS; }))
                .then(ClientCommands.literal("storage").then(ClientCommands.literal("clear").executes(c -> {
                    Storage.clear();
                    c.getSource().sendFeedback(Component.literal("§6[SkyblockHelper] §fStorage 미리보기 기억을 지웠어요"));
                    return Command.SINGLE_SUCCESS;
                })))
                .then(ClientCommands.literal("status").executes(c -> {
                    c.getSource().sendFeedback(Component.literal("§6[SkyblockHelper] §f프로그램 연결: " + (Link.connected ? "§a됨" : "§c안 됨 (프로그램을 켜 주세요)")
                        + " §f· 스카이블록: " + (SkyBlock.inSkyblock ? "§a" + (SkyBlock.area.isEmpty() ? "예" : SkyBlock.area) : "§7아님")
                        + " §f· 모드 " + VERSION));
                    return Command.SINGLE_SUCCESS;
                }));
            for (String[] f : FEATURES) {
                root = root.then(ClientCommands.literal(f[0]).executes(c -> {
                    boolean on = toggle(f[0]);
                    c.getSource().sendFeedback(Component.literal("§6[SkyblockHelper] §f" + f[1] + ": " + (on ? "§a켜짐" : "§c꺼짐")));
                    return Command.SINGLE_SUCCESS;
                }));
            }
            dispatcher.register(root);
        });
    }

    /** /sbh <이름> 으로 켜고 끄는 기능 */
    private static final String[][] FEATURES = {
        {"overlay", "HUD 전체"}, {"plan", "제작 플래너 HUD"}, {"timers", "타이머 HUD"}, {"tooltip", "툴팁 시세"},
        {"value", "상자 가치"}, {"highlight", "부족한 재료 강조"}, {"onlysb", "스카이블록에서만 HUD"},
        {"storagepreview", "Storage 미리보기"}, {"pets", "펫 레벨 배경색"}, {"bids", "입찰 상태"}, {"seller", "판매자 보기"},
        {"ahsearch", "경매장 가격 검색"}, {"signs", "표지판 입력 목록"}, {"times", "남은 시간 → 시각"}, {"age", "얻은 날"},
        {"hideexp", "실험대 툴팁 숨기기"}, {"hideharp", "Harp 툴팁 숨기기"},
    };

    private static boolean toggle(String name) {
        Config c = Config.get();
        boolean on = switch (name) {
            case "overlay" -> c.hud = !c.hud;
            case "plan" -> c.planHud = !c.planHud;
            case "timers" -> c.timerHud = !c.timerHud;
            case "tooltip" -> c.tooltipPrices = !c.tooltipPrices;
            case "value" -> c.containerValue = !c.containerValue;
            case "highlight" -> c.highlightMissing = !c.highlightMissing;
            case "onlysb" -> c.onlyInSkyblock = !c.onlyInSkyblock;
            case "storagepreview" -> c.storagePreview = !c.storagePreview;
            case "pets" -> c.petColors = !c.petColors;
            case "bids" -> c.ahBidStatus = !c.ahBidStatus;
            case "seller" -> c.ahSellerView = !c.ahSellerView;
            case "ahsearch" -> c.ahPriceSearch = !c.ahPriceSearch;
            case "signs" -> c.signHistory = !c.signHistory;
            case "times" -> c.realTimes = !c.realTimes;
            case "age" -> c.itemAge = !c.itemAge;
            case "hideexp" -> c.hideExperimentTips = !c.hideExperimentTips;
            case "hideharp" -> c.hideHarpTips = !c.hideHarpTips;
            default -> false;
        };
        Config.save();
        return on;
    }
}
