package io.groxaxo.linuxoid;

import android.app.Application;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.*;

public final class LinuxoidApp extends Application {
  public final List<Profile> profiles = new ArrayList<>();
  public final CopyOnWriteArrayList<Runnable> observers = new CopyOnWriteArrayList<>();
  public final StringBuilder log = new StringBuilder();
  public volatile boolean busy;
  public String progress = "Your next Linux workspace starts here.";

  @Override
  public void onCreate() {
    super.onCreate();
    try {
      JSONArray array =
          new JSONArray(getSharedPreferences("linuxoid", 0).getString("profiles", "[]"));
      for (int i = 0; i < array.length(); i++) profiles.add(Profile.from(array.getJSONObject(i)));
    } catch (JSONException | IllegalArgumentException e) {
      append("Could not read saved profiles: " + e.getMessage());
    }
  }

  public synchronized void save() {
    JSONArray a = new JSONArray();
    try {
      for (Profile p : profiles) a.put(p.json());
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    getSharedPreferences("linuxoid", 0).edit().putString("profiles", a.toString()).commit();
    notifyChanged();
  }

  public synchronized void append(String text) {
    log.append(text).append('\n');
    if (log.length() > 60000) log.delete(0, log.length() - 50000);
    progress = text;
    notifyChanged();
  }

  public synchronized String logText() {
    return log.toString();
  }

  public void notifyChanged() {
    for (Runnable observer : observers) observer.run();
  }

  public Profile find(String id) {
    for (Profile p : profiles) if (p.id.equals(id)) return p;
    throw new IllegalArgumentException("Profile not found");
  }
}
