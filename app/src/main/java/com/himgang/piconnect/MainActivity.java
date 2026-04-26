package com.himgang.piconnect;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String CONNECT_URL = "https://connect.raspberrypi.com/";
    private static final int PERMISSION_REQUEST_MEDIA = 42;

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout errorPanel;
    private HorizontalScrollView keyBarContainer;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        buildLayout();
        configureWebView();
        setContentView(buildRoot());

        if (savedInstanceState == null) {
            webView.loadUrl(CONNECT_URL);
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
            webView.loadUrl(CONNECT_URL);
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
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        toolbarParams.setMargins(dp(8), dp(8), dp(8), dp(8));
        root.addView(toolbarContainer, toolbarParams);

        keyBarContainer = new HorizontalScrollView(this);
        keyBarContainer.setHorizontalScrollBarEnabled(false);
        keyBarContainer.setFillViewport(false);
        keyBarContainer.setVisibility(View.GONE);
        keyBarContainer.setBackgroundResource(R.drawable.panel_bg);
        keyBarContainer.addView(buildKeyBar());

        FrameLayout.LayoutParams keyParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
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

        TextView title = new TextView(this);
        title.setText("Connection unavailable");
        title.setTextColor(Color.rgb(15, 23, 42));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        errorPanel.addView(title);

        TextView detail = new TextView(this);
        detail.setText("Check your internet connection, then retry.");
        detail.setTextColor(Color.rgb(71, 85, 105));
        detail.setTextSize(15);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, 0, 0, dp(20));
        errorPanel.addView(detail);

        Button retry = makeButton("Retry", "Reload Raspberry Pi Connect");
        retry.setOnClickListener(view -> {
            hideError();
            webView.loadUrl(CONNECT_URL);
        });
        errorPanel.addView(retry, buttonParams());
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
        home.setOnClickListener(view -> webView.loadUrl(CONNECT_URL));
        toolbar.addView(home, buttonParams());

        Button reload = makeButton("Reload", "Reload page");
        reload.setOnClickListener(view -> webView.reload());
        toolbar.addView(reload, buttonParams());

        Button keyboard = makeButton("Keyboard", "Show Android keyboard");
        keyboard.setOnClickListener(view -> showKeyboard());
        toolbar.addView(keyboard, buttonParams());

        Button keys = makeButton("Keys", "Show remote key bar");
        keys.setOnClickListener(view -> {
            boolean show = keyBarContainer.getVisibility() != View.VISIBLE;
            keyBarContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            keys.setSelected(show);
        });
        toolbar.addView(keys, buttonParams());

        Button fullscreen = makeButton("Full", "Toggle fullscreen");
        fullscreen.setSelected(immersive);
        fullscreen.setOnClickListener(view -> {
            immersive = !immersive;
            fullscreen.setSelected(immersive);
            applySystemBars();
        });
        toolbar.addView(fullscreen, buttonParams());

        return toolbar;
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

        ctrlButton = makeButton("Ctrl", "Hold control for the next key");
        ctrlButton.setOnClickListener(view -> {
            ctrlDown = !ctrlDown;
            ctrlButton.setSelected(ctrlDown);
        });
        keyBar.addView(ctrlButton, buttonParams());

        altButton = makeButton("Alt", "Hold alt for the next key");
        altButton.setOnClickListener(view -> {
            altDown = !altDown;
            altButton.setSelected(altDown);
        });
        keyBar.addView(altButton, buttonParams());

        shiftButton = makeButton("Shift", "Hold shift for the next key");
        shiftButton.setOnClickListener(view -> {
            shiftDown = !shiftDown;
            shiftButton.setSelected(shiftDown);
        });
        keyBar.addView(shiftButton, buttonParams());

        metaButton = makeButton("Cmd", "Hold system key for the next key");
        metaButton.setOnClickListener(view -> {
            metaDown = !metaDown;
            metaButton.setSelected(metaDown);
        });
        keyBar.addView(metaButton, buttonParams());

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

        return keyBar;
    }

    private Button makeButton(String label, String description) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackgroundResource(R.drawable.button_bg);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
        );
        params.setMargins(dp(3), 0, dp(3), 0);
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

    private void sendKey(int keyCode) {
        hideError();
        webView.requestFocus();
        int metaState = 0;
        if (ctrlDown) {
            metaState |= KeyEvent.META_CTRL_ON;
        }
        if (altDown) {
            metaState |= KeyEvent.META_ALT_ON;
        }
        if (shiftDown) {
            metaState |= KeyEvent.META_SHIFT_ON;
        }
        if (metaDown) {
            metaState |= KeyEvent.META_META_ON;
        }

        long now = SystemClock.uptimeMillis();
        webView.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState));
        webView.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState));
        clearStickyKeys();
    }

    private void clearStickyKeys() {
        ctrlDown = false;
        altDown = false;
        shiftDown = false;
        metaDown = false;
        if (ctrlButton != null) {
            ctrlButton.setSelected(false);
        }
        if (altButton != null) {
            altButton.setSelected(false);
        }
        if (shiftButton != null) {
            shiftButton.setSelected(false);
        }
        if (metaButton != null) {
            metaButton.setSelected(false);
        }
    }

    private void showError() {
        progressBar.setVisibility(View.GONE);
        errorPanel.setVisibility(View.VISIBLE);
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
            showError();
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
                showError();
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
