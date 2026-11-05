package sbhelper.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * /sbh 로 여는 모드 설정 창. 탭(일반 · HUD · 타이머 · 상자 · 경매장 · 툴팁 · 도구)과 켜고 끄는 스위치.
 * 열고 닫을 때 미끄러지며 나타나고, 스위치·탭 밑줄·버튼은 부드럽게 움직인다.
 */
public final class SettingsScreen extends Screen {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("skyblockhelper", "icon.png");
    private static final String[] TABS = {"일반", "HUD", "타이머", "상자", "경매장", "툴팁", "채팅", "전투", "도구"};
    private static final int PANEL = 0xF0141820, BORDER = 0x38FFFFFF, CARD = 0x10FFFFFF, CARD_HOVER = 0x24FFFFFF,
        TRACK = 0xFF3A404C, ACCENT = Hud.ACCENT, ACCENT_HI = 0xFFFFBE5C, DARK_TEXT = 0xFF1B1405;
    /** 마지막으로 본 탭 (다시 열면 그대로) */
    private static int tab = 0;

    private final Screen parent;
    private final Ui.Anim anim = new Ui.Anim();
    private long openedAt = System.currentTimeMillis();
    private long closingAt = 0;
    private long tabAt = 0;
    private int tabDir = 1;
    private final List<Hit> hits = new ArrayList<>();
    private String tooltip;

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {}

    public SettingsScreen(Screen parent) { super(Component.literal("SkyblockHelper 설정")); this.parent = parent; }

    @Override
    public void added() {
        super.added();
        openedAt = System.currentTimeMillis();
        closingAt = 0;
    }

    /** 0 (안 보임) ~ 1 (다 열림) */
    private float openness() {
        long now = System.currentTimeMillis();
        float open = Ui.easeOut((now - openedAt) / 260f);
        if (closingAt > 0) open = Math.min(open, 1 - Ui.easeOut((now - closingAt) / 160f));
        return Math.max(0, open);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        float a = openness();
        g.fillGradient(0, 0, this.width, this.height, Ui.alpha(0x88000000, a), Ui.alpha(0xB0000000, a));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        anim.frame();
        hits.clear();
        tooltip = null;
        long now = System.currentTimeMillis();
        float a = openness();
        if (a <= 0.01f) return;

        int pw = Math.min(390, this.width - 24), ph = Math.min(226, this.height - 12);
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2 + Math.round((1 - a) * 16);

        // 창
        Ui.shadow(g, px, py, px + pw, py + ph, a);
        Ui.card(g, px, py, px + pw, py + ph, Ui.alpha(PANEL, a), Ui.alpha(BORDER, a));
        g.fill(px + 2, py, px + pw - 2, py + 1, Ui.alpha(ACCENT, a));
        // 위쪽 선을 따라 지나가는 빛
        int sweep = (int) ((now / 6) % (pw + 80)) - 40;
        for (int i = 0; i < 40; i++) {
            int x = px + sweep + i;
            if (x < px + 2 || x >= px + pw - 2) continue;
            float k = 1 - Math.abs(i - 20) / 20f;
            g.fill(x, py, x + 1, py + 1, Ui.alpha(0xFFFFFFFF, a * k * 0.8f));
        }

        // 머리: 아이콘 · 이름 · 버전 · 연결 상태
        float pop = Ui.easeBack((now - openedAt) / 420f);
        g.pose().pushMatrix();
        g.pose().translate(px + 20, py + 18);
        g.pose().scale(pop, pop);
        g.blit(RenderPipelines.GUI_TEXTURED, ICON, -10, -10, 0f, 0f, 20, 20, 128, 128, 128, 128, ARGB.white(a));
        g.pose().popMatrix();
        text(g, Component.literal("SkyblockHelper").withStyle(ChatFormatting.BOLD), px + 36, py + 9, Ui.alpha(Hud.GOLD, a));
        text(g, "게임 모드 " + SbhMod.VERSION, px + 36, py + 20, Ui.alpha(Hud.GRAY, a));

        boolean on = Link.connected;
        String status = on ? "프로그램 연결됨" : "프로그램 꺼짐";
        int sw = this.font.width(status);
        int sx2 = px + pw - 10, sx1 = sx2 - sw - 20;
        int statusColor = on ? Hud.GREEN : Hud.RED;
        Ui.round(g, sx1, py + 10, sx2, py + 25, Ui.alpha(on ? 0x2855FF55 : 0x28FF6B6B, a));
        float pulse = 0.55f + 0.45f * (float) Math.sin(now / 280.0);
        Ui.round(g, sx1 + 5, py + 15, sx1 + 10, py + 20, Ui.alpha(statusColor, a * pulse));
        text(g, status, sx1 + 14, py + 14, Ui.alpha(statusColor, a));
        if (Ui.inside(mx, my, sx1, py + 10, sx2, py + 25))
            tooltip = on ? "SkyblockHelper 프로그램과 연결돼 있어요" : "PC 에서 SkyblockHelper 프로그램을 켜면 자동으로 연결돼요";

        // 탭
        int ty = py + 36;
        g.fill(px + 8, ty + 17, px + pw - 8, ty + 18, Ui.alpha(0x22FFFFFF, a));
        int tx = px + 10;
        int[] tabX = new int[TABS.length], tabW = new int[TABS.length];
        for (int i = 0; i < TABS.length; i++) {
            int w = this.font.width(TABS[i]) + 12;
            tabX[i] = tx; tabW[i] = w;
            boolean hov = Ui.inside(mx, my, tx, ty, tx + w, ty + 17);
            float hv = anim.to("tab" + i, hov ? 1 : 0, 16);
            float act = anim.to("tabA" + i, i == tab ? 1 : 0, 14);
            if (hv > 0.01f) Ui.round(g, tx, ty + 1, tx + w, ty + 16, Ui.alpha(0x18FFFFFF, a * hv));
            int color = Ui.mix(Math.max(act, hv * 0.6f), 0xFF8A8F99, 0xFFFFFFFF);
            text(g, TABS[i], tx + 6, ty + 5, Ui.alpha(color, a));
            int idx = i;
            hits.add(new Hit(tx, ty, tx + w, ty + 17, () -> setTab(idx)));
            tx += w + 2;
        }
        float ux = anim.to("ux", tabX[tab], 18), uw = anim.to("uw", tabW[tab], 18);
        Ui.round(g, Math.round(ux) + 5, ty + 15, Math.round(ux + uw) - 5, ty + 18, Ui.alpha(ACCENT, a));

        // 내용 (탭을 바꾸면 옆에서 미끄러져 들어옴)
        float ct = Ui.easeOut((now - tabAt) / 240f);
        float ca = a * ct;
        int cx = px + 10 + Math.round((1 - ct) * 14 * tabDir), cy = ty + 24, cw = pw - 20;
        switch (tab) {
            case 0 -> general(g, mx, my, cx, cy, cw, ca);
            case 1 -> hud(g, mx, my, cx, cy, cw, ca);
            case 2 -> timers(g, mx, my, cx, cy, cw, ca);
            case 3 -> containers(g, mx, my, cx, cy, cw, ca);
            case 4 -> auction(g, mx, my, cx, cy, cw, ca);
            case 5 -> tooltips(g, mx, my, cx, cy, cw, ca);
            case 6 -> chat(g, mx, my, cx, cy, cw, ca);
            case 7 -> combat(g, mx, my, cx, cy, cw, ca);
            default -> tools(g, mx, my, cx, cy, cw, ca);
        }

        // 아래: 안내 + 완료
        int fy = py + ph - 24;
        g.fill(px + 8, fy - 5, px + pw - 8, fy - 4, Ui.alpha(0x16FFFFFF, a));
        button(g, mx, my, px + pw - 70, fy, 60, 16, "done", "완료", true, () -> { Ui.click(); onClose(); }, a);
        text(g, Hud.fit(this.font, "Tab 키로 탭 이동 · /sbh <기능> 으로도 켜고 꺼요", pw - 96), px + 12, fy + 4, Ui.alpha(0xFF6F7580, a));

        if (tooltip != null && a > 0.9f) g.setTooltipForNextFrame(this.font, Component.literal(tooltip), mx, my);
    }

    private void general(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, float a) {
        Config c = Config.get();
        int half = (w - 6) / 2;
        toggle(g, mx, my, x, y, half, "hud", "HUD 전체", "단축키 O 로도 켜고 꺼요", null, c.hud, () -> c.hud = !c.hud, a);
        toggle(g, mx, my, x + half + 6, y, half, "onlysb", "스카이블록에서만", "스카이블록 밖에서는 HUD 를 숨겨요", null, c.onlyInSkyblock, () -> c.onlyInSkyblock = !c.onlyInSkyblock, a);

        // 지금 상태
        int sy = y + 30;
        Ui.round2(g, x, sy, x + w, sy + 50, Ui.alpha(0x0CFFFFFF, a));
        text(g, "지금 상태", x + 8, sy + 6, Ui.alpha(0xFF8A8F99, a));
        String where = !Link.connected ? "-" : SkyBlock.inSkyblock ? (SkyBlock.area.isEmpty() ? "스카이블록" : SkyBlock.area) : "스카이블록 아님";
        Link.Plan plan = Link.plan;
        int col2 = x + w / 2 + 4;
        info(g, x + 8, sy + 19, w / 2 - 12, "위치", where, a);
        info(g, col2, sy + 19, w / 2 - 12, "시세", Link.prices.isEmpty() ? "없음" : String.format("%,d개", Link.prices.size()), a);
        info(g, x + 8, sy + 32, w / 2 - 12, "제작 목표", plan == null ? "없음" : plan.title(), a);
        info(g, col2, sy + 32, w / 2 - 12, "타이머", Link.timers.size() + "개", a);
    }

    private void hud(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, float a) {
        Config c = Config.get();
        int half = (w - 6) / 2;
        toggle(g, mx, my, x, y, half, "plan", "제작 플래너 HUD", "프로그램 제작 플래너의 목표와 모자란 재료", null, c.planHud, () -> c.planHud = !c.planHud, a);
        toggle(g, mx, my, x + half + 6, y, half, "timers", "타이머 HUD", "포지 · 컴포스터 같은 타이머", null, c.timerHud, () -> c.timerHud = !c.timerHud, a);
        int half2 = (w - 6) / 2;
        button(g, mx, my, x, y + 30, half2, 20, "edit", "HUD 위치 · 크기 · 너비", true,
            () -> { Ui.click(); this.minecraft.gui.setScreen(new HudEditScreen(this)); }, a);
        button(g, mx, my, x + half2 + 6, y + 30, half2, 20, "planner", "제작 플래너 편집", true,
            () -> { Ui.click(); this.minecraft.gui.setScreen(new PlannerScreen(this)); }, a);
        button(g, mx, my, x, y + 56, half, 18, "resetSize", "크기 · 너비 기본으로", false, () -> {
            Ui.click(); c.planScale = 1f; c.timerScale = 1f; c.planWidth = 0; c.timerWidth = 0; Config.save();
        }, a);
        button(g, mx, my, x + half + 6, y + 56, half, 18, "resetPos", "위치 기본으로", false, () -> {
            Ui.click();
            c.planX = 4; c.planY = 4; c.planRight = false; c.planBottom = false;
            c.timerX = 4; c.timerY = 4; c.timerRight = true; c.timerBottom = false;
            Config.save();
        }, a);
        text(g, Hud.fit(this.font, "편집 화면: 끌어서 옮기기 · 휠로 크기 · 노란 막대로 너비", w), x + 2, y + 84, Ui.alpha(0xFF8A8F99, a));
        text(g, Hud.fit(this.font, "화면 끝에 붙이면 줄이 늘어도 반대쪽으로 자라요", w), x + 2, y + 96, Ui.alpha(0xFF6F7580, a));
    }

    private void timers(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, float a) {
        Config c = Config.get();
        int half = (w - 6) / 2;
        int i = 0;
        for (Map.Entry<String, String> k : Hud.TIMER_KINDS.entrySet()) {
            String key = k.getKey();
            int col = i % 2, row = i / 2;
            toggle(g, mx, my, x + col * (half + 6), y + row * 24, half, "t:" + key, k.getValue(), null, Hud.icon(key),
                c.showTimer(key), () -> c.toggleTimer(key), a);
            i++;
        }
    }

    private void containers(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, float a) {
        Config c = Config.get();
        int half = (w - 6) / 2;
        toggle(g, mx, my, x, y, half, "value", "상자 가치", "열어 본 상자 안 아이템의 총가치", null, c.containerValue, () -> c.containerValue = !c.containerValue, a);
        toggle(g, mx, my, x + half + 6, y, half, "highlight", "부족한 재료 강조", "제작 플래너에 모자란 재료 칸을 초록색으로", null,
            c.highlightMissing, () -> c.highlightMissing = !c.highlightMissing, a);
        toggle(g, mx, my, x, y + 24, half, "storage", "Storage 미리보기", "Storage 를 열면 엔더 체스트 · 백팩을 한 화면에. 눌러서 열기", null,
            c.storagePreview, () -> c.storagePreview = !c.storagePreview, a);
        toggle(g, mx, my, x + half + 6, y + 24, half, "pets", "펫 레벨 배경색", "Pets 화면에서 레벨에 따라 칸 색", null, c.petColors, () -> c.petColors = !c.petColors, a);
        toggle(g, mx, my, x, y + 48, half, "petpct", "펫 만렙 %", "펫 이름 옆 · Pets 칸에 만렙까지 몇 % (1렙 0%, 만렙 100%)", null,
            c.petPercent, () -> c.petPercent = !c.petPercent, a);
        segment(g, mx, my, x, y + 74, w, "가치 위치", new String[]{"이름 옆", "왼쪽 빈 곳"}, c.valuePosition, v -> c.valuePosition = v, a);
    }

    private void auction(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, float a) {
        Config c = Config.get();
        int half = (w - 6) / 2;
        toggle(g, mx, my, x, y, half, "bid", "입찰 상태", "내가 최고 입찰이면 초록, 밀렸으면 빨간 테두리", null, c.ahBidStatus, () -> c.ahBidStatus = !c.ahBidStatus, a);
        toggle(g, mx, my, x + half + 6, y, half, "seller", "판매자 보기", "아이템을 가운데 클릭하면 그 판매자의 경매 목록", null, c.ahSellerView, () -> c.ahSellerView = !c.ahSellerView, a);
        toggle(g, mx, my, x, y + 24, half, "ahsearch", "가격 검색 칸", "경매 목록 옆 칸에 1m-5m 처럼 적으면 그 가격대를 초록으로", null,
            c.ahPriceSearch, () -> c.ahPriceSearch = !c.ahPriceSearch, a);
        button(g, mx, my, x, y + 50, w, 18, "ahwin", "경매장 검색 창 열기 (/sbh ah)", true,
            () -> { Ui.click(); this.minecraft.gui.setScreen(new AuctionSearchScreen(this)); }, a);
        toggle(g, mx, my, x + half + 6, y + 24, half, "sign", "표지판 입력 목록", "가격 · 검색어 · 금액을 적는 표지판에서 전에 적은 값 고르기", null,
            c.signHistory, () -> c.signHistory = !c.signHistory, a);
    }

    private void tooltips(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, float a) {
        Config c = Config.get();
        int half = (w - 6) / 2;
        toggle(g, mx, my, x, y, half, "tooltip", "툴팁 시세", "아이템 설명에 바자 · 경매장 · NPC 가격", null, c.tooltipPrices, () -> c.tooltipPrices = !c.tooltipPrices, a);
        toggle(g, mx, my, x + half + 6, y, half, "times", "남은 시간 → 시각", "경매 종료 · 이벤트 · 은행 이자 · Pet Care 를 실제 시각으로", null,
            c.realTimes, () -> c.realTimes = !c.realTimes, a);
        toggle(g, mx, my, x, y + 24, half, "age", "얻은 날", "아이템에 기록이 있으면 얻은 날짜", null, c.itemAge, () -> c.itemAge = !c.itemAge, a);
        toggle(g, mx, my, x + half + 6, y + 24, half, "exp", "실험대 툴팁 숨기기", "Chronomatron · Ultrasequencer · Superpairs", null,
            c.hideExperimentTips, () -> c.hideExperimentTips = !c.hideExperimentTips, a);
        toggle(g, mx, my, x, y + 48, half, "harp", "Harp 툴팁 숨기기", "Melody's Harp 화면", null, c.hideHarpTips, () -> c.hideHarpTips = !c.hideHarpTips, a);
        toggle(g, mx, my, x + half + 6, y + 48, half, "enchant", "마법 부여 도우미", "Enchant Item 화면 왼쪽에 마법 목록 · 검색 (누르면 그 칸을 한 번)", null,
            c.enchantPanel, () -> c.enchantPanel = !c.enchantPanel, a);
    }

    private void chat(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, float a) {
        Config c = Config.get();
        int half = (w - 6) / 2;
        toggle(g, mx, my, x, y, half, "chatscreen", "상자 화면 채팅", "엔더 체스트 · 거래 · 은행 · 제작 화면에서도 최근 채팅", null,
            c.chatInScreens, () -> c.chatInScreens = !c.chatInScreens, a);
        toggle(g, mx, my, x + half + 6, y, half, "shortcuts", "바로가기 메뉴", "상자 화면 옆: 엔더 체스트 · 제작대 · 옷장 · 펫 · Storage · 메뉴", null,
            c.shortcuts, () -> c.shortcuts = !c.shortcuts, a);
        toggle(g, mx, my, x, y + 24, half, "pinvite", "채팅 파티 초대", "플레이어 채팅 끝의 [파티 초대] 를 누르면 /p invite", null,
            c.chatPartyInvite, () -> c.chatPartyInvite = !c.chatPartyInvite, a);
        toggle(g, mx, my, x + half + 6, y + 24, half, "cinvite", "Ctrl+우클릭 초대", "Ctrl 을 누른 채 플레이어를 우클릭하면 파티 초대", null,
            c.ctrlClickInvite, () -> c.ctrlClickInvite = !c.ctrlClickInvite, a);
        toggle(g, mx, my, x, y + 48, half, "reply", "/r 마지막 상대", "/r 을 마지막으로 귓속말한 사람에게 (/msg 이름)", null,
            c.replyLast, () -> c.replyLast = !c.replyLast, a);
        toggle(g, mx, my, x + half + 6, y + 48, half, "names", "이름 자동완성", "/p · /msg · /invite 등에서 탭 목록 플레이어 이름", null,
            c.nameComplete, () -> c.nameComplete = !c.nameComplete, a);
        segment(g, mx, my, x, y + 74, w, "바로가기 위치", Shortcuts.POSITIONS, c.shortcutPos, v -> c.shortcutPos = v, a);
        text(g, Hud.fit(this.font, "자유: 상자 화면에서 Ctrl 을 누른 채 바를 끌어서 옮겨요", w), x + 2, y + 100, Ui.alpha(0xFF6F7580, a));
    }

    private void combat(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, float a) {
        Config c = Config.get();
        int half = (w - 6) / 2;
        toggle(g, mx, my, x, y, half, "dragonhb", "드래곤 히트박스", "드래곤이 나타나면 히트박스를 자동으로", null, c.dragonHitbox, () -> c.dragonHitbox = !c.dragonHitbox, a);
        toggle(g, mx, my, x + half + 6, y, half, "crystalhb", "크리스탈 히트박스", "Dragon's Nest 에서 엔드 크리스탈 히트박스", null, c.crystalHitbox, () -> c.crystalHitbox = !c.crystalHitbox, a);
        toggle(g, mx, my, x, y + 24, half, "scroll", "드래곤 전투 휠 잠금", "드래곤이 있을 때 마우스 휠로 핫바가 넘어가지 않게", null,
            c.dragonScrollLock, () -> c.dragonScrollLock = !c.dragonScrollLock, a);
        toggle(g, mx, my, x + half + 6, y + 24, half, "toolr", "도구 범위", "Builder's Wand · Block Zapper · Bonemerang 범위 미리보기", null,
            c.toolRanges, () -> c.toolRanges = !c.toolRanges, a);
        toggle(g, mx, my, x, y + 48, half, "pillar", "Blaze 기둥 경고", "Blaze 슬레이어 불기둥 폭발까지 남은 시간", null, c.blazePillar, () -> c.blazePillar = !c.blazePillar, a);
        toggle(g, mx, my, x + half + 6, y + 48, half, "ashfang", "Ashfang", "전투 시간 · 체력 · 중력 구 위치", null, c.ashfang, () -> c.ashfang = !c.ashfang, a);
        toggle(g, mx, my, x, y + 72, half, "dojo", "Dojo 도우미", "Discipline: 좀비 머리 위에 맞는 칼", null, c.dojoHelper, () -> c.dojoHelper = !c.dojoHelper, a);
        toggle(g, mx, my, x + half + 6, y + 72, half, "kuudra", "Kuudra 타이머", "Kuudra 에 들어간 뒤 지난 시간", null, c.kuudraTimer, () -> c.kuudraTimer = !c.kuudraTimer, a);
        segment(g, mx, my, x, y + 98, w, "낚싯줄", new String[]{World.LINE_NAMES[c.fishingLineColor], "바꾸기"}, 0,
            v -> c.fishingLineColor = (c.fishingLineColor + 1) % World.LINE_COLORS.length, a);
    }

    private void tools(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, float a) {
        Config c = Config.get();
        int half = (w - 6) / 2;
        toggle(g, mx, my, x, y, half, "copy", "복사 단축키", "상자 화면에서 Ctrl+C 아이템, Ctrl+Shift+C 화면 전체", null, c.copyKeys, () -> c.copyKeys = !c.copyKeys, a);
        button(g, mx, my, x + half + 6, y + 1, half, 18, "copyInv", "인벤토리 복사", false, () -> { Ui.click(); Copy.inventory(); }, a);
        button(g, mx, my, x, y + 26, half, 18, "copyEntity", "보고 있던 엔티티 복사", false, () -> { Ui.click(); Copy.entity(); }, a);
        button(g, mx, my, x + half + 6, y + 26, half, 18, "storageClear", "Storage 기억 지우기", false, () -> { Ui.click(); Storage.clear(); }, a);
        button(g, mx, my, x, y + 50, half, 18, "island", "섬 분석 (미니언 · 상자 · 호퍼)", false, () -> { Ui.click(); World.island(); }, a);
        button(g, mx, my, x + half + 6, y + 50, half, 18, "matri", "Heavy Pearl 24시간 시작", false, () -> {
            Ui.click(); Bosses.setHeavyPearl(System.currentTimeMillis());
        }, a);
        toggle(g, mx, my, x, y + 72, half, "music", "장소별 음악", "music 폴더에 지역이름.ogg (예: Hub.ogg) 를 넣으면 그 지역에서 재생", null,
            c.areaMusic, () -> { c.areaMusic = !c.areaMusic; if (!c.areaMusic) Music.stop(); }, a);
        button(g, mx, my, x + half + 6, y + 73, half, 18, "musicdir", "음악 폴더 열기", false, () -> { Ui.click(); Music.openFolder(); }, a);
        button(g, mx, my, x, y + 97, half, 18, "logs", "로그 검색 (/sbh logs)", false, () -> { Ui.click(); this.minecraft.gui.setScreen(new LogScreen(this)); }, a);
        text(g, Hud.fit(this.font, "지금 지역: " + (SkyBlock.area.isEmpty() ? "-" : SkyBlock.area), half), x + half + 10, y + 102, Ui.alpha(0xFF8A8F99, a));
        text(g, Hud.fit(this.font, "/sbh stalk 닉네임 · copy · entity · island · update", w), x + 2, y + 120, Ui.alpha(0xFF6F7580, a));
    }

    // ---------- 부품 ----------

    /** 켜고 끄는 카드: 이름 + 오른쪽 스위치 (손잡이가 미끄러지고 색이 바뀜) */
    private void toggle(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String key, String name, String desc, ItemStack icon,
                        boolean on, Runnable flip, float a) {
        int h = 20;
        boolean hov = Ui.inside(mx, my, x, y, x + w, y + h);
        float hv = anim.to("h:" + key, hov ? 1 : 0, 14);
        float sv = anim.to("s:" + key, on ? 1 : 0, 14);
        Ui.round2(g, x, y, x + w, y + h, Ui.alpha(Ui.mix(hv, CARD, CARD_HOVER), a));
        int lx = x + 7;
        if (icon != null) {
            if (a > 0.6f) {
                g.pose().pushMatrix();
                g.pose().translate(lx, y + 3);
                g.pose().scale(0.875f, 0.875f);
                g.item(icon, 0, 0);
                g.pose().popMatrix();
            }
            lx += 17;
        }
        text(g, Hud.fit(this.font, name, x + w - 34 - lx), lx, y + 6, Ui.alpha(Ui.mix(sv, 0xFFB8BCC4, 0xFFFFFFFF), a));
        int tx = x + w - 27, ty = y + 5;
        Ui.round(g, tx, ty, tx + 20, ty + 10, Ui.alpha(Ui.mix(sv, TRACK, ACCENT), a));
        int kx = tx + 1 + Math.round(sv * 10);
        Ui.round(g, kx, ty + 1, kx + 8, ty + 9, Ui.alpha(0xFFFFFFFF, a));
        hits.add(new Hit(x, y, x + w, y + h, () -> { flip.run(); Config.save(); Ui.click(); }));
        if (hov && desc != null) tooltip = desc;
    }

    /** 버튼: 주황(주요) 또는 반투명, 마우스를 올리면 밝아짐 */
    private void button(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String key, String label, boolean primary, Runnable action, float a) {
        boolean hov = Ui.inside(mx, my, x, y, x + w, y + h);
        float hv = anim.to("b:" + key, hov ? 1 : 0, 16);
        int bg = primary ? Ui.mix(hv, ACCENT, ACCENT_HI) : Ui.mix(hv, 0x1CFFFFFF, 0x34FFFFFF);
        Ui.round2(g, x, y, x + w, y + h, Ui.alpha(bg, a));
        String s = Hud.fit(this.font, label, w - 8);
        int color = primary ? DARK_TEXT : 0xFFFFFFFF;
        int textX = x + (w - this.font.width(s)) / 2, textY = y + (h - 8) / 2;
        if (ARGB.alpha(Ui.alpha(color, a)) >= 8) g.text(this.font, s, textX, textY, Ui.alpha(color, a), !primary);
        hits.add(new Hit(x, y, x + w, y + h, action));
    }

    /** 둘 중 하나 고르기: 고른 쪽 배경이 미끄러져 옮겨 감 */
    private void segment(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String name, String[] opts, int sel, IntConsumer set, float a) {
        int h = 20;
        Ui.round2(g, x, y, x + w, y + h, Ui.alpha(CARD, a));
        text(g, name, x + 7, y + 6, Ui.alpha(0xFFFFFFFF, a));
        int ow = 0;
        for (String o : opts) ow = Math.max(ow, this.font.width(o) + 16);
        int sx = x + w - 4 - ow * opts.length, sy = y + 3;
        Ui.round(g, sx, sy, sx + ow * opts.length, sy + 14, Ui.alpha(0x40000000, a));
        float pos = anim.to("seg:" + name, sel, 16);
        int hx = sx + Math.round(pos * ow);
        Ui.round(g, hx, sy, hx + ow, sy + 14, Ui.alpha(ACCENT, a));
        for (int i = 0; i < opts.length; i++) {
            int ox = sx + i * ow;
            float near = 1 - Math.min(1, Math.abs(pos - i));
            int color = Ui.mix(near, 0xFFB8BCC4, DARK_TEXT);
            int c2 = Ui.alpha(color, a);
            if (ARGB.alpha(c2) >= 8) g.text(this.font, opts[i], ox + (ow - this.font.width(opts[i])) / 2, sy + 3, c2, false);
            int v = i;
            hits.add(new Hit(ox, sy, ox + ow, sy + 14, () -> { if (v != sel) { set.accept(v); Config.save(); Ui.click(); } }));
        }
    }

    private void info(GuiGraphicsExtractor g, int x, int y, int w, String label, String value, float a) {
        text(g, label, x, y, Ui.alpha(0xFF6F7580, a));
        int lw = this.font.width(label) + 6;
        text(g, Hud.fit(this.font, value, w - lw), x + lw, y, Ui.alpha(0xFFE6E8EC, a));
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        if (ARGB.alpha(color) >= 8) g.text(this.font, s, x, y, color, true);
    }

    private void text(GuiGraphicsExtractor g, Component s, int x, int y, int color) {
        if (ARGB.alpha(color) >= 8) g.text(this.font, s, x, y, color, true);
    }

    /** 자동 테스트용: 다음에 열 때 이 탭으로 */
    public static void selectTab(int i) { tab = Math.max(0, Math.min(TABS.length - 1, i)); }

    private void setTab(int i) {
        if (i == tab) return;
        tabDir = i > tab ? 1 : -1;
        tab = i;
        tabAt = System.currentTimeMillis();
        Ui.click();
    }

    // ---------- 입력 ----------

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (closingAt > 0) return true;
        if (e.button() == 0) {
            for (Hit h : List.copyOf(hits)) {
                if (Ui.inside(e.x(), e.y(), h.x1(), h.y1(), h.x2(), h.y2())) { h.action().run(); return true; }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_TAB) { setTab((tab + 1) % TABS.length); return true; }
        return super.keyPressed(e);
    }

    @Override
    public void tick() {
        if (closingAt > 0 && System.currentTimeMillis() - closingAt >= 170) {
            closingAt = 0;
            this.minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        if (closingAt == 0) { closingAt = System.currentTimeMillis(); Config.save(); }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
