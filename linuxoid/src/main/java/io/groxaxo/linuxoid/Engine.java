package io.groxaxo.linuxoid;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class Engine {
  private final Context context;
  private final UbuntuImages.Progress log;
  private File tools;

  public Engine(Context c, UbuntuImages.Progress log) {
    this.context = c;
    this.log = log;
  }

  public static String architecture() {
    for (String abi : Build.SUPPORTED_ABIS) {
      if (abi.equals("arm64-v8a")) return "arm64";
      if (abi.equals("armeabi-v7a")) return "armhf";
      if (abi.equals("x86_64")) return "amd64";
      if (abi.equals("x86")) return "i386";
    }
    throw new IllegalStateException("This CPU architecture is not supported.");
  }

  private void prepare() throws IOException {
    tools = new File(context.getFilesDir(), "tools");
    if (!tools.isDirectory() && !tools.mkdirs())
      throw new IOException("Cannot create tool directory");
    for (String name : new String[] {"busybox", "rootfs-mount"}) {
      File bb = new File(tools, name);
      if (!bb.exists()) {
        File tmp = new File(tools, name + ".part");
        try (InputStream in = context.getAssets().open("tools/" + architecture() + "/" + name);
            OutputStream out = new FileOutputStream(tmp)) {
          byte[] buffer = new byte[32768];
          int n;
          while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
        if (!tmp.setExecutable(true, false) || !tmp.renameTo(bb))
          throw new IOException("Cannot prepare native tools");
      }
    }
  }

  private String asset(String name) throws IOException {
    try (InputStream in = context.getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      return out.toString(StandardCharsets.UTF_8.name());
    }
  }

  public String prelude(Profile p) throws IOException {
    prepare();
    return "[ \"$(id -u)\" = 0 ] || { echo 'Root access is required.'; exit 1; }\n"
        + "BB="
        + RootShell.quote(new File(tools, "busybox").getAbsolutePath())
        + "\n"
        + "\"$BB\" --install -s "
        + RootShell.quote(tools.getAbsolutePath())
        + "\n"
        + "export PATH="
        + RootShell.quote(tools.getAbsolutePath() + ":/system/bin:/system/xbin")
        + "\n"
        + "OPS="
        + RootShell.quote(new File(tools, "rootfs-mount").getAbsolutePath())
        + "\n"
        + "BASE="
        + RootShell.quote(p.base())
        + "\nROOT="
        + RootShell.quote(p.root())
        + "\nID="
        + RootShell.quote(p.id)
        + "\n"
        + "MODE="
        + p.mode.name()
        + "\nARCH="
        + p.arch
        + "\nSSH_PORT="
        + p.sshPort
        + "\nDISPLAY_NUM="
        + p.display
        + "\n"
        + "LISTEN="
        + (p.lan ? "0.0.0.0" : "127.0.0.1")
        + "\n"
        + asset("engine.sh")
        + "\n";
  }

  public void install(Profile p, String password, String dns) throws Exception {
    if (p.mode != Profile.Mode.MINIMAL
        && (password == null
            || password.length() < 8
            || password.length() > 64
            || password.matches("(?s).*[\\p{Cntrl}].*")))
      throw new IllegalArgumentException(
          "Use a password of 8–64 characters without control characters.");
    if (!dns.matches("[0-9a-fA-F:.]+"))
      throw new IllegalArgumentException("Enter a DNS server IP address.");
    log.update("Checking the official Ubuntu Base manifest");
    UbuntuImages.Image image = UbuntuImages.resolve(p.release, p.arch);
    File archive = UbuntuImages.download(context.getCacheDir(), image, log);
    RootShell.run(
        prelude(p)
            + "ARCHIVE="
            + RootShell.quote(archive.getAbsolutePath())
            + "\nDNS="
            + RootShell.quote(dns)
            + "\nPASSWORD="
            + RootShell.quote(password == null ? "" : password)
            + "\ninstall_container\nstart_container",
        log);
  }

  public void start(Profile p) throws Exception {
    RootShell.run(prelude(p) + "start_container", log);
  }

  public void stop(Profile p) throws Exception {
    RootShell.run(prelude(p) + "stop_container", log);
  }

  public String status(Profile p) throws Exception {
    java.util.concurrent.atomic.AtomicReference<String> state =
        new java.util.concurrent.atomic.AtomicReference<>("Incomplete");
    RootShell.run(
        prelude(p)
            + "owned\n"
            + "if [ ! -f \"$BASE/.ready\" ]; then echo STATE=Incomplete; elif mounted"
            + " \"$ROOT/proc\"; then echo STATE=Running; else echo STATE=Stopped; fi",
        line -> {
          if (line.startsWith("STATE=")) state.set(line.substring(6));
        });
    return state.get();
  }

  public String terminalCommand(Profile p) throws IOException {
    prepare();
    return "export TERM=xterm-256color\nexec "
        + RootShell.quote(new File(tools, "busybox").getAbsolutePath())
        + " chroot "
        + RootShell.quote(p.root())
        + " /bin/su - linuxoid --session-command 'stty echo; exec /bin/bash -l'\n";
  }
}
