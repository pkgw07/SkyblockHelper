package sbhelper.mod;

/**
 * 상자 화면을 옮겨 다닐 때 (Storage → 엔더 상자 페이지 등) 마우스가 가운데로 가지 않게:
 * 상자 화면이 닫힌 직후 다른 화면이 열리면 닫히기 전 위치로 되돌린다. 내 화면에서만 보이는 일 (게임 조작 아님)
 */
public final class Cursor {
    private static double x, y;
    private static long closedAt = 0;

    public static void save(double cx, double cy) { x = cx; y = cy; closedAt = System.currentTimeMillis(); }

    /** 되돌릴 위치 (없으면 null) */
    public static double[] restore() {
        if (!Config.get().keepCursor || System.currentTimeMillis() - closedAt > 1000) return null;
        closedAt = 0;
        return new double[]{x, y};
    }

    private Cursor() {}
}
