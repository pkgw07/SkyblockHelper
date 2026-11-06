package sbhelper.mod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 모드 기능에서 오류가 나도 게임은 멈추지 않게 (화면이 까맣게 되거나 튕기지 않게).
 * 오류는 로그(latest.log)에 한 번만 남긴다.
 */
public final class Safe {
    private static final Logger LOG = LoggerFactory.getLogger("SkyblockHelper");
    private static final Set<String> logged = new HashSet<>();

    public static void run(String where, Runnable r) {
        try { r.run(); } catch (Throwable t) { log(where, t); }
    }

    public static <T> T get(String where, Supplier<T> s, T fallback) {
        try { return s.get(); } catch (Throwable t) { log(where, t); return fallback; }
    }

    private static void log(String where, Throwable t) {
        synchronized (logged) {
            if (!logged.add(where + ":" + t.getClass().getName())) return;
        }
        LOG.error("[SkyblockHelper] {} 에서 오류 (게임은 계속돼요)", where, t);
    }

    private Safe() {}
}
