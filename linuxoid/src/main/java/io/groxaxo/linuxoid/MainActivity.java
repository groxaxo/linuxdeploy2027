package io.groxaxo.linuxoid;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native Android interface authored for Linuxoid, independent of the legacy app. */
public final class MainActivity extends Activity {
  private static final int BG = Color.rgb(246, 247, 242),
      INK = Color.rgb(23, 42, 31),
      MUTED = Color.rgb(102, 116, 105),
      GREEN = Color.rgb(33, 77, 56),
      PALE = Color.rgb(228, 237, 222);
  private LinuxoidApp app;
  private LinearLayout content;
  private TextView status;
  private TextView logView;
  private int profileCount = -1;
  private boolean wasBusy;
  private boolean checking;
  private final Runnable observer = () -> runOnUiThread(this::update);

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    app = (LinuxoidApp) getApplication();
    render();
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 40);
  }

  @Override
  public void onResume() {
    super.onResume();
    app.observers.add(observer);
    update();
    refreshStates();
  }

  @Override
  public void onPause() {
    app.observers.remove(observer);
    super.onPause();
  }

  private int dp(float n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

  private GradientDrawable box(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private TextView text(String s, int size, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    t.setLineSpacing(dp(3), 1);
    return t;
  }

  private void space(LinearLayout parent, int h) {
    View v = new View(this);
    parent.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  private Button button(String name, boolean primary, Runnable action) {
    Button b = new Button(this);
    b.setText(name);
    b.setAllCaps(false);
    b.setTextSize(14);
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setTextColor(primary ? Color.WHITE : GREEN);
    b.setBackground(box(primary ? GREEN : PALE, 14));
    b.setMinHeight(dp(48));
    b.setPadding(dp(13), dp(4), dp(13), dp(4));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  private void weighted(LinearLayout r, View v, int margin) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
    p.setMargins(dp(margin), 0, dp(margin), 0);
    r.addView(v, p);
  }

  private void render() {
    profileCount = app.profiles.size();
    wasBusy = app.busy;
    LinearLayout outer = column();
    outer.setBackgroundColor(BG);
    outer.setOnApplyWindowInsetsListener(
        (v, i) -> {
          v.setPadding(
              i.getSystemWindowInsetLeft(),
              i.getSystemWindowInsetTop(),
              i.getSystemWindowInsetRight(),
              i.getSystemWindowInsetBottom());
          return i;
        });
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    content = column();
    content.setPadding(dp(23), dp(18), dp(23), dp(24));
    scroll.addView(content);
    outer.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    setContentView(outer);
    LinearLayout header = row();
    ImageView mark = new ImageView(this);
    mark.setImageResource(R.drawable.ic_linuxoid);
    header.addView(mark, new LinearLayout.LayoutParams(dp(38), dp(38)));
    TextView brand = text("  Linuxoid", 23, INK, true);
    header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    TextView tag = text("ROOT REQUIRED", 10, GREEN, true);
    tag.setPadding(dp(10), dp(7), dp(10), dp(7));
    tag.setBackground(box(PALE, 20));
    header.addView(tag);
    content.addView(header);
    space(content, 33);
    content.addView(text("A little phone.\nA whole Linux world.", 32, INK, true));
    space(content, 10);
    content.addView(text("Your Ubuntu workspace, built for your pocket.", 15, MUTED, false));
    space(content, 27);
    LinearLayout intro = column();
    intro.setPadding(dp(21), dp(20), dp(21), dp(20));
    intro.setBackground(box(GREEN, 22));
    intro.addView(text("UBUNTU, YOUR WAY", 10, Color.rgb(198, 245, 154), true));
    space(intro, 10);
    intro.addView(text("Start small. Make it yours.", 23, Color.WHITE, true));
    space(intro, 7);
    intro.addView(
        text(
            "A minimal base, a terminal over SSH, or a full desktop. One Android kernel"
                + " underneath.",
            14,
            Color.rgb(220, 232, 218),
            false));
    space(intro, 18);
    Button create = button("+  Create workspace", false, this::createDialog);
    create.setBackground(box(Color.rgb(198, 245, 154), 14));
    create.setEnabled(!app.busy);
    intro.addView(create, new LinearLayout.LayoutParams(-1, dp(49)));
    content.addView(intro);
    space(content, 27);
    LinearLayout title = row();
    title.addView(text("Your workspaces", 19, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
    title.addView(text(String.format(Locale.ROOT, "%02d", app.profiles.size()), 14, MUTED, true));
    content.addView(title);
    space(content, 13);
    if (app.profiles.isEmpty()) {
      LinearLayout empty = column();
      empty.setPadding(dp(20), dp(23), dp(20), dp(23));
      empty.setBackground(box(Color.WHITE, 18));
      empty.addView(text("Room for your next idea.", 17, INK, true));
      space(empty, 7);
      empty.addView(
          text(
              "Create Ubuntu once. Keep your files and pick up where you left off.",
              14,
              MUTED,
              false));
      content.addView(empty);
    }
    for (Profile p : app.profiles) profileCard(p);
    space(content, 18);
    status = text(app.progress, 12, MUTED, false);
    status.setMaxLines(3);
    content.addView(status);
    space(content, 12);
    Button logs = button("View activity log", false, this::showLog);
    content.addView(logs, new LinearLayout.LayoutParams(-1, dp(44)));
    space(content, 18);
    TextView footer = text("Native chroot · Official Ubuntu Base · Linuxoid 0.1", 11, MUTED, false);
    footer.setGravity(Gravity.CENTER);
    content.addView(footer);
    footer.setOnClickListener(
        v ->
            new AlertDialog.Builder(this)
                .setTitle("About Linuxoid")
                .setMessage(
                    "An independent Android app and chroot engine by groxaxo.\n\n"
                        + "Root and a compatible Android kernel are required. Ubuntu shares"
                        + " Android's kernel; systemd is not PID 1.\n\n"
                        + "Application source: GPLv3. Bundled BusyBox: GPLv2. Terminal rendering:"
                        + " xterm.js, MIT. Ubuntu is provided by Canonical and has its own package"
                        + " licenses.\n\n"
                        + "Linux Deploy is retained separately in this repository; its app code is"
                        + " not used by Linuxoid.")
                .setPositiveButton(
                    "Source & notices",
                    (d, w) ->
                        startActivity(
                            new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://github.com/groxaxo/linuxdeploy2027/tree/main/linuxoid"))))
                .setNegativeButton("Close", null)
                .show());
  }

  private void profileCard(Profile p) {
    LinearLayout card = column();
    card.setPadding(dp(18), dp(18), dp(18), dp(18));
    card.setBackground(box(Color.WHITE, 18));
    LinearLayout heading = row();
    TextView title = text(p.title(), 20, INK, true);
    heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    TextView state = text(p.state, 11, GREEN, true);
    state.setPadding(dp(9), dp(5), dp(9), dp(5));
    state.setBackground(box(PALE, 20));
    heading.addView(state);
    card.addView(heading);
    space(card, 7);
    card.addView(text(p.modeName() + "  ·  " + p.arch, 13, MUTED, false));
    space(card, 16);
    if (p.state.equals("Incomplete") || p.state.equals("Installing") || p.state.equals("New")) {
      card.addView(
          text(
              p.state.equals("Incomplete")
                  ? "Installation did not finish. Files were retained; check the activity log"
                      + " before creating another workspace."
                  : "Preparing your Ubuntu workspace…",
              13,
              MUTED,
              false));
    } else {
      LinearLayout actions = row();
      boolean running = p.state.equals("Running");
      Button power =
          button(
              running ? "Stop" : "Start",
              !running,
              () -> operation(p, running ? "stop" : "start", null, null));
      power.setEnabled(!app.busy);
      weighted(actions, power, 2);
      Button terminal =
          button(
              "Terminal",
              running,
              () ->
                  startActivity(
                      new Intent(this, TerminalActivity.class).putExtra("profile", p.id)));
      terminal.setEnabled(running && !app.busy);
      weighted(actions, terminal, 2);
      card.addView(actions);
      if (p.mode != Profile.Mode.MINIMAL) {
        space(card, 12);
        card.addView(
            text(
                "SSH  linuxoid@" + (p.lan ? "<phone-ip>" : "127.0.0.1") + "  ·  port " + p.sshPort,
                12,
                MUTED,
                false));
      }
      if (p.mode == Profile.Mode.DESKTOP) {
        space(card, 12);
        Button desktop = button("Open desktop  ↗", false, () -> desktop(p));
        desktop.setEnabled(running && !app.busy);
        card.addView(desktop, new LinearLayout.LayoutParams(-1, dp(46)));
      }
    }
    content.addView(card, new LinearLayout.LayoutParams(-1, -2));
    space(content, 12);
  }

  private void desktop(Profile p) {
    new AlertDialog.Builder(this)
        .setTitle("Your Ubuntu desktop")
        .setMessage(
            "Connect a VNC viewer to 127.0.0.1:"
                + (5900 + p.display)
                + " on this phone. Use the first 8 characters of your workspace password.\n\n"
                + "For another computer, tunnel this port over SSH. Desktop connections stay on"
                + " localhost.")
        .setPositiveButton(
            "Open VNC viewer",
            (d, w) -> {
              try {
                startActivity(
                    new Intent(
                        Intent.ACTION_VIEW, Uri.parse("vnc://127.0.0.1:" + (5900 + p.display))));
              } catch (ActivityNotFoundException ex) {
                Toast.makeText(
                        this, "Open a VNC viewer and enter the address above.", Toast.LENGTH_LONG)
                    .show();
              }
            })
        .setNegativeButton("Close", null)
        .show();
  }

  private void refreshStates() {
    if (app.busy || checking || app.profiles.isEmpty()) return;
    checking = true;
    java.util.List<Profile> snapshot = new java.util.ArrayList<>(app.profiles);
    new Thread(
            () -> {
              for (Profile p : snapshot) {
                if (app.busy) break;
                try {
                  p.state = new Engine(this, ignored -> {}).status(p);
                } catch (Exception e) {
                  p.state = "Unverified";
                }
              }
              app.save();
              runOnUiThread(
                  () -> {
                    checking = false;
                    if (!isFinishing()) render();
                  });
            },
            "linuxoid-status")
        .start();
  }

  private void update() {
    if (isFinishing()) return;
    if (profileCount != app.profiles.size() || wasBusy != app.busy) {
      render();
      return;
    }
    if (status != null) status.setText(app.progress);
    if (logView != null) logView.setText(app.logText());
  }

  private void showLog() {
    ScrollView sc = new ScrollView(this);
    logView = text(app.logText().isEmpty() ? "No operations yet." : app.logText(), 12, INK, false);
    logView.setTypeface(Typeface.MONOSPACE);
    logView.setTextIsSelectable(true);
    logView.setPadding(dp(18), dp(12), dp(18), dp(12));
    sc.addView(logView);
    new AlertDialog.Builder(this)
        .setTitle("Activity log")
        .setView(sc)
        .setPositiveButton("Close", (d, w) -> logView = null)
        .setOnDismissListener(d -> logView = null)
        .show();
  }

  private void operation(Profile p, String action, String password, String dns) {
    Intent i =
        new Intent(this, OperationService.class)
            .putExtra("profile", p.id)
            .putExtra("action", action);
    if (password != null) i.putExtra("password", password);
    if (dns != null) i.putExtra("dns", dns);
    startForegroundService(i);
  }

  private void createDialog() {
    LinearLayout form = column();
    form.setPadding(dp(22), dp(6), dp(22), dp(8));
    form.addView(text("Ubuntu release", 13, MUTED, true));
    Spinner releases = new Spinner(this);
    List<String> choices = new ArrayList<>(Arrays.asList("26.04", "24.04", "22.04"));
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices);
    releases.setAdapter(adapter);
    form.addView(releases, new LinearLayout.LayoutParams(-1, dp(48)));
    TextView releaseHint =
        text("The latest available point image is chosen automatically.", 12, MUTED, false);
    form.addView(releaseHint);
    space(form, 14);
    new Thread(
            () -> {
              try {
                List<String> all = UbuntuImages.releases();
                runOnUiThread(
                    () -> {
                      String selected = (String) releases.getSelectedItem();
                      choices.clear();
                      choices.addAll(all);
                      adapter.notifyDataSetChanged();
                      releases.setSelection(Math.max(0, choices.indexOf(selected)));
                      releaseHint.setText(
                          "Official Ubuntu Base archive · older releases may be unsupported.");
                    });
              } catch (Exception ignored) {
                runOnUiThread(
                    () ->
                        releaseHint.setText(
                            "Recommended releases shown. Availability is checked before install."));
              }
            },
            "linuxoid-releases")
        .start();
    form.addView(text("Choose a starting point", 13, MUTED, true));
    RadioGroup modes = new RadioGroup(this);
    int[] modeIds = {R.id.mode_minimal, R.id.mode_ssh, R.id.mode_desktop};
    String[] labels = {
      "Minimal — base + sudo, no servers",
      "Terminal + SSH — shell access from anywhere",
      "Desktop — LXDE + VNC + SSH"
    };
    for (int n = 0; n < labels.length; n++) {
      RadioButton r = new RadioButton(this);
      r.setId(modeIds[n]);
      r.setText(labels[n]);
      r.setTextSize(13);
      modes.addView(r);
    }
    modes.check(R.id.mode_minimal);
    form.addView(modes);
    space(form, 10);
    EditText password = new EditText(this);
    password.setHint("Workspace password (8+ characters)");
    password.setSingleLine();
    password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    password.setVisibility(View.GONE);
    form.addView(password);
    CheckBox lan = new CheckBox(this);
    lan.setText("Allow SSH connections from my network");
    lan.setTextSize(12);
    lan.setVisibility(View.GONE);
    form.addView(lan);
    modes.setOnCheckedChangeListener(
        (g, id) -> {
          password.setVisibility(id == R.id.mode_minimal ? View.GONE : View.VISIBLE);
          lan.setVisibility(id == R.id.mode_minimal ? View.GONE : View.VISIBLE);
        });
    EditText dns = new EditText(this);
    dns.setSingleLine();
    dns.setText("1.1.1.1");
    dns.setHint("DNS server IP");
    space(form, 8);
    form.addView(text("DNS server", 12, MUTED, true));
    form.addView(dns);
    space(form, 10);
    form.addView(
        text(
            "Uses private Linux storage on /data. Root permission and free space are required."
                + " Desktop installs need more space and a VNC viewer.",
            12,
            MUTED,
            false));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(form);
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("Create a workspace")
            .setView(scroll)
            .setPositiveButton("Install Ubuntu", null)
            .setNegativeButton("Cancel", null)
            .create();
    d.setOnShowListener(
        dialog ->
            d.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      if (app.busy) return;
                      Profile.Mode mode =
                          modes.getCheckedRadioButtonId() == R.id.mode_minimal
                              ? Profile.Mode.MINIMAL
                              : modes.getCheckedRadioButtonId() == R.id.mode_ssh
                                  ? Profile.Mode.SSH
                                  : Profile.Mode.DESKTOP;
                      String secret = password.getText().toString();
                      if (mode != Profile.Mode.MINIMAL
                          && (secret.length() < 8
                              || secret.length() > 64
                              || secret.matches("(?s).*[\\p{Cntrl}].*"))) {
                        password.setError("Use 8–64 characters without control characters");
                        return;
                      }
                      if (!dns.getText().toString().matches("[0-9a-fA-F:.]+")) {
                        dns.setError("Enter a DNS server IP address");
                        return;
                      }
                      int slot = 0;
                      for (Profile p : app.profiles) slot = Math.max(slot, p.display);
                      Profile p =
                          Profile.create(
                              (String) releases.getSelectedItem(),
                              Engine.architecture(),
                              mode,
                              slot,
                              lan.isChecked());
                      app.profiles.add(p);
                      app.save();
                      d.dismiss();
                      operation(p, "install", secret, dns.getText().toString());
                    }));
    d.show();
  }
}
