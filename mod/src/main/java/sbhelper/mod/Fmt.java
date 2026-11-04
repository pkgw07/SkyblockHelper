package sbhelper.mod;

/** 숫자·시간 표기 */
public final class Fmt {
    public static String coins(double v) {
        if (v < 0) return "-";
        if (v >= 1e9) return String.format("%.2fB", v / 1e9);
        if (v >= 1e6) return String.format("%.2fM", v / 1e6);
        if (v >= 1e4) return String.format("%.1fK", v / 1e3);
        if (v >= 100) return String.format("%,d", Math.round(v));
        return v % 1 == 0 ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    public static String count(double v) { return String.format("%,d", (long) Math.floor(v)); }

    public static String left(long ms) {
        if (ms <= 0) return "완료!";
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60;
        if (h >= 24) return (h / 24) + "일 " + (h % 24) + "시간";
        if (h > 0) return h + "시간 " + m + "분";
        if (m > 0) return m + "분 " + (s % 60) + "초";
        return (s % 60) + "초";
    }

    private Fmt() {}
}
