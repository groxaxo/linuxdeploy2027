package io.groxaxo.linuxoid;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class RootShell {
  public static String quote(String s) {
    if (s.indexOf('\0') >= 0) throw new IllegalArgumentException("NUL is not a shell argument");
    return "'" + s.replace("'", "'\\''") + "'";
  }

  public static void run(String script, UbuntuImages.Progress log)
      throws IOException, InterruptedException {
    Process p = new ProcessBuilder("su").redirectErrorStream(true).start();
    AtomicReference<IOException> failure = new AtomicReference<>();
    Thread writer =
        new Thread(
            () -> {
              try (OutputStream out = p.getOutputStream()) {
                out.write(
                    ("set -eu\numask 022\n" + script + "\nexit 0\n")
                        .getBytes(StandardCharsets.UTF_8));
              } catch (IOException e) {
                failure.set(e);
              }
            },
            "linuxoid-root-input");
    writer.start();
    try (BufferedReader in =
        new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = in.readLine()) != null) log.update(line);
    }
    int status = p.waitFor();
    writer.join();
    if (status != 0)
      throw new IOException("Root operation failed (exit " + status + "). See the activity log.");
    if (failure.get() != null) throw failure.get();
  }
}
