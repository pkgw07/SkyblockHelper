package sbhelper.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 게임을 켰을 때 구버전이면 뜨는 업데이트 창 */
public final class UpdateScreen extends Screen {
    private final Screen parent;
    private String message;
    private Button yes, no;

    public UpdateScreen(Screen parent) {
        super(Component.literal("SkyblockHelper 모드 업데이트"));
        this.parent = parent;
        this.message = "새 버전 " + Updater.latest + " 이 있어요 (지금 " + SbhMod.VERSION + ")";
    }

    @Override
    protected void init() {
        int cx = this.width / 2, y = this.height / 2 + 10;
        yes = this.addRenderableWidget(Button.builder(Component.literal("업데이트"), b -> update()).pos(cx - 104, y).size(100, 20).build());
        no = this.addRenderableWidget(Button.builder(Component.literal("나중에"), b -> onClose()).pos(cx + 4, y).size(100, 20).build());
    }

    private void update() {
        yes.active = false; no.active = false;
        message = "받는 중…";
        Thread t = new Thread(() -> {
            boolean ok = Updater.download();
            message = ok ? "다 받았어요. 게임을 다시 시작하면 적용돼요." : "받지 못했어요. 다음에 게임을 켤 때 다시 물어볼게요.";
            no.active = true;
            no.setMessage(Component.literal("확인"));
        }, "sbh-update-download");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(this.font, this.title.getString(), this.width / 2, this.height / 2 - 40, Hud.GOLD);
        g.centeredText(this.font, message, this.width / 2, this.height / 2 - 20, Hud.WHITE);
        g.centeredText(this.font, "받은 뒤 게임을 다시 시작하면 적용돼요", this.width / 2, this.height / 2 - 8, Hud.GRAY);
    }

    @Override
    public void onClose() { this.minecraft.gui.setScreen(parent); }
}
