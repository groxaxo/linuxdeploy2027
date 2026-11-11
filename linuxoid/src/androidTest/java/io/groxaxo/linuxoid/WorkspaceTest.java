package io.groxaxo.linuxoid;

import static org.junit.Assert.*;

import android.content.Context;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

/** Opt-in live integration: installs fresh rootfses on a disposable rooted emulator. */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class WorkspaceTest {
  @BeforeClass
  public static void requireDisposableDevice() {
    Assume.assumeTrue(
        "Live installs require -e linuxoid.disposable yes on a disposable device",
        "yes".equals(InstrumentationRegistry.getArguments().getString("linuxoid.disposable")));
  }

  private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
  private final LinuxoidApp app = (LinuxoidApp) context.getApplicationContext();
  private final StringBuilder transcript = new StringBuilder();

  private void log(String line) {
    transcript.append(line).append('\n');
    Log.i("LinuxoidTest", line);
  }

  private Profile install(String release, Profile.Mode mode) throws Exception {
    int slot = 0;
    for (Profile p : app.profiles) slot = Math.max(slot, p.display);
    Profile p = Profile.create(release, Engine.architecture(), mode, slot, false);
    synchronized (app) {
      app.profiles.add(p);
      app.save();
    }
    try {
      new Engine(context, this::log).install(p, "Linuxoid-Test-Only-2026", "10.0.2.3");
      p.state = "Running";
      app.save();
      return p;
    } catch (Exception e) {
      p.state = "Incomplete";
      app.save();
      throw new AssertionError(transcript.toString(), e);
    }
  }

  private void check(Profile p, String command) throws Exception {
    Engine e = new Engine(context, this::log);
    RootShell.run(
        e.prelude(p) + "owned\ninside /bin/sh -ec " + RootShell.quote(command), this::log);
  }

  @Test
  public void test01_minimalResoluteAndRealPty() throws Exception {
    Profile p;
    if ("yes".equals(InstrumentationRegistry.getArguments().getString("linuxoid.reuseMinimal"))) {
      p = null;
      for (Profile candidate : app.profiles)
        if (candidate.mode == Profile.Mode.MINIMAL && candidate.release.equals("26.04"))
          p = candidate;
      assertNotNull("No provisioned minimal workspace is available", p);
      log("Reusing the workspace created through the signed release UI");
    } else {
      p = install("26.04", Profile.Mode.MINIMAL);
    }

    check(
        p,
        ". /etc/os-release; test \"$VERSION_CODENAME\" = resolute; test -z \"$(dpkg --audit)\";"
            + " test ! -x /usr/sbin/sshd; test \"$(id -u linuxoid)\" = 60000; id linuxoid; /bin/su"
            + " - linuxoid -c 'sudo -n true'");
    int[] session = Pty.open(new Engine(context, this::log).terminalCommand(p));
    assertNotNull("Native PTY could not open", session);
    StringBuilder output = new StringBuilder();
    CountDownLatch ready = new CountDownLatch(1);
    ExecutorService worker = Executors.newSingleThreadExecutor();
    Future<Boolean> result =
        worker.submit(
            () -> {
              byte[] bytes;
              while ((bytes = Pty.read(session[0])) != null) {
                output.append(new String(bytes, StandardCharsets.UTF_8));
                if (output.toString().contains("linuxoid@")) ready.countDown();
                if (output.toString().contains("LINUXOID_PTY_PASS")) return true;
              }
              return false;
            });
    try {
      Pty.resize(session[0], 80, 24);

      assertTrue("No interactive prompt: " + output, ready.await(20, TimeUnit.SECONDS));
      Pty.write(
          session[0],
          "test -o monitor && test \"$(id -un)\" = linuxoid && printf 'LINUXOID_%s_PASS\\n' PTY\nexit\n"
              .getBytes(StandardCharsets.UTF_8));
      assertTrue("PTY output: " + output, result.get(30, TimeUnit.SECONDS));
    } finally {
      Pty.close(session[0], session[1]);
      worker.shutdownNow();
    }
    check(p, "touch /home/linuxoid/.keep");
    boolean refused = false;
    try {
      new Engine(context, this::log).install(p, "", "10.0.2.3");
    } catch (java.io.IOException expected) {
      refused = true;
    }
    assertTrue("An existing workspace must never be reinstalled over", refused);
    check(p, "test -f /home/linuxoid/.keep");
    new Engine(context, this::log).stop(p);
    p.state = "Stopped";
    app.save();
    RootShell.run("! grep -F " + RootShell.quote(p.root()) + " /proc/mounts", this::log);
    new Engine(context, this::log).start(p);
    p.state = "Running";
    app.save();
    check(p, "test -r /proc/version; /bin/su - linuxoid -c 'sudo -n true'");
  }

  @Test
  public void test02_terminalJammy() throws Exception {
    Profile p = install("22.04", Profile.Mode.SSH);
    check(
        p,
        ". /etc/os-release; test \"$VERSION_CODENAME\" = jammy; test -z \"$(dpkg --audit)\";"
            + " /usr/sbin/sshd -t; test -s /run/linuxoid-sshd.pid");
  }

  @Test
  public void test03_minimalNoble() throws Exception {
    Profile p = install("24.04", Profile.Mode.MINIMAL);
    check(
        p,
        ". /etc/os-release; test \"$VERSION_CODENAME\" = noble; test -z \"$(dpkg --audit)\"; test !"
            + " -x /usr/sbin/sshd");
  }

  @Test
  public void test04_desktopResolute() throws Exception {
    Profile p;
    if ("yes".equals(InstrumentationRegistry.getArguments().getString("linuxoid.reuseDesktop"))) {
      p = null;
      for (Profile candidate : app.profiles)
        if (candidate.mode == Profile.Mode.DESKTOP) p = candidate;
      assertNotNull("No provisioned desktop is available", p);
      log("Reusing an already installed desktop to verify the lifecycle correction");
    } else {
      p = install("26.04", Profile.Mode.DESKTOP);
    }
    check(
        p,
        ". /etc/os-release; test \"$VERSION_CODENAME\" = resolute; test -z \"$(dpkg --audit)\";"
            + " /usr/sbin/sshd -t; id linuxoid");
    assertVnc(p);
    Engine e = new Engine(context, this::log);
    e.start(p); // Repeated start must not spawn another server or fail.
    e.stop(p);
    RootShell.run("! grep -F " + RootShell.quote(p.root()) + " /proc/mounts", this::log);
    e.start(p);
    assertVnc(p);
  }

  private void assertVnc(Profile p) throws Exception {
    try (java.net.Socket socket = new java.net.Socket("127.0.0.1", 5900 + p.display)) {
      socket.setSoTimeout(10000);
      byte[] banner = new byte[12];
      new java.io.DataInputStream(socket.getInputStream()).readFully(banner);
      assertEquals("RFB 003.008\n", new String(banner, StandardCharsets.US_ASCII));
    }
  }
}
