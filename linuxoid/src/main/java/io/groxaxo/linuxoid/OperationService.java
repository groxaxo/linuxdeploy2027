package io.groxaxo.linuxoid;

import android.app.*;
import android.content.Intent;
import android.os.*;

/** Keeps a user-requested installation alive while Android backgrounds the activity. */
public final class OperationService extends Service {
  private PowerManager.WakeLock wakeLock;

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) {
      stopSelf();
      return START_NOT_STICKY;
    }
    LinuxoidApp app = (LinuxoidApp) getApplication();
    if (app.busy) return START_NOT_STICKY;
    String id = intent.getStringExtra("profile"), action = intent.getStringExtra("action");
    String password = intent.getStringExtra("password"), dns = intent.getStringExtra("dns");
    intent.removeExtra("password");
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel(
            "operations", "Workspace operations", NotificationManager.IMPORTANCE_LOW));
    PendingIntent open =
        PendingIntent.getActivity(
            this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    Notification n =
        new Notification.Builder(this, "operations")
            .setSmallIcon(io.groxaxo.linuxoid.R.drawable.ic_linuxoid)
            .setContentTitle("Linuxoid")
            .setContentText("Working on your Ubuntu workspace")
            .setContentIntent(open)
            .setOngoing(true)
            .build();
    startForeground(1, n);
    wakeLock =
        ((PowerManager) getSystemService(POWER_SERVICE))
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Linuxoid:operation");
    wakeLock.acquire(60 * 60 * 1000L);
    app.busy = true;
    new Thread(
            () -> {
              Profile p = app.find(id);
              String previous = p.state;
              try {
                p.state = "install".equals(action) ? "Installing" : "Working";
                app.save();
                Engine e = new Engine(this, app::append);
                if ("install".equals(action)) e.install(p, password, dns);
                else if ("start".equals(action)) e.start(p);
                else if ("stop".equals(action)) e.stop(p);
                else throw new IllegalArgumentException("Unknown operation");
                p.state = "stop".equals(action) ? "Stopped" : "Running";
                app.append(p.title() + " · " + p.state);
              } catch (Exception ex) {
                p.state = "install".equals(action) ? "Incomplete" : previous;
                app.append("Could not finish: " + ex.getMessage());
              } finally {
                app.busy = false;
                app.save();
                if (wakeLock.isHeld()) wakeLock.release();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
              }
            },
            "linuxoid-operation")
        .start();
    return START_NOT_STICKY;
  }

  @Override
  public void onTimeout(int startId, int fgsType) {
    stopSelf();
  }
}
