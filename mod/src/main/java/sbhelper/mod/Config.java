package sbhelper.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

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
    /** HUD 위치 (화면 좌표). timerX < 0 이면 오른쪽 위 */
    public int planX = 4, planY = 4;
    public int timerX = -1, timerY = 4;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("skyblockhelper.json"); }

    public static Config get() {
        if (instance == null) {
            try { instance = GSON.fromJson(Files.readString(file()), Config.class); } catch (Exception ignored) {}
            if (instance == null) instance = new Config();
        }
        return instance;
    }

    public static void save() {
        try { Files.writeString(file(), GSON.toJson(get())); } catch (Exception ignored) {}
    }

    private Config() {}
}
