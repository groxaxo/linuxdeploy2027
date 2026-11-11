package io.groxaxo.linuxoid;

import android.app.*;
import android.graphics.Color;
import android.os.*;
import android.util.Base64;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.nio.charset.StandardCharsets;

public final class TerminalActivity extends Activity {
  private WebView web;
  private volatile int fd = -1, pid = -1;
  private volatile boolean destroyed;
  private boolean control;
  private Profile profile;

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().setStatusBarColor(Color.rgb(13, 22, 20));
    getWindow().setNavigationBarColor(Color.rgb(13, 22, 20));
    getWindow().getDecorView().setSystemUiVisibility(0);
    profile = ((LinuxoidApp) getApplication()).find(getIntent().getStringExtra("profile"));
    LinearLayout column = new LinearLayout(this);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setBackgroundColor(Color.rgb(13, 22, 20));
    LinearLayout bar = new LinearLayout(this);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    Button back = button("‹", () -> finish());
    bar.addView(back, new LinearLayout.LayoutParams(dp(54), dp(54)));
    TextView title = new TextView(this);
    title.setText("linuxoid · ubuntu " + profile.release);
    title.setTextColor(Color.rgb(198, 245, 154));
    title.setTextSize(16);
    bar.addView(title);
    column.addView(bar, new LinearLayout.LayoutParams(-1, dp(54)));
    web = new WebView(this);
    web.setBackgroundColor(Color.rgb(13, 22, 20));
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setAllowFileAccess(false);
    web.getSettings().setAllowContentAccess(false);
    web.getSettings().setBlockNetworkLoads(true);
    web.addJavascriptInterface(new Bridge(), "Linuxoid");
    web.setWebViewClient(
        new WebViewClient() {
          @Override
          public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            return true;
          }
        });
    column.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout keys = new LinearLayout(this);
    String[] labels = {"ESC", "CTRL", "TAB", "↑", "↓", "←", "→"};
    String[] codes = {"\033", null, "\t", "\033[A", "\033[B", "\033[D", "\033[C"};
    for (int i = 0; i < labels.length; i++) {
      final int k = i;
      keys.addView(
          button(
              labels[i],
              () -> {
                if (k == 1) {
                  control = !control;
                  return;
                }
                send(codes[k]);
              }),
          new LinearLayout.LayoutParams(0, dp(48), 1));
    }
    column.addView(keys);
    setContentView(column);
    column.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets;
        });
    web.loadUrl("file:///android_asset/web/index.html");
  }

  private int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

  private Button button(String s, Runnable r) {
    Button b = new Button(this);
    b.setText(s);
    b.setTextSize(12);
    b.setTextColor(Color.rgb(198, 245, 154));
    b.setBackgroundColor(Color.rgb(24, 39, 33));
    b.setOnClickListener(v -> r.run());
    return b;
  }

  private void output(byte[] data) {
    String b64 = Base64.encodeToString(data, Base64.NO_WRAP);
    runOnUiThread(
        () -> {
          if (!destroyed) web.evaluateJavascript("window.feed('" + b64 + "')", null);
        });
  }

  private void message(String s) {
    output(("\r\n" + s + "\r\n").getBytes(StandardCharsets.UTF_8));
  }

  private synchronized void send(String s) {
    if (fd >= 0) Pty.write(fd, s.getBytes(StandardCharsets.UTF_8));
  }

  public final class Bridge {
    @JavascriptInterface
    public void ready() {
      new Thread(
              () -> {
                try {
                  int[] session =
                      Pty.open(
                          new Engine(TerminalActivity.this, ignored -> {})
                              .terminalCommand(profile));
                  if (session == null)
                    throw new IllegalStateException("Could not open a pseudoterminal");
                  fd = session[0];
                  pid = session[1];
                  Pty.resize(fd, 80, 24);

                  byte[] b;
                  while (!destroyed && (b = Pty.read(fd)) != null) output(b);
                  if (!destroyed) message("Session ended.");
                } catch (Exception e) {
                  if (!destroyed) message("Terminal: " + e.getMessage());
                }
              },
              "linuxoid-pty")
          .start();
    }

    @JavascriptInterface
    public void input(String value) {
      if (control && !value.isEmpty()) {
        char c = value.charAt(0);
        if (c >= '@' && c <= '_') value = String.valueOf((char) (c & 31));
        else if (c >= 'a' && c <= 'z') value = String.valueOf((char) (c - 96));
        control = false;
      }
      send(value);
    }

    @JavascriptInterface
    public void resize(int cols, int rows) {
      if (fd >= 0 && cols > 1 && cols < 1000 && rows > 1 && rows < 500) Pty.resize(fd, cols, rows);
    }
  }

  @Override
  public void onDestroy() {
    destroyed = true;
    if (fd >= 0) {
      Pty.close(fd, pid);
      fd = -1;
    }
    web.removeJavascriptInterface("Linuxoid");
    web.destroy();
    super.onDestroy();
  }
}
