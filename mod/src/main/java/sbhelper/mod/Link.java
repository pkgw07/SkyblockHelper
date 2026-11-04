package sbhelper.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SkyblockHelper 프로그램과의 연결 (같은 컴퓨터 안, 127.0.0.1 만). 인터넷으로는 아무것도 보내지 않는다.
 * 한 줄에 JSON 하나씩 주고받는다. 프로그램이 꺼져 있으면 5초마다 다시 연결해 본다.
 *
 * 프로그램 → 모드: prices(시세), plan(제작 플래너), timers(타이머)
 * 모드 → 프로그램: hello, loc(스카이블록 안·지역·지갑), inv(내 인벤토리), container(열어 본 상자)
 */
public final class Link {
    public static final int PORT = 47321;

    /** 아이템 ID → [바자 즉시 판매가, 바자 즉시 구매가, 최저 BIN, NPC 판매가] (없으면 -1) */
    public static volatile Map<String, double[]> prices = Map.of();
    public static volatile Plan plan = null;
    public static volatile List<Timer> timers = List.of();
    public static volatile boolean connected = false;

    public record PlanRow(String id, String name, double have, double need) {}
    public record Plan(String title, double pct, List<PlanRow> rows, int more) {
        /** 아직 모자란 재료 ID (상자에서 강조) */
        public boolean needs(String id) {
            for (PlanRow r : rows) if (r.id().equals(id) && r.have() < r.need()) return true;
            return false;
        }
    }
    public record Timer(String label, long at) {}

    private static volatile Socket socket;
    private static final Object WRITE = new Object();

    public static void start() {
        Thread t = new Thread(Link::loop, "sbh-link");
        t.setDaemon(true);
        t.start();
    }

    private static void loop() {
        while (true) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", PORT), 2000);
                socket = s;
                connected = true;
                JsonObject hello = new JsonObject();
                hello.addProperty("t", "hello");
                hello.addProperty("mod", SbhMod.VERSION);
                send(hello);
                SkyBlock.resend();
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null) {
                    try { handle(JsonParser.parseString(line).getAsJsonObject()); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {
                // 프로그램이 꺼져 있음
            } finally {
                connected = false;
                socket = null;
            }
            try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
        }
    }

    private static void handle(JsonObject m) {
        String t = m.has("t") ? m.get("t").getAsString() : "";
        switch (t) {
            case "prices" -> {
                Map<String, double[]> out = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("data").entrySet()) {
                    JsonArray a = e.getValue().getAsJsonArray();
                    double[] v = new double[4];
                    for (int i = 0; i < 4; i++) v[i] = i < a.size() ? a.get(i).getAsDouble() : -1;
                    out.put(e.getKey(), v);
                }
                prices = out;
            }
            case "plan" -> {
                if (!m.has("title") || m.get("title").isJsonNull()) { plan = null; return; }
                List<PlanRow> rows = new ArrayList<>();
                for (JsonElement e : m.getAsJsonArray("rows")) {
                    JsonObject r = e.getAsJsonObject();
                    rows.add(new PlanRow(r.get("id").getAsString(), r.get("name").getAsString(),
                        r.get("have").getAsDouble(), r.get("need").getAsDouble()));
                }
                plan = new Plan(m.get("title").getAsString(), m.get("pct").getAsDouble(), rows, m.has("more") ? m.get("more").getAsInt() : 0);
            }
            case "timers" -> {
                List<Timer> list = new ArrayList<>();
                for (JsonElement e : m.getAsJsonArray("list")) {
                    JsonObject r = e.getAsJsonObject();
                    list.add(new Timer(r.get("label").getAsString(), r.get("at").getAsLong()));
                }
                timers = list;
            }
            default -> {}
        }
    }

    /** 프로그램으로 보내기 (연결이 없으면 버림) */
    public static void send(JsonObject m) {
        Socket s = socket;
        if (s == null) return;
        byte[] bytes = (m.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        Thread t = new Thread(() -> {
            synchronized (WRITE) {
                try { OutputStream o = s.getOutputStream(); o.write(bytes); o.flush(); } catch (Exception ignored) {}
            }
        }, "sbh-link-send");
        t.setDaemon(true);
        t.start();
    }

    /** 이 아이템의 시세 [바자 판매, 바자 구매, 최저 BIN, NPC] */
    public static double[] price(String id) { return prices.get(id); }

    /** 팔 때 가치 (바자 즉시 판매가 → 최저 BIN → NPC), 없으면 -1 */
    public static double value(String id) {
        double[] p = prices.get(id);
        if (p == null) return -1;
        if (p[0] > 0) return p[0];
        if (p[2] > 0) return p[2];
        return p[3] > 0 ? p[3] : -1;
    }

    private Link() {}
}
