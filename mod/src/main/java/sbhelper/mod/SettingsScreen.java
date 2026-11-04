package sbhelper.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;

/** /sbh 로 여는 모드 설정 창 */
public final class SettingsScreen extends Screen {
    private final Screen parent;

    public SettingsScreen(Screen parent) { super(Component.literal("SkyblockHelper 설정")); this.parent = parent; }

    @Override
    protected void init() {
        int col = this.width / 2 - 155, y = 40;
        Config c = Config.get();
        toggle(col, y, "HUD", x -> x.hud, (x) -> x.hud = !x.hud);
        toggle(col + 160, y, "스카이블록에서만", x -> x.onlyInSkyblock, x -> x.onlyInSkyblock = !x.onlyInSkyblock);
        y += 24;
        toggle(col, y, "제작 플래너 HUD", x -> x.planHud, x -> x.planHud = !x.planHud);
        toggle(col + 160, y, "타이머 HUD", x -> x.timerHud, x -> x.timerHud = !x.timerHud);
        y += 24;
        toggle(col, y, "툴팁 시세", x -> x.tooltipPrices, x -> x.tooltipPrices = !x.tooltipPrices);
        toggle(col + 160, y, "상자 가치", x -> x.containerValue, x -> x.containerValue = !x.containerValue);
        y += 24;
        toggle(col, y, "부족한 재료 강조", x -> x.highlightMissing, x -> x.highlightMissing = !x.highlightMissing);
        this.addRenderableWidget(Button.builder(valueLabel(c), b -> { c.valuePosition = 1 - c.valuePosition; Config.save(); b.setMessage(valueLabel(c)); })
            .pos(col + 160, y).size(150, 20).build());
        y += 34;
        this.addRenderableWidget(Button.builder(Component.literal("HUD 위치·크기·너비 편집"), b -> this.minecraft.gui.setScreen(new HudEditScreen(this)))
            .pos(col, y).size(310, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(Component.literal("HUD 크기 기본으로"), b -> {
            c.planScale = 1f; c.timerScale = 1f; c.planWidth = 0; c.timerWidth = 0; Config.save();
        }).pos(col, y).size(150, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("완료"), b -> onClose()).pos(col + 160, y).size(150, 20).build());
    }

    private static Component valueLabel(Config c) {
        return Component.literal("상자 가치 위치: " + (c.valuePosition == 0 ? "이름 옆" : "왼쪽 빈 곳"));
    }

    private void toggle(int x, int y, String name, Function<Config, Boolean> get, Consumer<Config> flip) {
        Config c = Config.get();
        this.addRenderableWidget(Button.builder(label(name, get.apply(c)), b -> {
            flip.accept(c);
            Config.save();
            b.setMessage(label(name, get.apply(c)));
        }).pos(x, y).size(150, 20).build());
    }

    private static Component label(String name, boolean on) { return Component.literal(name + ": " + (on ? "§a켜짐" : "§c꺼짐")); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(this.font, this.title.getString(), this.width / 2, 15, Hud.GOLD);
        g.centeredText(this.font, "프로그램 연결: " + (Link.connected ? "됨" : "안 됨 (SkyblockHelper 프로그램을 켜 주세요)") + " · 모드 " + SbhMod.VERSION,
            this.width / 2, 27, Link.connected ? Hud.GREEN : Hud.GRAY);
    }

    @Override
    public void onClose() { Config.save(); this.minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
