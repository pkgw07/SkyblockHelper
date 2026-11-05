package sbhelper.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 게임 안 경매장 검색 창 (/sbh ah): 프로그램이 받아 둔 경매장 전체에서 이름으로 찾아 싼 순으로.
 * 누르면 /viewauction 으로 그 경매를 연다 (한 번 누르면 한 번).
 */
public final class AuctionSearchScreen extends SearchScreen {
    private static boolean binOnly = true;

    public AuctionSearchScreen(Screen parent) { super("경매장 검색", parent); }

    @Override protected String subtitle() {
        if (!Link.connected) return "프로그램을 켜 주세요";
        if (Link.auctionLoading) return "경매장 전체를 받는 중이에요 (조금 걸려요)";
        long ago = Link.auctionUpdated > 0 ? (System.currentTimeMillis() - Link.auctionUpdated) / 60_000 : -1;
        return ago >= 0 ? "프로그램 경매장 데이터 · " + ago + "분 전" : "프로그램 경매장 데이터";
    }

    @Override protected String hint() { return "아이템 이름 (2글자 이상)…"; }

    @Override protected void search(String q) { if (q.length() >= 2) Link.auctionSearch(q, binOnly); else Link.auctionHits = List.of(); }

    @Override protected String[] option() { return new String[]{"BIN 만", binOnly ? "1" : "0"}; }

    @Override protected void toggleOption() { binOnly = !binOnly; }

    @Override protected String footer() { return "누르면 그 경매를 열어요 · 가격은 BIN 또는 지금 입찰가"; }

    @Override
    protected List<Row> rows() {
        List<Row> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Link.AuctionHit h : Link.auctionHits) {
            String right = Fmt.coins(h.price()) + (h.bin() ? "" : " 입찰") + " · " + Fmt.left(h.end() - now);
            String uuid = h.uuid();
            out.add(new Row(h.name() + "  §7" + h.tier().toLowerCase(Locale.ROOT), right, h.bin() ? Hud.GOLD : 0xFF55FFFF, () -> {
                closeNow();
                Minecraft.getInstance().getConnection().sendCommand("viewauction " + uuid);
            }, "끝나는 시각 " + Tooltips.clock(h.end())));
        }
        return out;
    }
}
