package sbhelper.mod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.libc.LibCStdlib;

import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 장소별 음악: config/skyblockhelper/music 폴더에 "지역이름.ogg" (예: Hub.ogg, Dwarven Mines.ogg) 를 넣으면
 * 그 지역에 들어갈 때 반복해서 튼다. 지역을 나가면 멈춘다. (게임 소리 장치 OpenAL 로 직접 재생)
 */
public final class Music {
    private static String playingFor = "";
    private static int source = 0, buffer = 0;
    private static volatile boolean loading = false;

    public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("skyblockhelper").resolve("music"); }

    /** 2초마다 (지역이 바뀌었는지) */
    public static void tick() {
        Config c = Config.get();
        String area = c.areaMusic && SkyBlock.inSkyblock ? SkyBlock.area : "";
        Path file = area.isEmpty() ? null : find(area);
        String key = file == null ? "" : file.toString();
        if (key.equals(playingFor) || loading) {
            if (source != 0) AL10.alSourcef(source, AL10.AL_GAIN, c.musicVolume);
            return;
        }
        stop();
        playingFor = key;
        if (file == null) return;
        loading = true;
        Thread t = new Thread(() -> decode(file, key), "sbh-music");
        t.setDaemon(true);
        t.start();
    }

    /** 이 지역에 맞는 파일 (이름이 지역 이름에 들어 있는 것 중 가장 긴 이름) */
    private static Path find(String area) {
        Path d = dir();
        if (!Files.isDirectory(d)) return null;
        String a = area.toLowerCase(Locale.ROOT);
        Path best = null;
        int bestLen = 0;
        try (Stream<Path> s = Files.list(d)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!n.endsWith(".ogg")) continue;
                n = n.substring(0, n.length() - 4).trim();
                if (!n.isEmpty() && a.contains(n) && n.length() > bestLen) { best = p; bestLen = n.length(); }
            }
        } catch (Exception ignored) {}
        return best;
    }

    private static void decode(Path file, String key) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ch = stack.mallocInt(1), rate = stack.mallocInt(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_filename(file.toString(), ch, rate);
            if (pcm == null) { loading = false; return; }
            int channels = ch.get(0), sampleRate = rate.get(0);
            Minecraft.getInstance().execute(() -> {
                try {
                    if (!key.equals(playingFor)) return;
                    buffer = AL10.alGenBuffers();
                    AL10.alBufferData(buffer, channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, pcm, sampleRate);
                    source = AL10.alGenSources();
                    AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
                    AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_TRUE);
                    AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                    AL10.alSourcef(source, AL10.AL_GAIN, Config.get().musicVolume);
                    AL10.alSourcePlay(source);
                } catch (Throwable ignored) {
                } finally {
                    LibCStdlib.free(pcm);
                    loading = false;
                }
            });
        } catch (Throwable e) {
            loading = false;
        }
    }

    public static void stop() {
        try {
            if (source != 0) { AL10.alSourceStop(source); AL10.alDeleteSources(source); }
            if (buffer != 0) AL10.alDeleteBuffers(buffer);
        } catch (Throwable ignored) {}
        source = 0;
        buffer = 0;
        playingFor = "";
    }

    public static void openFolder() {
        try {
            Files.createDirectories(dir());
            net.minecraft.util.Util.getPlatform().openPath(dir());
        } catch (Exception ignored) {}
    }

    private Music() {}
}
