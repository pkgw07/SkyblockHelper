package sbhelper.mod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * 게임 로그 검색 (/sbh logs): logs 폴더의 latest.log 와 지난 로그(.log.gz)에서 글자를 찾는다.
 * 채팅도 로그에 남아서 예전 대화 · 거래 · 드롭 메시지를 찾을 때 쓴다. 누르면 그 줄을 복사.
 */
public final class LogScreen extends SearchScreen {
    private static final int MAX = 300;
    private volatile List<Row> results = List.of();
    private volatile String status = "";

    public LogScreen(Screen parent) { super("로그 검색", parent); }

    @Override protected String subtitle() { return status.isEmpty() ? "logs 폴더의 게임 기록 (채팅 포함)" : status; }
    @Override protected String hint() { return "찾을 글자 (2글자 이상)…"; }
    @Override protected String footer() { return "최근 기록부터 최대 " + MAX + "줄 · 누르면 그 줄을 복사해요"; }
    @Override protected List<Row> rows() { return results; }

    @Override
    protected void search(String q) {
        if (q.length() < 2) { results = List.of(); status = ""; return; }
        status = "찾는 중…";
        Thread t = new Thread(() -> run(q.toLowerCase(Locale.ROOT)), "sbh-log-search");
        t.setDaemon(true);
        t.start();
    }

    private void run(String q) {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("logs");
        List<Row> out = new ArrayList<>();
        int files = 0;
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> logs = s.filter(p -> { String n = p.getFileName().toString(); return n.endsWith(".log") || n.endsWith(".log.gz"); })
                .sorted(Comparator.comparing((Path p) -> p.toFile().lastModified()).reversed()).toList();
            for (Path p : logs) {
                if (out.size() >= MAX) break;
                files++;
                String name = p.getFileName().toString();
                String day = name.length() >= 10 && Character.isDigit(name.charAt(0)) ? name.substring(5, 10).replace('-', '/') : "오늘";
                List<String> matches = new ArrayList<>();
                try (InputStream in = name.endsWith(".gz") ? new GZIPInputStream(Files.newInputStream(p)) : Files.newInputStream(p);
                     BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) if (line.toLowerCase(Locale.ROOT).contains(q)) matches.add(line);
                } catch (Exception ignored) {}
                for (int i = matches.size() - 1; i >= 0 && out.size() < MAX; i--) {
                    String line = matches.get(i);
                    String time = line.length() > 10 && line.charAt(0) == '[' ? line.substring(1, Math.min(9, line.length())) : "";
                    String text = line.replaceFirst("^\\[[^\\]]*\\] \\[[^\\]]*\\](?: \\([^)]*\\))?:? ", "").replace("[CHAT] ", "");
                    String copy = text;
                    out.add(new Row(SkyBlock.strip(text), day + " " + time, 0xFF8A8F99,
                        () -> Minecraft.getInstance().keyboardHandler.setClipboard(copy), "누르면 복사"));
                }
            }
        } catch (Exception e) {
            status = "logs 폴더를 읽지 못했어요";
            return;
        }
        results = out;
        status = out.isEmpty() ? "찾은 줄이 없어요 (" + files + "개 파일)" : out.size() + "줄 찾음 (" + files + "개 파일)";
    }
}
