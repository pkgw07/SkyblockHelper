package sbhelper.mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 모드 자동 업데이트 (게임 안):
 *  1. 게임을 켜면 GitHub 최신 릴리스에 더 새 모드가 있는지 확인
 *  2. 있으면 타이틀 화면에 업데이트 창 → '업데이트' 를 누르면 mods 폴더에 받아 둠 (*.jar.new)
 *  3. 게임을 끄면 예전 파일을 지우고 새 파일로 바꿈 → 다시 켜면 적용
 *  (게임이 켜져 있는 동안은 지금 모드 파일이 잠겨 있어서 바로 바꿀 수 없다)
 */
public final class Updater {
    private static final String API = "https://api.github.com/repos/pkgw07/SkyblockHelper/releases/latest";
    private static final Pattern JAR = Pattern.compile("^SkyblockHelper-mod-([0-9][0-9A-Za-z.\\-+]*)\\.jar$");

    public static volatile String latest = null;
    private static volatile String url = null;
    private static volatile String fileName = null;
    /** 이번 실행에서 창을 이미 보여 줬음 */
    public static volatile boolean asked = false;
    /** 받아 둔 새 파일 (게임을 끌 때 바꿈) */
    private static volatile Path pending = null;

    /** 지금 버전보다, 그리고 이미 받아 둔 버전보다 새 버전이 있는지 */
    public static boolean available() {
        if (latest == null || url == null || !newer(latest, SbhMod.VERSION)) return false;
        String got = pendingVersion();
        return got == null || newer(latest, got);
    }
    public static boolean downloaded() { return pending != null; }

    /** 게임 시작 때 한 번 (따로 스레드) */
    public static void start() {
        Thread t = new Thread(() -> { resumePending(); check(); }, "sbh-updater");
        t.setDaemon(true);
        t.start();
        // 게임 중에도 30분마다 확인 (게임을 켠 뒤 새 버전이 나와도 알 수 있게)
        Thread loop = new Thread(() -> {
            while (true) {
                try { Thread.sleep(30 * 60_000L); } catch (InterruptedException e) { return; }
                String before = latest;
                check();
                if (available() && latest != null && !latest.equals(before)) notifyChat();
            }
        }, "sbh-updater-loop");
        loop.setDaemon(true);
        loop.start();
    }

    /** GitHub 최신 릴리스에서 모드 파일 찾기 (실패하면 그대로) */
    public static synchronized void check() {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(API))
                .header("Accept", "application/vnd.github+json").header("User-Agent", "SkyblockHelper-mod").timeout(Duration.ofSeconds(15)).build(),
                HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return;
            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("assets")) {
                JsonObject a = e.getAsJsonObject();
                Matcher m = JAR.matcher(a.get("name").getAsString());
                if (m.matches()) {
                    fileName = a.get("name").getAsString();
                    url = a.get("browser_download_url").getAsString();
                    latest = m.group(1);
                }
            }
        } catch (Exception ignored) {}
    }

    /** /sbh update: 지금 다시 확인하고 새 버전이 있으면 업데이트 창 */
    public static void checkNowAndOpen() {
        Thread t = new Thread(() -> {
            check();
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> {
                if (available()) { asked = true; mc.gui.setScreen(new UpdateScreen(null)); }
                else msg(downloaded() ? "새 버전 " + pendingVersion() + " 을 받아 뒀어요. 게임을 다시 시작하면 적용돼요."
                    : latest == null ? "확인하지 못했어요. 인터넷 연결을 확인해 주세요." : "최신 버전이에요 (" + SbhMod.VERSION + ")");
            });
        }, "sbh-updater-now");
        t.setDaemon(true);
        t.start();
    }

    private static void notifyChat() {
        msg("새 버전 §e" + latest + "§f 이 나왔어요. §e/sbh update§f 로 받을 수 있어요.");
    }

    private static void msg(String s) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> { if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6[SkyblockHelper] §f" + s)); });
    }

    /** 받아 둔 새 파일의 버전 (없으면 null) */
    private static String pendingVersion() {
        Path p = pending;
        if (p == null) return null;
        Matcher m = JAR.matcher(p.getFileName().toString().replace(".jar.new", ".jar"));
        return m.matches() ? m.group(1) : null;
    }

    /** '업데이트' 를 눌렀을 때: 받아서 *.jar.new 로 둔다. 성공하면 true */
    public static boolean download() {
        try {
            Path dir = modsDir();
            Path tmp = dir.resolve(fileName + ".new");
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<Path> res = http.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "SkyblockHelper-mod")
                .timeout(Duration.ofMinutes(2)).build(), HttpResponse.BodyHandlers.ofFile(tmp));
            if (res.statusCode() != 200 || Files.size(tmp) < 1000) { Files.deleteIfExists(tmp); return false; }
            // 전에 받아 둔 (더 예전) 새 파일은 지운다
            Path old = pending;
            if (old != null && !old.equals(tmp)) Files.deleteIfExists(old);
            pending = tmp;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 예전에 받아 두고 못 바꾼 새 파일이 있으면 이번에 끌 때 다시 시도 */
    private static void resumePending() {
        try (var files = Files.list(modsDir())) {
            files.filter(p -> p.getFileName().toString().endsWith(".jar.new")).findFirst().ifPresent(p -> {
                Matcher m = JAR.matcher(p.getFileName().toString().replace(".jar.new", ".jar"));
                if (m.matches() && newer(m.group(1), SbhMod.VERSION)) pending = p;
                else p.toFile().delete();
            });
        } catch (Exception ignored) {}
    }

    /** 게임을 끌 때: 예전 파일 지우기 + 새 파일 이름 바꾸기 */
    public static void applyOnExit() {
        Path p = pending;
        if (p == null) return;
        Path target = p.resolveSibling(p.getFileName().toString().replace(".jar.new", ".jar"));
        List<Path> old = currentJars();
        try {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
                // Windows: 게임이 꺼진 뒤(파일 잠금이 풀린 뒤) 바꾸는 작은 자바 프로그램(Swapper)을 같은 자바로 띄운다
                Path self = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("skyblockhelper")
                    .flatMap(c -> c.getOrigin().getPaths().stream().findFirst()).orElse(null);
                if (self == null || !Files.isRegularFile(self)) return;
                Path helper = Path.of(System.getProperty("java.io.tmpdir"), "skyblockhelper-mod-update.jar");
                Files.copy(self, helper, StandardCopyOption.REPLACE_EXISTING);
                String java = ProcessHandle.current().info().command().orElse("javaw");
                Path javaw = Path.of(java).resolveSibling("javaw.exe");
                if (Files.isRegularFile(javaw)) java = javaw.toString();
                List<String> cmd = new java.util.ArrayList<>(List.of(java, "-cp", helper.toString(), "sbhelper.mod.Swapper",
                    String.valueOf(ProcessHandle.current().pid()), p.toString(), target.toString()));
                for (Path o : old) cmd.add(o.toString());
                new ProcessBuilder(cmd).start();
            } else {
                for (Path o : old) if (!o.equals(target)) Files.deleteIfExists(o);
                Files.move(p, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {}
    }


    /** 지금 실행 중인 이 모드 파일(들) */
    private static List<Path> currentJars() {
        return FabricLoader.getInstance().getModContainer("skyblockhelper")
            .map(m -> m.getOrigin().getPaths()).orElse(List.of())
            .stream().filter(p -> p.getFileName().toString().endsWith(".jar")).toList();
    }

    static Path modsDir() {
        List<Path> jars = currentJars();
        if (!jars.isEmpty() && jars.get(0).getParent() != null) return jars.get(0).getParent();
        return FabricLoader.getInstance().getGameDir().resolve("mods");
    }

    /** a 가 b 보다 새 버전인가 (1.0.10 > 1.0.9) */
    static boolean newer(String a, String b) {
        String[] x = a.split("[^0-9]+"), y = b.split("[^0-9]+");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0;
            int r = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
            if (p != r) return p > r;
        }
        return false;
    }

    private Updater() {}
}
