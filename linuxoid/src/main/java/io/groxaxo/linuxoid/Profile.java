package io.groxaxo.linuxoid;

import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/** Linuxoid's independent on-disk profile format. Passwords are never persisted. */
public final class Profile {
  public enum Mode {
    MINIMAL,
    SSH,
    DESKTOP
  }

  public final String id, release, arch;
  public final Mode mode;
  public final int sshPort, display;
  public final boolean lan;
  public String state;

  public Profile(String id, String release, String arch, Mode mode, int slot, boolean lan) {
    if (!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
      throw new IllegalArgumentException("Invalid profile ID");
    if (!release.matches("[0-9]{2}\\.[0-9]{2}"))
      throw new IllegalArgumentException("Invalid Ubuntu release");
    if (!arch.matches("amd64|arm64|armhf|i386"))
      throw new IllegalArgumentException("Unsupported architecture");
    if (slot < 0 || slot > 500) throw new IllegalArgumentException("Too many profiles");
    this.id = id;
    this.release = release;
    this.arch = arch;
    this.mode = mode;
    this.sshPort = 2222 + slot;
    this.display = 1 + slot;
    this.lan = lan;
    this.state = "New";
  }

  public static Profile create(String release, String arch, Mode mode, int slot, boolean lan) {
    return new Profile(UUID.randomUUID().toString(), release, arch, mode, slot, lan);
  }

  public String base() {
    return "/data/local/linuxoid/" + id;
  }

  public String root() {
    return base() + "/rootfs";
  }

  public String title() {
    return "Ubuntu " + release;
  }

  public String modeName() {
    return mode == Mode.MINIMAL ? "Minimal" : mode == Mode.SSH ? "Terminal + SSH" : "Desktop";
  }

  public JSONObject json() throws JSONException {
    return new JSONObject()
        .put("id", id)
        .put("release", release)
        .put("arch", arch)
        .put("mode", mode.name())
        .put("slot", display - 1)
        .put("lan", lan)
        .put("state", state);
  }

  public static Profile from(JSONObject j) throws JSONException {
    Profile p =
        new Profile(
            j.getString("id"),
            j.getString("release"),
            j.getString("arch"),
            Mode.valueOf(j.getString("mode")),
            j.getInt("slot"),
            j.optBoolean("lan"));
    p.state = j.optString("state", "Stopped");
    return p;
  }
}
