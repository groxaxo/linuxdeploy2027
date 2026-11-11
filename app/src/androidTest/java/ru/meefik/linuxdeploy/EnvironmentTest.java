package ru.meefik.linuxdeploy;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

/** Integration tests against the APK's actual extracted environment on a rooted emulator. */
@RunWith(AndroidJUnit4.class)
public class EnvironmentTest {
    private static Context context;

    @BeforeClass public static void installEnvironment() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (!EnvUtils.isLatestVersion(context)) {
            assertTrue("Environment extraction/setup failed", EnvUtils.updateEnv(context));
        }
    }

    @Test public void environmentIsRunnable() {
        assertTrue(EnvUtils.isLatestVersion(context));
        assertTrue(new File(PrefStore.getBinDir(context), "sh").canExecute());
        assertTrue(EnvUtils.cli(context, "help", null));
    }

    @Test public void cliPreservesFailureStatus() throws Exception {
        File cli = new File(PrefStore.getBinDir(context), "linuxdeploy");
        byte[] original = Files.readAllBytes(cli.toPath());
        try {
            Files.write(cli.toPath(), "#!/system/bin/sh\nexit 42\n".getBytes(StandardCharsets.UTF_8));
            assertFalse("CLI failure must reach the caller", EnvUtils.cli(context, "status", null));
        } finally {
            Files.write(cli.toPath(), original);
        }
    }

    @Test public void bundledZstdWorksWithoutTermux() {
        java.util.List<String> commands = new java.util.ArrayList<>();
        commands.add("set -e");
        commands.add("printf 'ubuntu-bootstrap-test' | zstd -q | zstdcat > tmp/zstd-test.txt");
        commands.add("test \"$(cat tmp/zstd-test.txt)\" = ubuntu-bootstrap-test");
        assertTrue("Bundled zstd compression/decompression failed", EnvUtils.exec(context, "sh", commands));
    }

    @Test public void configRoundTripPreservesEquals() throws Exception {
        ParamUtils store = new ParamUtils("roundtrip_test", new String[]{"source_path"});
        store.set(context, "source_path", "https://example.org/rootfs?release=noble");
        File config = new File(context.getCacheDir(), "roundtrip.conf");
        assertTrue(store.dump(context, config));
        assertTrue(store.restore(context, config));
        assertEquals("https://example.org/rootfs?release=noble", store.get(context, "source_path"));
    }

    @Test public void configKeysAreIndependentOfDeviceLocale() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(new java.util.Locale("tr", "TR"));
            ParamUtils store = new ParamUtils("locale_test", new String[]{"distrib", "suite"});
            assertTrue(store.get(context).containsKey("DISTRIB"));
            assertTrue(store.get(context).containsKey("SUITE"));
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }
}
