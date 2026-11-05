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
    private static int worldTicks = 0;

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
            World.tick(mc);
            if (screenTick % 40 == 0) Music.tick();
            if (++screenTick % 10 == 0 && mc.gui.screen() instanceof AbstractContainerScreen<?> shown) Storage.tick(shown);
            while (toggleHud.consumeClick()) {
                Config c = Config.get();
                c.hud = !c.hud;
                Config.save();
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[SkyblockHelper] §fHUD " + (c.hud ? "켜짐" : "꺼짐")));
            }
            // 구버전이면 업데이트 창 (한 번만): 타이틀 화면, 아니면 서버에 들어와 5초 뒤 (다른 클라이언트는 타이틀 화면이 달라서)
            worldTicks = mc.player == null ? 0 : worldTicks + 1;
            if (!Updater.asked && Updater.available()) {
                if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen title) {
                    Updater.asked = true;
                    mc.gui.setScreen(new UpdateScreen(title));
                } else if (mc.player != null && mc.gui.screen() == null && worldTicks > 100) {
                    Updater.asked = true;
                    mc.gui.setScreen(new UpdateScreen(null));
                }
            }
            // 명령어로 연 편집 화면은 채팅 화면이 닫힌 다음 틱에 연다
            if (open == 1) { open = 0; mc.gui.setScreen(new SettingsScreen(null)); }
            if (open == 2) { open = 0; mc.gui.setScreen(new HudEditScreen(null)); }
            if (open == 3) { open = 0; mc.gui.setScreen(new UpdateScreen(null)); }
            if (open == 4) { open = 0; mc.gui.setScreen(new PlannerScreen(null)); }
            if (open == 5) { open = 0; mc.gui.setScreen(new AuctionSearchScreen(null)); }
            if (open == 6) { open = 0; mc.gui.setScreen(new LogScreen(null)); }
        });

        // 채팅: 기록 · [파티 초대] 버튼 · /r → 마지막 상대
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay) ChatLog.received(message); });
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay ? message : ChatLog.modify(message));
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.MODIFY_COMMAND.register(ChatLog::modifyCommand);
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.COMMAND.register(ChatLog::sentCommand);
        // Ctrl + 우클릭으로 플레이어 파티 초대 (진짜 플레이어만, NPC 제외)
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!level.isClientSide() || !Config.get().ctrlClickInvite || !SkyBlock.inSkyblock) return net.minecraft.world.InteractionResult.PASS;
            if (!(entity instanceof net.minecraft.world.entity.player.Player target) || target == player || target.getUUID().version() != 4) return net.minecraft.world.InteractionResult.PASS;
            Minecraft mc = Minecraft.getInstance();
            if (!InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) && !InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL))
                return net.minecraft.world.InteractionResult.PASS;
            mc.getConnection().sendCommand("p invite " + target.getName().getString());
            return net.minecraft.world.InteractionResult.FAIL;
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
                    Shortcuts.draw(cs, g, mx, my);
                    Enchant.draw(cs, g, mx, my);
                    if (SkyBlock.inSkyblock && !Storage.active(cs)) ChatLog.drawInScreen(g, cs.width, cs.height);
                });
                ScreenEvents.remove(screen).register(s -> Storage.save());
                Auction.addSearchBox(cs);
                Enchant.init(cs);
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
                .then(ClientCommands.literal("update").executes(c -> {
                    if (Updater.available()) open = 3;
                    else c.getSource().sendFeedback(Component.literal("§6[SkyblockHelper] §f" + (Updater.downloaded()
                        ? "새 버전을 받아 뒀어요. 게임을 다시 시작하면 적용돼요."
                        : "최신 버전이에요 (" + VERSION + ")")));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("plan").executes(c -> { open = 4; return Command.SINGLE_SUCCESS; }))
                .then(ClientCommands.literal("ah").executes(c -> { open = 5; return Command.SINGLE_SUCCESS; }))
                .then(ClientCommands.literal("logs").executes(c -> { open = 6; return Command.SINGLE_SUCCESS; }))
                .then(ClientCommands.literal("music").executes(c -> { Music.openFolder(); return Command.SINGLE_SUCCESS; }))
                .then(ClientCommands.literal("stalk").then(ClientCommands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(c -> { Stalk.ask(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name")); return Command.SINGLE_SUCCESS; })))
                .then(ClientCommands.literal("island").executes(c -> { World.island(); return Command.SINGLE_SUCCESS; }))
                .then(ClientCommands.literal("matriarch").executes(c -> {
                    Config.get().matriarchAt = System.currentTimeMillis() + 24 * 3_600_000L;
                    Config.save();
                    c.getSource().sendFeedback(Component.literal("§6[SkyblockHelper] §fMatriarch 타이머를 24시간으로 맞췄어요"));
                    return Command.SINGLE_SUCCESS;
                }))
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
        {"hideexp", "실험대 툴팁 숨기기"}, {"hideharp", "Harp 툴팁 숨기기"}, {"petpct", "펫 만렙 %"},
        {"chat", "상자 화면 채팅"}, {"shortcuts", "바로가기 메뉴"}, {"partyinvite", "채팅 파티 초대"}, {"ctrlinvite", "Ctrl+우클릭 파티 초대"},
        {"reply", "/r 마지막 상대"}, {"names", "이름 자동완성"}, {"dragon", "드래곤 히트박스"}, {"crystal", "크리스탈 히트박스"},
        {"scrolllock", "드래곤 전투 스크롤 잠금"}, {"tools", "도구 범위"}, {"pillar", "Blaze 기둥 경고"}, {"ashfang", "Ashfang"},
        {"dojo", "Dojo 도우미"}, {"kuudra", "Kuudra 타이머"}, {"areamusic", "장소별 음악"}, {"enchant", "마법 부여 도우미"},
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
            case "petpct" -> c.petPercent = !c.petPercent;
            case "chat" -> c.chatInScreens = !c.chatInScreens;
            case "shortcuts" -> c.shortcuts = !c.shortcuts;
            case "partyinvite" -> c.chatPartyInvite = !c.chatPartyInvite;
            case "ctrlinvite" -> c.ctrlClickInvite = !c.ctrlClickInvite;
            case "reply" -> c.replyLast = !c.replyLast;
            case "names" -> c.nameComplete = !c.nameComplete;
            case "dragon" -> c.dragonHitbox = !c.dragonHitbox;
            case "crystal" -> c.crystalHitbox = !c.crystalHitbox;
            case "scrolllock" -> c.dragonScrollLock = !c.dragonScrollLock;
            case "tools" -> c.toolRanges = !c.toolRanges;
            case "pillar" -> c.blazePillar = !c.blazePillar;
            case "ashfang" -> c.ashfang = !c.ashfang;
            case "dojo" -> c.dojoHelper = !c.dojoHelper;
            case "kuudra" -> c.kuudraTimer = !c.kuudraTimer;
            case "areamusic" -> { c.areaMusic = !c.areaMusic; if (!c.areaMusic) Music.stop(); yield c.areaMusic; }
            case "enchant" -> c.enchantPanel = !c.enchantPanel;
            default -> false;
        };
        Config.save();
        return on;
    }
}
