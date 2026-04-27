package com.himgang.piconnect;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Activity;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String CONNECT_URL = "https://connect.raspberrypi.com/";
    private static final int PERMISSION_REQUEST_MEDIA = 42;

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout errorPanel;
    private TextView errorTitle;
    private TextView errorDetail;
    private HorizontalScrollView keyBarContainer;
    private Button keysButton;
    private Button ctrlButton;
    private Button altButton;
    private Button shiftButton;
    private Button metaButton;

    private PermissionRequest pendingPermissionRequest;
    private boolean immersive = true;
    private boolean ctrlDown;
    private boolean altDown;
    private boolean shiftDown;
    private boolean metaDown;
    private boolean ctrlLocked;
    private boolean altLocked;
    private boolean shiftLocked;
    private boolean metaLocked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        buildLayout();
        configureWebView();
        setContentView(buildRoot());

        if (savedInstanceState == null) {
            loadHome();
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applySystemBars();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (errorPanel.getVisibility() == View.VISIBLE) {
            hideError();
            loadHome();
            return;
        }
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST_MEDIA || pendingPermissionRequest == null) {
            return;
        }

        boolean allGranted = true;
        for (int result : grantResults) {
            allGranted = allGranted && result == PackageManager.PERMISSION_GRANTED;
        }

        PermissionRequest request = pendingPermissionRequest;
        pendingPermissionRequest = null;
        if (allGranted && isTrustedOrigin(request.getOrigin())) {
            request.grant(request.getResources());
        } else {
            request.deny();
        }
    }

    private FrameLayout buildRoot() {
        FrameLayout root = new FrameLayout(this);

        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3),
                Gravity.TOP
        );
        root.addView(progressBar, progressParams);

        root.addView(errorPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        HorizontalScrollView toolbarContainer = new HorizontalScrollView(this);
        toolbarContainer.setHorizontalScrollBarEnabled(false);
        toolbarContainer.setFillViewport(false);
        toolbarContainer.setBackgroundResource(R.drawable.panel_bg);
        toolbarContainer.addView(buildToolbar());

        FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        toolbarParams.setMargins(dp(8), dp(8), dp(8), dp(8));
        root.addView(toolbarContainer, toolbarParams);

        keyBarContainer = new HorizontalScrollView(this);
        keyBarContainer.setHorizontalScrollBarEnabled(false);
        keyBarContainer.setFillViewport(false);
        keyBarContainer.setVisibility(View.VISIBLE);
        keyBarContainer.setBackgroundResource(R.drawable.panel_bg);
        keyBarContainer.addView(buildKeyBar());

        FrameLayout.LayoutParams keyParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        keyParams.setMargins(dp(8), dp(8), dp(8), dp(62));
        root.addView(keyBarContainer, keyParams);

        return root;
    }

    private void buildLayout() {
        webView = new WebView(this);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);

        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(dp(24), dp(24), dp(24), dp(24));
        errorPanel.setBackgroundResource(R.drawable.error_bg);
        errorPanel.setVisibility(View.GONE);

        errorTitle = new TextView(this);
        errorTitle.setTextColor(Color.rgb(15, 23, 42));
        errorTitle.setTextSize(22);
        errorTitle.setGravity(Gravity.CENTER);
        errorTitle.setPadding(0, 0, 0, dp(8));
        errorPanel.addView(errorTitle);

        errorDetail = new TextView(this);
        errorDetail.setTextColor(Color.rgb(71, 85, 105));
        errorDetail.setTextSize(15);
        errorDetail.setGravity(Gravity.CENTER);
        errorDetail.setPadding(0, 0, 0, dp(20));
        errorPanel.addView(errorDetail);

        Button retry = makeButton("Retry", "Reload Raspberry Pi Connect");
        retry.setOnClickListener(view -> {
            hideError();
            loadHome();
        });
        errorPanel.addView(retry, buttonParams());
        setErrorMessage("Connection unavailable", "Check your internet connection, then retry.");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        String userAgent = settings.getUserAgentString();
        settings.setUserAgentString(userAgent + " PiConnectAndroid/0.1");

        webView.setWebViewClient(new PiConnectWebViewClient());
        webView.setWebChromeClient(new PiConnectChromeClient());
        webView.setDownloadListener(downloadListener);
    }

    private LinearLayout buildToolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        Button back = makeButton("Back", "Go back");
        back.setOnClickListener(view -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });
        toolbar.addView(back, buttonParams());

        Button forward = makeButton("Next", "Go forward");
        forward.setOnClickListener(view -> {
            if (webView.canGoForward()) {
                webView.goForward();
            }
        });
        toolbar.addView(forward, buttonParams());

        Button home = makeButton("Home", "Open Raspberry Pi Connect");
        home.setOnClickListener(view -> loadHome());
        toolbar.addView(home, buttonParams());

        Button reload = makeButton("Reload", "Reload page");
        reload.setOnClickListener(view -> reloadPage());
        toolbar.addView(reload, buttonParams());

        keysButton = makeButton("Keys", "Show remote key bar");
        keysButton.setSelected(true);
        keysButton.setOnClickListener(view -> toggleKeyBar());
        toolbar.addView(keysButton, buttonParams());

        Button keyboard = makeButton("Keyboard", "Show Android keyboard");
        keyboard.setOnClickListener(view -> showKeyboard());
        toolbar.addView(keyboard, buttonParams());

        Button paste = makeButton("Paste", "Paste clipboard text into the page");
        paste.setOnClickListener(view -> pasteClipboardText());
        toolbar.addView(paste, buttonParams());

        Button fullscreen = makeButton("Full", "Toggle fullscreen");
        fullscreen.setSelected(immersive);
        fullscreen.setOnClickListener(view -> {
            immersive = !immersive;
            fullscreen.setSelected(immersive);
            applySystemBars();
        });
        toolbar.addView(fullscreen, buttonParams());

        Button browser = makeButton("Browser", "Open current page in browser");
        browser.setOnClickListener(view -> openCurrentPageExternal());
        toolbar.addView(browser, buttonParams());

        Button reset = makeButton("Reset", "Clear web session and reload");
        reset.setOnClickListener(view -> confirmSessionReset());
        toolbar.addView(reset, buttonParams());

        return toolbar;
    }

    private void toggleKeyBar() {
        boolean show = keyBarContainer.getVisibility() != View.VISIBLE;
        keyBarContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        if (keysButton != null) {
            keysButton.setSelected(show);
        }
    }

    private LinearLayout buildKeyBar() {
        LinearLayout keyBar = new LinearLayout(this);
        keyBar.setOrientation(LinearLayout.HORIZONTAL);
        keyBar.setGravity(Gravity.CENTER_VERTICAL);

        Button esc = makeButton("Esc", "Send escape");
        esc.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_ESCAPE));
        keyBar.addView(esc, buttonParams());

        Button tab = makeButton("Tab", "Send tab");
        tab.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_TAB));
        keyBar.addView(tab, buttonParams());

        Button history = makeButton("Search", "Search command history");
        history.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_R, KeyEvent.META_CTRL_ON));
        keyBar.addView(history, buttonParams());

        Button up = makeButton("Up", "Send up arrow");
        up.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_DPAD_UP));
        keyBar.addView(up, buttonParams());

        Button down = makeButton("Down", "Send down arrow");
        down.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN));
        keyBar.addView(down, buttonParams());

        Button left = makeButton("Left", "Send left arrow");
        left.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT));
        keyBar.addView(left, buttonParams());

        Button right = makeButton("Right", "Send right arrow");
        right.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT));
        keyBar.addView(right, buttonParams());

        Button enter = makeButton("Enter", "Send enter");
        enter.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_ENTER));
        keyBar.addView(enter, buttonParams());

        ctrlButton = makeModifierButton("Ctrl", "Control");
        keyBar.addView(ctrlButton, buttonParams());

        altButton = makeModifierButton("Alt", "Alt");
        keyBar.addView(altButton, buttonParams());

        shiftButton = makeModifierButton("Shift", "Shift");
        keyBar.addView(shiftButton, buttonParams());

        metaButton = makeModifierButton("Cmd", "System");
        keyBar.addView(metaButton, buttonParams());

        Button cancel = makeButton("Break", "Send control C");
        cancel.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON));
        keyBar.addView(cancel, buttonParams());

        Button eof = makeButton("EOF", "Send control D");
        eof.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_D, KeyEvent.META_CTRL_ON));
        keyBar.addView(eof, buttonParams());

        Button clear = makeButton("Clear", "Send control L");
        clear.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_L, KeyEvent.META_CTRL_ON));
        keyBar.addView(clear, buttonParams());

        Button home = makeButton("Home", "Send home");
        home.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_MOVE_HOME));
        keyBar.addView(home, buttonParams());

        Button end = makeButton("End", "Send end");
        end.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_MOVE_END));
        keyBar.addView(end, buttonParams());

        Button pageUp = makeButton("PgUp", "Send page up");
        pageUp.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_PAGE_UP));
        keyBar.addView(pageUp, buttonParams());

        Button pageDown = makeButton("PgDn", "Send page down");
        pageDown.setOnClickListener(view -> sendKey(KeyEvent.KEYCODE_PAGE_DOWN));
        keyBar.addView(pageDown, buttonParams());

        return keyBar;
    }

    private Button makeModifierButton(String label, String modifierName) {
        Button button = makeButton(label, modifierName + " modifier. Tap for next key, long press to lock.");
        button.setOnClickListener(view -> {
            setModifierOneShot(button, modifierName);
            updateModifierButtons();
        });
        button.setOnLongClickListener(view -> {
            toggleModifierLock(button, modifierName);
            updateModifierButtons();
            return true;
        });
        return button;
    }

    private Button makeButton(String label, String description) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setMinEms(0);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackgroundResource(R.drawable.button_bg);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(74),
                dp(38)
        );
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private void showKeyboard() {
        hideError();
        webView.requestFocusFromTouch();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(webView, InputMethodManager.SHOW_FORCED);
        }
    }

    private void loadHome() {
        if (!hasNetworkConnection()) {
            showError("No network connection", "Connect to Wi-Fi or mobile data, then retry.");
            return;
        }
        hideError();
        webView.loadUrl(CONNECT_URL);
    }

    private void reloadPage() {
        if (!hasNetworkConnection()) {
            showError("No network connection", "Connect to Wi-Fi or mobile data, then retry.");
            return;
        }
        hideError();
        webView.reload();
    }

    private void openCurrentPageExternal() {
        String url = webView.getUrl();
        openExternal(Uri.parse(url == null || url.isEmpty() ? CONNECT_URL : url));
    }

    private void confirmSessionReset() {
        new AlertDialog.Builder(this)
                .setTitle("Reset web session?")
                .setMessage("This clears cookies, cached data, and local site storage before reloading Raspberry Pi Connect.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (dialog, which) -> resetWebSession())
                .show();
    }

    private void resetWebSession() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(value -> {
            cookieManager.flush();
            runOnUiThread(() -> {
                webView.clearCache(true);
                webView.clearHistory();
                webView.clearFormData();
                WebStorage.getInstance().deleteAllData();
                Toast.makeText(this, "Web session reset", Toast.LENGTH_SHORT).show();
                loadHome();
            });
        });
    }

    private void pasteClipboardText() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null
                || clipboard.getPrimaryClip().getItemCount() == 0) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (text == null || text.length() == 0) {
            Toast.makeText(this, "Clipboard has no text", Toast.LENGTH_SHORT).show();
            return;
        }

        hideError();
        webView.requestFocus();
        String script = "(function(text) {"
                + "var el = document.activeElement;"
                + "if (!el) return false;"
                + "if (el.isContentEditable) {"
                + "document.execCommand('insertText', false, text);"
                + "return true;"
                + "}"
                + "var tag = (el.tagName || '').toLowerCase();"
                + "if (tag !== 'input' && tag !== 'textarea') return false;"
                + "var start = el.selectionStart == null ? el.value.length : el.selectionStart;"
                + "var end = el.selectionEnd == null ? el.value.length : el.selectionEnd;"
                + "el.value = el.value.slice(0, start) + text + el.value.slice(end);"
                + "el.selectionStart = el.selectionEnd = start + text.length;"
                + "el.dispatchEvent(new Event('input', { bubbles: true }));"
                + "el.dispatchEvent(new Event('change', { bubbles: true }));"
                + "return true;"
                + "})(" + JSONObject.quote(text.toString()) + ");";
        webView.evaluateJavascript(script, result -> {
            if (!"true".equals(result)) {
                Toast.makeText(this, "Tap a text field before pasting", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendKey(int keyCode) {
        sendKey(keyCode, 0);
    }

    private void sendKey(int keyCode, int extraMetaState) {
        hideError();
        webView.requestFocus();
        int metaState = extraMetaState;
        if (ctrlDown || ctrlLocked) {
            metaState |= KeyEvent.META_CTRL_ON;
        }
        if (altDown || altLocked) {
            metaState |= KeyEvent.META_ALT_ON;
        }
        if (shiftDown || shiftLocked) {
            metaState |= KeyEvent.META_SHIFT_ON;
        }
        if (metaDown || metaLocked) {
            metaState |= KeyEvent.META_META_ON;
        }

        long now = SystemClock.uptimeMillis();
        webView.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState));
        webView.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState));
        clearStickyKeys();
    }

    private void setModifierOneShot(Button button, String modifierName) {
        boolean enabled;
        if (button == ctrlButton) {
            ctrlDown = !ctrlDown;
            ctrlLocked = false;
            enabled = ctrlDown;
        } else if (button == altButton) {
            altDown = !altDown;
            altLocked = false;
            enabled = altDown;
        } else if (button == shiftButton) {
            shiftDown = !shiftDown;
            shiftLocked = false;
            enabled = shiftDown;
        } else if (button == metaButton) {
            metaDown = !metaDown;
            metaLocked = false;
            enabled = metaDown;
        } else {
            enabled = false;
        }
        String message = enabled ? modifierName + " for next key" : modifierName + " cancelled";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void toggleModifierLock(Button button, String modifierName) {
        if (button == ctrlButton) {
            ctrlLocked = !ctrlLocked;
            ctrlDown = false;
        } else if (button == altButton) {
            altLocked = !altLocked;
            altDown = false;
        } else if (button == shiftButton) {
            shiftLocked = !shiftLocked;
            shiftDown = false;
        } else if (button == metaButton) {
            metaLocked = !metaLocked;
            metaDown = false;
        }
        Toast.makeText(this, modifierName + (isModifierLocked(button) ? " locked" : " unlocked"),
                Toast.LENGTH_SHORT).show();
    }

    private boolean isModifierLocked(Button button) {
        if (button == ctrlButton) {
            return ctrlLocked;
        }
        if (button == altButton) {
            return altLocked;
        }
        if (button == shiftButton) {
            return shiftLocked;
        }
        return button == metaButton && metaLocked;
    }

    private void clearStickyKeys() {
        ctrlDown = false;
        altDown = false;
        shiftDown = false;
        metaDown = false;
        updateModifierButtons();
    }

    private void updateModifierButtons() {
        if (ctrlButton != null) {
            ctrlButton.setSelected(ctrlDown || ctrlLocked);
        }
        if (altButton != null) {
            altButton.setSelected(altDown || altLocked);
        }
        if (shiftButton != null) {
            shiftButton.setSelected(shiftDown || shiftLocked);
        }
        if (metaButton != null) {
            metaButton.setSelected(metaDown || metaLocked);
        }
    }

    private void showError(String title, String detail) {
        progressBar.setVisibility(View.GONE);
        setErrorMessage(title, detail);
        errorPanel.setVisibility(View.VISIBLE);
    }

    private void setErrorMessage(String title, String detail) {
        errorTitle.setText(title);
        errorDetail.setText(detail);
    }

    private void hideError() {
        errorPanel.setVisibility(View.GONE);
    }

    private void applySystemBars() {
        if (immersive) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.systemBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.systemBars());
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        }
    }

    private boolean isTrustedOrigin(Uri origin) {
        if (origin == null) {
            return false;
        }
        String host = origin.getHost();
        return host != null && (host.equals("raspberrypi.com") || host.endsWith(".raspberrypi.com"));
    }

    private boolean hasNetworkConnection() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }

        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private boolean needsRuntimeMediaPermission(String[] resources) {
        for (String resource : resources) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    private String[] permissionsForResources(String[] resources) {
        List<String> permissions = new ArrayList<>();
        for (String resource : resources) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
        }
        return permissions.toArray(new String[0]);
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
            showError("No app can open this link", "Install or enable a browser, then retry.");
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private final DownloadListener downloadListener = (url, userAgent, contentDisposition, mimetype, contentLength) ->
            openExternal(Uri.parse(url));

    private class PiConnectWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                return false;
            }
            openExternal(uri);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            hideError();
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progressBar.setVisibility(View.GONE);
            CookieManager.getInstance().flush();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                String detail = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? error.getDescription().toString()
                        : "Check your internet connection, then retry.";
                showError("Connection unavailable", detail);
            }
        }
    }

    private class PiConnectChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> {
                if (!isTrustedOrigin(request.getOrigin())) {
                    request.deny();
                    return;
                }
                if (needsRuntimeMediaPermission(request.getResources())) {
                    pendingPermissionRequest = request;
                    requestPermissions(
                            permissionsForResources(request.getResources()),
                            PERMISSION_REQUEST_MEDIA
                    );
                    return;
                }
                request.grant(request.getResources());
            });
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            WebView popup = new WebView(MainActivity.this);
            popup.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView popupView, WebResourceRequest request) {
                    webView.loadUrl(request.getUrl().toString());
                    return true;
                }

                @Override
                public void onPageStarted(WebView popupView, String url, android.graphics.Bitmap favicon) {
                    webView.loadUrl(url);
                }
            });
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(popup);
            resultMsg.sendToTarget();
            return true;
        }
    }
}
