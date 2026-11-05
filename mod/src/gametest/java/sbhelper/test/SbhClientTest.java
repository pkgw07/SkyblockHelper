package sbhelper.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import sbhelper.mod.HudEditScreen;
import sbhelper.mod.Link;
import sbhelper.mod.SettingsScreen;
import sbhelper.mod.SkyBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 진짜 게임을 띄워서 모드 화면을 열고 사진을 찍는다 (CI: xvfb).
 * 모드가 게임에 제대로 붙는지(mixin), 화면이 깨지지 않는지 확인용.
 */
public class SbhClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        SkyBlock.TEST = true;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            ctx.waitTicks(60);
            fakeData();
            ctx.takeScreenshot("01-hud");

            for (int i = 0; i < 9; i++) {
                SettingsScreen.selectTab(i);
                ctx.setScreen(() -> new SettingsScreen(null));
                ctx.waitTicks(15);
                ctx.takeScreenshot("02-settings-" + i);
            }
            ctx.setScreen(() -> new HudEditScreen(null));
            ctx.waitTicks(10);
            ctx.takeScreenshot("03-hud-edit");

            // Storage 미리보기: 페이지를 차례로 열어 기억시킨 뒤 Storage 화면
            ctx.setScreen(() -> page(6, "Ender Chest (1/3)", 0));
            ctx.waitTicks(15);
            ctx.setScreen(() -> page(6, "Ender Chest (2/3)", 7));
            ctx.waitTicks(15);
            ctx.setScreen(() -> page(4, "Medium Backpack (Slot #1)", 3));
            ctx.waitTicks(15);
            ctx.setScreen(SbhClientTest::storageMenu);
            ctx.waitTicks(15);
            ctx.takeScreenshot("04-storage");
            ctx.setScreen(() -> page(6, "Ender Chest (2/3)", 7));
            ctx.waitTicks(15);
            ctx.takeScreenshot("05-storage-page");

            ctx.setScreen(SbhClientTest::pets);
            ctx.waitTicks(10);
            ctx.takeScreenshot("06-pets");

            ctx.setScreen(SbhClientTest::auctions);
            ctx.waitTicks(10);
            ctx.takeScreenshot("07-auctions");
            // 경매 종료 시각 툴팁: 첫 번째 아이템 위에 마우스
            double[] pos = ctx.computeOnClient(mc -> {
                double scale = mc.getWindow().getGuiScale();
                var cs = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.gui.screen();
                var slot = cs.getMenu().slots.get(10);
                var acc = (sbhelper.mod.mixin.ContainerScreenAccessor) cs;
                return new double[]{(acc.sbh$leftPos() + slot.x + 8) * scale, (acc.sbh$topPos() + slot.y + 8) * scale};
            });
            ctx.getInput().setCursorPos(pos[0], pos[1]);
            ctx.waitTicks(5);
            ctx.takeScreenshot("08-auction-tooltip");
            // 큰 화면 (1920x1080, GUI 크기 3): Storage 가로 3개, 펫 %, 업데이트 창
            ctx.setScreen(() -> null);
            ctx.runOnClient(mc -> mc.options.guiScale().set(3));
            ctx.getInput().resizeWindow(1920, 1080);
            ctx.waitTicks(10);
            ctx.setScreen(() -> page(6, "Ender Chest (3/3)", 4));
            ctx.waitTicks(15);
            ctx.setScreen(() -> page(3, "Small Backpack (Slot #2)", 9));
            ctx.waitTicks(15);
            ctx.setScreen(SbhClientTest::storageMenu);
            ctx.waitTicks(15);
            ctx.takeScreenshot("09-storage-wide");
            ctx.setScreen(SbhClientTest::pets);
            ctx.waitTicks(10);
            ctx.takeScreenshot("10-pets-wide");
            ctx.setScreen(() -> new sbhelper.mod.PlannerScreen(null));
            ctx.waitTicks(15);
            ctx.takeScreenshot("12-planner");
            // 3단계: 경매장 검색 · 로그 검색 · 마법 부여 도우미
            long now2 = System.currentTimeMillis();
            Link.auctionHits = List.of(
                new Link.AuctionHit("a1", "Hyperion", "LEGENDARY", 812_000_000, true, now2 + 3 * 3_600_000L),
                new Link.AuctionHit("a2", "Hyperion", "LEGENDARY", 829_500_000, true, now2 + 26 * 3_600_000L),
                new Link.AuctionHit("a3", "Hyperion", "MYTHIC", 905_000_000, false, now2 + 40 * 60_000L));
            Link.auctionUpdated = now2 - 5 * 60_000L;
            ctx.setScreen(() -> new sbhelper.mod.AuctionSearchScreen(null));
            ctx.waitTicks(5);
            ctx.getInput().typeChars("hyp");
            ctx.waitTicks(15);
            ctx.takeScreenshot("13-ah-search");
            ctx.setScreen(() -> new sbhelper.mod.LogScreen(null));
            ctx.waitTicks(5);
            ctx.getInput().typeChars("sbh-test");
            ctx.waitTicks(30);
            ctx.takeScreenshot("14-log-search");
            ctx.setScreen(SbhClientTest::enchant);
            ctx.waitTicks(10);
            ctx.takeScreenshot("15-enchant");
            sbhelper.mod.Updater.latest = "9.9.9";
            ctx.setScreen(() -> new sbhelper.mod.UpdateScreen(null));
            ctx.waitTicks(15);
            ctx.takeScreenshot("11-update");
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);
        }
    }

    private static void fakeData() {
        long now = System.currentTimeMillis();
        Link.connected = true;
        Link.timers = List.of(
            new Link.Timer("composter", "컴포스터 멈춤", now - 1000, true, 0),
            new Link.Timer("forge", "포지 #1 Refined Mithril", now - 5000, true, now - 3_600_000),
            new Link.Timer("forge", "포지 #2 Divan's Drill", now + 3 * 3_600_000L, false, now - 5 * 3_600_000L),
            new Link.Timer("forge", "포지 #3 Gemstone Mixture", now + 8 * 60_000L, false, now - 3_600_000L),
            new Link.Timer("timeTower", "Time Tower 충전", now + 5 * 3_600_000L, false, 0),
            new Link.Timer("hoppity", "초콜릿 알 등장", now + 25 * 60_000L, false, 0),
            new Link.Timer("fetchur", "Fetchur 요청 초기화", now + 9 * 3_600_000L, false, 0));
        Link.plan = new Link.Plan("Hyperion", 0.42, List.of(
            new Link.PlanRow("NECRON_HANDLE", "Necron's Handle", 1, 1),
            new Link.PlanRow("WITHER_CATALYST", "Wither Catalyst", 12, 24),
            new Link.PlanRow("GIANT_FRAGMENT_LASER", "L.A.S.R.'s Eye", 3, 8)), 2);
        Link.goals = List.of(new Link.Goal("HYPERION", "Hyperion", 1), new Link.Goal("TERMINATOR", "Terminator", 1),
            new Link.Goal("ENCHANTED_DIAMOND_BLOCK", "Enchanted Diamond Block", 32));
        Link.currentGoal = "HYPERION";
        Link.currentAmount = 1;
        Link.materials = List.of(
            new Link.Material("NECRON_HANDLE", "Necron's Handle", 1, 1, false),
            new Link.Material("WITHER_CATALYST", "Wither Catalyst", 12, 24, false),
            new Link.Material("GIANT_FRAGMENT_LASER", "L.A.S.R.'s Eye", 3, 8, false),
            new Link.Material("DIAMOND", "Diamond", 120000, 409600, false),
            new Link.Material("IRON_INGOT", "Iron Ingot", 0, 25600, true));
        Link.craftables = List.of(new String[]{"HYPERION", "Hyperion"}, new String[]{"TERMINATOR", "Terminator"});
    }

    private static ItemStack named(Item item, int count, String name, String... lore) {
        ItemStack st = new ItemStack(item, count);
        st.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        if (lore.length > 0) st.set(DataComponents.LORE, new ItemLore(Arrays.stream(lore).map(l -> (Component) Component.literal(l)).toList()));
        return st;
    }

    /** 이름으로 아이템 찾기 (없으면 유리판) */
    private static Item item(String id) {
        Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id));
        System.out.println("[sbh-test] item " + id + " -> " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it));
        return it == Items.AIR ? Items.GLASS_PANE : it;
    }

    private static ItemStack filler() { return named(item("black_stained_glass_pane"), 1, " "); }

    private static ContainerScreen chest(int rows, String title, List<ItemStack> items) {
        Minecraft mc = Minecraft.getInstance();
        SimpleContainer c = new SimpleContainer(rows * 9);
        for (int i = 0; i < items.size() && i < rows * 9; i++) c.setItem(i, items.get(i));
        ChestMenu menu = rows == 6 ? ChestMenu.sixRows(99, mc.player.getInventory(), c)
            : new ChestMenu(rows == 4 ? net.minecraft.world.inventory.MenuType.GENERIC_9x4 : net.minecraft.world.inventory.MenuType.GENERIC_9x3, 99, mc.player.getInventory(), c, rows);
        return new ContainerScreen(menu, mc.player.getInventory(), Component.literal(title));
    }

    /** 위쪽 줄은 아이템, 마지막 줄은 유리판 + Go Back (하이픽셀 페이지처럼) */
    private static ContainerScreen page(int rows, String title, int seed) {
        Item[] pool = {Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.ENDER_PEARL, Items.BLAZE_ROD, Items.NETHER_STAR, Items.DIAMOND_SWORD, Items.BOW, Items.REDSTONE, Items.SLIME_BALL};
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < (rows - 1) * 9; i++) {
            if ((i * 7 + seed) % 3 == 0) items.add(ItemStack.EMPTY);
            else { Item it = pool[(i + seed) % pool.length]; items.add(new ItemStack(it, it.getDefaultMaxStackSize() > 1 ? 1 + (i * 13 + seed) % 64 : 1)); }
        }
        for (int i = 0; i < 9; i++) items.add(i == 4 ? named(Items.ARROW, 1, "Go Back") : filler());
        return chest(rows, title, items);
    }

    private static ContainerScreen storageMenu() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 54; i++) items.add(filler());
        items.set(4, named(Items.CHEST, 1, "Storage"));
        for (int i = 1; i <= 3; i++) items.set(8 + i, named(item("purple_stained_glass_pane"), 1, "Ender Chest Page " + i, "Click to open!"));
        items.set(27, named(Items.PLAYER_HEAD, 1, "Backpack Slot 1", "Medium Backpack"));
        items.set(28, named(Items.PLAYER_HEAD, 1, "Backpack Slot 2", "Small Backpack"));
        return chest(6, "Storage", items);
    }

    private static ContainerScreen pets() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 54; i++) items.add(i < 9 || i >= 45 ? filler() : ItemStack.EMPTY);
        int[] lv = {1, 15, 37, 58, 76, 99, 100, 200};
        for (int i = 0; i < lv.length; i++) items.set(10 + i, named(Items.BONE, 1, "[Lvl " + lv[i] + "] Pet " + (i + 1)));
        return chest(6, "Pets (1/1)", items);
    }

    private static ContainerScreen enchant() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 54; i++) items.add(i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8 ? filler() : ItemStack.EMPTY);
        String[] names = {"Sharpness", "Smite", "Bane of Arthropods", "Critical", "First Strike", "Giant Killer", "Execute", "Lethality", "Ender Slayer", "Cubism"};
        for (int i = 0; i < names.length; i++) items.set(12 + (i / 5) * 9 + i % 5, named(Items.ENCHANTED_BOOK, 1, names[i], "Cost: " + (i + 1) * 10 + " Exp Levels"));
        items.set(19, named(Items.DIAMOND_SWORD, 1, "Aspect of the End"));
        return chest(6, "Enchant Item", items);
    }

    private static ContainerScreen auctions() {
        String me = Minecraft.getInstance().getUser().getName();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 54; i++) items.add(i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8 ? filler() : ItemStack.EMPTY);
        items.set(10, named(Items.DIAMOND_SWORD, 1, "Aspect of the End", "Seller: [MVP+] SomeSeller", "Buy it now: 1,250,000 coins", "", "Ends in: 5h 3m"));
        items.set(11, named(Items.BOW, 1, "Juju Shortbow", "Seller: [VIP] Another", "Top bid: 12,000,000 coins", "Bidder: [MVP+] " + me, "Ends in: 2h 10m 5s"));
        items.set(12, named(Items.GOLDEN_HELMET, 1, "Old Helmet", "Seller: Third", "Top bid: 300,000 coins", "Bidder: [VIP] SomeoneElse", "Your previous bid: 250,000 coins", "Ends in: 45m"));
        items.set(13, named(Items.ENDER_PEARL, 16, "Ender Pearl", "Seller: Fourth", "Buy it now: 9,000 coins", "Ends in: 1d 4h"));
        return chest(6, "Auctions Browser", items);
    }
}
