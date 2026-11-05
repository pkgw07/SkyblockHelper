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
    /** 게임 안 제작 플래너: 목표 목록 · 지금 목표 · 모든 재료 · 옵션 · 고를 수 있는 아이템 */
    public static volatile List<Goal> goals = List.of();
    public static volatile String currentGoal = null;
    public static volatile int currentAmount = 1;
    public static volatile List<Material> materials = List.of();
    public static volatile boolean useSacks = true, useInventory = true;
    public static volatile List<String[]> craftables = List.of();
    public record Goal(String id, String name, int amount) {}
    /** 경매장 검색 결과 */
    public record AuctionHit(String uuid, String name, String tier, double price, boolean bin, long end) {}
    public static volatile List<AuctionHit> auctionHits = List.of();
    public static volatile boolean auctionLoading = false;
    public static volatile long auctionUpdated = 0;
    public record Material(String id, String name, double have, double need, boolean off) {}
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
    /** kind: forge, composter, timeTower, fetchur, experiment, hoppity, mayor (옛 프로그램이면 other). start 를 모르면 0 */
    public record Timer(String kind, String label, long at, boolean done, long start) {}

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
                // 프로그램이 모드를 자동으로 업데이트할 수 있게 mods 폴더 위치를 알려 준다
                hello.addProperty("modsDir", net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().toString());
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
            case "ah.results" -> {
                List<AuctionHit> list = new ArrayList<>();
                for (JsonElement e : m.getAsJsonArray("list")) {
                    JsonObject r = e.getAsJsonObject();
                    list.add(new AuctionHit(r.get("uuid").getAsString(), r.get("name").getAsString(), r.get("tier").getAsString(),
                        r.get("price").getAsDouble(), r.get("bin").getAsBoolean(), r.get("end").getAsLong()));
                }
                auctionHits = list;
                auctionLoading = m.has("loading") && m.get("loading").getAsBoolean();
                auctionUpdated = m.has("updated") ? m.get("updated").getAsLong() : 0;
            }
            case "player.status" -> Stalk.result(m);
            case "craftables" -> {
                List<String[]> list = new ArrayList<>();
                for (JsonElement e : m.getAsJsonArray("list")) {
                    JsonArray a = e.getAsJsonArray();
                    list.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString()});
                }
                craftables = list;
            }
            case "plan" -> {
                List<Goal> gs = new ArrayList<>();
                if (m.has("targets")) for (JsonElement e : m.getAsJsonArray("targets")) {
                    JsonObject r = e.getAsJsonObject();
                    gs.add(new Goal(r.get("id").getAsString(), r.get("name").getAsString(), r.get("amount").getAsInt()));
                }
                goals = gs;
                if (m.has("sacks")) useSacks = m.get("sacks").getAsBoolean();
                if (m.has("inventory")) useInventory = m.get("inventory").getAsBoolean();
                currentGoal = m.has("current") ? m.get("current").getAsString() : null;
                currentAmount = m.has("amount") ? m.get("amount").getAsInt() : 1;
                List<Material> ms = new ArrayList<>();
                if (m.has("all")) for (JsonElement e : m.getAsJsonArray("all")) {
                    JsonObject r = e.getAsJsonObject();
                    ms.add(new Material(r.get("id").getAsString(), r.get("name").getAsString(), r.get("have").getAsDouble(),
                        r.get("need").getAsDouble(), r.has("off") && r.get("off").getAsBoolean()));
                }
                materials = ms;
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
                    String label = r.get("label").getAsString();
                    // 옛 프로그램(2.0.7 이하)은 종류 없이 이모지가 붙은 이름만 보낸다
                    String kind = r.has("kind") ? r.get("kind").getAsString() : (label.startsWith("포지") ? "forge" : "other");
                    if (!r.has("kind")) label = label.replaceAll("[^\\x{0000}-\\x{FFFF}]\\s*", "").trim();
                    list.add(new Timer(kind, label, r.get("at").getAsLong(),
                        r.has("done") && r.get("done").getAsBoolean(), r.has("start") ? r.get("start").getAsLong() : 0));
                }
                timers = list;
            }
            default -> {}
        }
    }

    public static void auctionSearch(String q, boolean binOnly) {
        JsonObject m = new JsonObject();
        m.addProperty("t", "ah.search");
        m.addProperty("q", q);
        m.addProperty("bin", binOnly);
        send(m);
    }

    /** 게임 안 제작 플래너 → 프로그램 (앱의 플래너 설정을 바꾼다) */
    public static void planCommand(String type, String id, int amount) {
        JsonObject m = new JsonObject();
        m.addProperty("t", "plan." + type);
        if (id != null) m.addProperty("id", id);
        m.addProperty("amount", amount);
        send(m);
    }

    public static void planExclude(String target, String id, boolean off) {
        JsonObject m = new JsonObject();
        m.addProperty("t", "plan.exclude");
        m.addProperty("target", target);
        m.addProperty("id", id);
        m.addProperty("off", off);
        send(m);
    }

    public static void planOption(boolean sacks, boolean inventory) {
        JsonObject m = new JsonObject();
        m.addProperty("t", "plan.option");
        m.addProperty("sacks", sacks);
        m.addProperty("inventory", inventory);
        send(m);
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
