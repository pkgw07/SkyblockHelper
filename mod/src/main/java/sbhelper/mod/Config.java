package sbhelper.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/** 설정 (config/skyblockhelper.json) — 게임을 다시 켜도 유지 */
public final class Config {
    public boolean hud = true;
    /** 스카이블록 안에서만 HUD 보이기 */
    public boolean onlyInSkyblock = true;
    public boolean planHud = true;
    public boolean timerHud = true;
    public boolean tooltipPrices = true;
    public boolean containerValue = true;
    public boolean highlightMissing = true;
    /** 상자 가치 위치: 0 = 상자 이름 옆, 1 = 상자 왼쪽 빈 곳 */
    public int valuePosition = 0;
    /**
     * HUD 위치: 가까운 화면 가장자리에서 떨어진 거리.
     * right 면 x 는 오른쪽 끝에서, bottom 이면 y 는 아래 끝에서 잰다 → 줄이 늘어도 화면 밖으로 나가지 않고 반대쪽으로 자란다.
     */
    public int planX = 4, planY = 4;
    public boolean planRight = false, planBottom = false;
    public int timerX = 4, timerY = 4;
    public boolean timerRight = true, timerBottom = false;
    /** HUD 크기 (배율) · 너비 (0 = 내용에 맞춤) */
    public float planScale = 1f, timerScale = 1f;
    public int planWidth = 0, timerWidth = 0;
    /** 타이머 HUD 에서 숨길 종류 (Timers.KINDS 의 키) */
    public List<String> hiddenTimers = new ArrayList<>(List.of("fetchur", "experiment", "mayor"));
    /** 상자 · 경매장 · 툴팁 (1.1.0) */
    public boolean storagePreview = true;
    public boolean petColors = true;
    public boolean petPercent = true;
    public boolean ahBidStatus = true;
    public boolean ahSellerView = true;
    public boolean ahPriceSearch = true;
    public boolean signHistory = true;
    public boolean realTimes = true;
    public boolean itemAge = true;
    public boolean hideExperimentTips = true;
    public boolean hideHarpTips = true;
    public boolean copyKeys = true;
    /** 채팅 · 편의 (1.2.0) */
    public boolean chatInScreens = true;
    public boolean shortcuts = true;
    /** 바로가기 바 위치: 0 오른쪽, 1 왼쪽, 2 위, 3 아래, 4 자유 (shortcutX/Y) */
    public int shortcutPos = 0;
    public int shortcutX = -1, shortcutY = -1;
    public boolean shortcutVertical = true;
    public boolean chatPartyInvite = true;
    public boolean ctrlClickInvite = true;
    public boolean replyLast = true;
    public boolean nameComplete = true;
    /** 월드 · 전투 · 보스 (1.2.0) */
    public boolean dragonHitbox = true;
    public boolean crystalHitbox = true;
    public boolean dragonScrollLock = true;
    public boolean toolRanges = true;
    public int fishingLineColor = 0;
    public boolean blazePillar = true;
    public boolean ashfang = true;
    public boolean dojoHelper = true;
    public boolean kuudraTimer = true;
    public long matriarchAt = 0;
    /** 3단계 (1.3.0) */
    public boolean areaMusic = true;
    public float musicVolume = 0.5f;
    public boolean enchantPanel = true;
    /** 표지판 입력 기록: 표지판 아래 줄 → 최근 값 */
    public Map<String, List<String>> signValues = new HashMap<>();
    /** 설정 파일 형식 (옛 파일 옮기기용) */
    public int layout = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("skyblockhelper.json"); }

    public static Config get() {
        if (instance == null) {
            String text = null;
            try { text = Files.readString(file()); instance = GSON.fromJson(text, Config.class); } catch (Exception ignored) {}
            if (instance == null) instance = new Config();
            if (instance.hiddenTimers == null) instance.hiddenTimers = new ArrayList<>();
            if (instance.signValues == null) instance.signValues = new HashMap<>();
            // 1.0.1 까지의 파일: timerX < 0 이면 오른쪽 위, 아니면 왼쪽 기준
            if (text != null && !text.contains("\"layout\"")) {
                if (instance.timerX < 0) { instance.timerRight = true; instance.timerX = 4; }
                else instance.timerRight = false;
                instance.planRight = false; instance.planBottom = false; instance.timerBottom = false;
            }
            // 1.0.2 에서 새로 깔면 두 HUD 가 같은 자리에 겹쳤다 → 타이머를 오른쪽 위로
            if (!instance.planRight && !instance.timerRight && instance.planBottom == instance.timerBottom
                && instance.planX == instance.timerX && instance.planY == instance.timerY) instance.timerRight = true;
            instance.layout = 1;
        }
        return instance;
    }

    public boolean showTimer(String kind) { return !hiddenTimers.contains(kind); }

    public void toggleTimer(String kind) { if (!hiddenTimers.remove(kind)) hiddenTimers.add(kind); }

    public static void save() {
        try { Files.writeString(file(), GSON.toJson(get())); } catch (Exception ignored) {}
    }

    private Config() {}
}
