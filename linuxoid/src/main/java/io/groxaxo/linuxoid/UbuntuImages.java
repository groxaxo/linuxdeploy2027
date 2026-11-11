package io.groxaxo.linuxoid;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.*;
import javax.net.ssl.HttpsURLConnection;

/** Only Canonical's HTTPS Ubuntu Base archive is used. No arbitrary download URLs. */
public final class UbuntuImages {
  public static final String ORIGIN = "https://cdimage.ubuntu.com/ubuntu-base/releases/";

  public interface Progress {
    void update(String line);
  }

  public static final class Image {
    public final String name, sha256, url;

    Image(String name, String hash, String url) {
      this.name = name;
      this.sha256 = hash;
      this.url = url;
    }
  }

  private static HttpsURLConnection connection(String url) throws IOException {
    URL u = new URL(url);
    if (!u.getProtocol().equals("https") || !u.getHost().equals("cdimage.ubuntu.com"))
      throw new IOException("Unexpected image host");
    HttpsURLConnection c = (HttpsURLConnection) u.openConnection();
    c.setConnectTimeout(20000);
    c.setReadTimeout(30000);
    c.setInstanceFollowRedirects(false);
    c.setRequestProperty("User-Agent", "Linuxoid/0.1");
    if (c.getResponseCode() != 200) {
      int code = c.getResponseCode();
      c.disconnect();
      throw new IOException("Ubuntu archive returned HTTP " + code);
    }
    return c;
  }

  private static String text(String url) throws IOException {
    HttpsURLConnection c = connection(url);
    try (InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) {
        out.write(b, 0, n);
        if (out.size() > 1024 * 1024) throw new IOException("Unexpectedly large manifest");
      }
      return out.toString(StandardCharsets.UTF_8.name());
    } finally {
      c.disconnect();
    }
  }

  public static List<String> releases() throws IOException {
    Matcher m = Pattern.compile("href=\"([0-9]{2}\\.[0-9]{2})/\"").matcher(text(ORIGIN));
    Set<String> all = new HashSet<>();
    while (m.find()) all.add(m.group(1));
    List<String> out = new ArrayList<>(all);
    out.sort((a, b) -> compareVersion(b, a));
    return out;
  }

  public static int compareVersion(String a, String b) {
    String[] x = a.split("\\."), y = b.split("\\.");
    for (int i = 0; i < Math.max(x.length, y.length); i++) {
      int n =
          Integer.compare(
              i < x.length ? Integer.parseInt(x[i]) : 0, i < y.length ? Integer.parseInt(y[i]) : 0);
      if (n != 0) return n;
    }
    return 0;
  }

  public static Image parse(String manifest, String release, String arch) throws IOException {
    if (!release.matches("[0-9]{2}\\.[0-9]{2}") || !arch.matches("amd64|arm64|armhf|i386"))
      throw new IOException("Invalid image selection");
    Pattern line =
        Pattern.compile(
            "(?m)^([0-9a-fA-F]{64})\\s+\\*?(ubuntu-base-([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)-base-"
                + arch
                + "\\.tar\\.gz)\\s*$");
    Matcher m = line.matcher(manifest);
    Image best = null;
    String version = "0";
    while (m.find()) {
      String v = m.group(3);
      if ((v.equals(release) || v.startsWith(release + ".")) && compareVersion(v, version) > 0) {
        version = v;
        best =
            new Image(
                m.group(2),
                m.group(1).toLowerCase(Locale.ROOT),
                ORIGIN + release + "/release/" + m.group(2));
      }
    }
    if (best == null)
      throw new IOException(
          "No Ubuntu Base "
              + release
              + " image is available for "
              + arch
              + ". Choose another release.");
    return best;
  }

  public static Image resolve(String release, String arch) throws IOException {
    return parse(text(ORIGIN + release + "/release/SHA256SUMS"), release, arch);
  }

  public static String hash(File f) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream in = new FileInputStream(f)) {
        byte[] b = new byte[65536];
        int n;
        while ((n = in.read(b)) != -1) digest.update(b, 0, n);
      }
      StringBuilder s = new StringBuilder();
      for (byte b : digest.digest()) s.append(String.format(Locale.ROOT, "%02x", b & 255));
      return s.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public static File download(File cache, Image image, Progress progress) throws IOException {
    File target = new File(cache, image.sha256 + ".tar.gz");
    if (target.exists() && hash(target).equals(image.sha256)) {
      progress.update("Using verified Ubuntu Base image");
      return target;
    }
    File partial = new File(cache, image.sha256 + ".part");
    HttpsURLConnection c = connection(image.url);
    try (InputStream in = c.getInputStream();
        OutputStream out = new FileOutputStream(partial)) {
      byte[] b = new byte[65536];
      int n;
      long total = 0, last = -1;
      while ((n = in.read(b)) != -1) {
        total += n;
        if (total > 1024L * 1024 * 1024) throw new IOException("Image exceeds download limit");
        out.write(b, 0, n);
        long mb = total / (1024 * 1024);
        if (mb != last) {
          progress.update("Downloading Ubuntu Base · " + mb + " MB");
          last = mb;
        }
      }
    } finally {
      c.disconnect();
    }
    if (!hash(partial).equals(image.sha256)) {
      partial.delete();
      throw new IOException("Ubuntu image checksum mismatch; installation refused");
    }
    if (!partial.renameTo(target)) throw new IOException("Could not save verified image");
    progress.update("SHA-256 verified · " + image.name);
    return target;
  }
}
