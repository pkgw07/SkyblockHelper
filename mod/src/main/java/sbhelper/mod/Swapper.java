package sbhelper.mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * 모드 업데이트 마무리 (Windows): 게임이 완전히 꺼진 뒤 예전 모드 파일을 지우고 받아 둔 새 파일로 바꾼다.
 * 게임과 같은 자바로 따로 잠깐 실행된다 (PowerShell · 배치 파일을 쓰지 않음). 게임 클래스는 쓰지 않는다.
 * 사용: Swapper <게임 PID> <새 파일(.jar.new)> <바꿀 이름(.jar)> [지울 예전 파일...]
 */
public final class Swapper {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) return;
        long pid = Long.parseLong(args[0]);
        Path fresh = Path.of(args[1]), target = Path.of(args[2]);
        ProcessHandle.of(pid).ifPresent(h -> {
            try { h.onExit().get(10, TimeUnit.MINUTES); } catch (Exception ignored) {}
        });
        Thread.sleep(500);
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                for (int i = 3; i < args.length; i++) {
                    Path old = Path.of(args[i]);
                    if (!old.equals(target)) Files.deleteIfExists(old);
                }
                if (Files.exists(fresh)) Files.move(fresh, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (Exception e) {
                Thread.sleep(500);
            }
        }
    }

    private Swapper() {}
}
