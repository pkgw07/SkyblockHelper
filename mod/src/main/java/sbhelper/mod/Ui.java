package sbhelper.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;

import java.util.HashMap;
import java.util.Map;

/** 모드 화면에서 같이 쓰는 그리기·애니메이션 도우미 */
final class Ui {
    /** 모서리 1칸을 깎은 상자 */
    static void round(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 < 3 || y2 - y1 < 3) { g.fill(x1, y1, x2, y2, color); return; }
        g.fill(x1 + 1, y1, x2 - 1, y2, color);
        g.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        g.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    /** 모서리를 2칸 둥글게 깎은 상자 */
    static void round2(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 < 5 || y2 - y1 < 5) { round(g, x1, y1, x2, y2, color); return; }
        g.fill(x1 + 2, y1, x2 - 2, y1 + 1, color);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, color);
        g.fill(x1, y1 + 2, x2, y2 - 2, color);
        g.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, color);
        g.fill(x1 + 2, y2 - 1, x2 - 2, y2, color);
    }

    /** 테두리가 있는 둥근 상자 */
    static void card(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int fill, int border) {
        round2(g, x1, y1, x2, y2, border);
        round2(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
    }

    /** 바깥으로 번지는 부드러운 그림자 */
    static void shadow(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, float alpha) {
        for (int i = 1; i <= 6; i++) {
            int a = Math.round(alpha * (28 - i * 4));
            if (a <= 0) continue;
            round2(g, x1 - i, y1 - i + 2, x2 + i, y2 + i + 2, a << 24);
        }
    }

    static int alpha(int color, float a) { return ARGB.multiplyAlpha(color, Math.max(0, Math.min(1, a))); }

    static int mix(float t, int from, int to) { return ARGB.srgbLerp(Math.max(0, Math.min(1, t)), from, to); }

    /** 빠르게 시작해서 부드럽게 멈춤 */
    static float easeOut(float t) { t = Math.max(0, Math.min(1, t)); float u = 1 - t; return 1 - u * u * u; }

    /** 살짝 넘쳤다가 돌아옴 */
    static float easeBack(float t) {
        t = Math.max(0, Math.min(1, t));
        float c1 = 1.4f, c3 = c1 + 1, u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    static boolean inside(double mx, double my, int x1, int y1, int x2, int y2) { return mx >= x1 && mx < x2 && my >= y1 && my < y2; }

    static void click() {
        try { Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f)); } catch (Exception ignored) {}
    }

    /** 프레임마다 목표값으로 부드럽게 따라가는 값들 */
    static final class Anim {
        private final Map<String, Float> values = new HashMap<>();
        private long last = System.nanoTime();
        private float dt = 0;

        /** 프레임 시작에 한 번 */
        void frame() {
            long now = System.nanoTime();
            dt = Math.min(0.1f, (now - last) / 1e9f);
            last = now;
        }

        /** speed 가 클수록 빨리 따라감 (초당) */
        float to(String key, float target, float speed) {
            Float cur = values.get(key);
            float v = cur == null ? target : cur + (target - cur) * (1 - (float) Math.exp(-dt * speed));
            if (Math.abs(v - target) < 0.001f) v = target;
            values.put(key, v);
            return v;
        }

        void set(String key, float v) { values.put(key, v); }
    }

    private Ui() {}
}
