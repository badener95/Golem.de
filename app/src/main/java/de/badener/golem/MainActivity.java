package de.badener.golem;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayInputStream;

public class MainActivity extends AppCompatActivity {

    private static final String GOLEM_URL = "https://www.golem.de/";

    private SwipeRefreshLayout swipe;
    private WebView webView;
    private ProgressBar progressBar;
    private CoordinatorLayout coordinatorLayout;
    private FloatingActionButton fabShare;
    private FrameLayout fullScreen;

    private boolean hasIntent;
    private boolean isLoading;
    private boolean isFullScreen;
    private String externalURL;
    private long timeBackPressed;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        swipe = findViewById(R.id.swipeRefreshLayout);
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        fabShare = findViewById(R.id.fabShare);
        fullScreen = findViewById(R.id.fullScreen);

        // WebView options
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setAppCacheEnabled(true);
        webView.getSettings().setGeolocationEnabled(false);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        webView.getSettings().setAppCachePath(getApplicationContext().getCacheDir().getAbsolutePath());
        // Enable dark mode for WebView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int isLightThemeEnabled = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (isLightThemeEnabled == Configuration.UI_MODE_NIGHT_NO) {
                webView.getSettings().setForceDark(WebSettings.FORCE_DARK_OFF);
            } else {
                webView.getSettings().setForceDark(WebSettings.FORCE_DARK_ON);
            }
        }

        // Swipe to refresh layout
        swipe.setProgressBackgroundColorSchemeResource(R.color.colorPrimary);
        swipe.setColorSchemeResources(R.color.colorAccent, R.color.colorDrawables);
        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                webView.reload();
            }
        });

        // FAB with share action
        fabShare.hide();
        coordinatorLayout.setVisibility(View.VISIBLE);
        fabShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
                startActivity(Intent.createChooser(share, getString(R.string.chooser_share)));
            }
        });

        // Hide/show FAB on scrolling down/up
        webView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View view, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                // Calculate the delta of the scrolling position
                int deltaScrollY = scrollY - oldScrollY;
                if (deltaScrollY > 10) fabShare.hide();
                if (deltaScrollY < -10 && progressBar.getVisibility() == View.GONE) fabShare.show();
            }
        });

        // Handle intents
        Intent intent = getIntent();
        Uri uri = intent.getData();
        String url;
        if (uri != null) {
            hasIntent = true;
            url = uri.toString();
        } else {
            url = GOLEM_URL;
        }
        // Load either the GOLEM_URL or the URL provided by an intent
        webView.loadUrl(url);

        webView.setWebChromeClient(new WebChromeClient() {

            // Enter fullscreen
            @Override
            public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
                isFullScreen = true;
                fabShare.hide();
                fullScreen.setVisibility(View.VISIBLE);
                fullScreen.addView(view);
                enableFullScreen();
            }

            // Exit fullscreen
            @Override
            public void onHideCustomView() {
                super.onHideCustomView();
                isFullScreen = false;
                fullScreen.setVisibility(View.GONE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                int isLightThemeEnabled = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (isLightThemeEnabled == Configuration.UI_MODE_NIGHT_NO) {
                    // Light theme is enabled, restore light status bar and nav bar
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        View decorView = getWindow().getDecorView();
                        decorView.setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                    } else {
                        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                    }
                }
            }

            // Update the progress bar, FAB and swipe to refresh layout accordingly
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    progressBar.setProgress(newProgress, true);
                } else {
                    progressBar.setProgress(newProgress);
                }
                if (newProgress == 100) {
                    isLoading = false;
                    progressBar.setVisibility(View.GONE);
                    fabShare.show();
                    swipe.setRefreshing(false);
                } else {
                    isLoading = true;
                    progressBar.setVisibility(View.VISIBLE);
                    fabShare.hide();
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            // Block all hosts except necessary ones (ad blocker)
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                // Check whether request comes from permitted host
                if (host == null || host.contains("golem.de") || host.contains("narando.com") ||
                        host.contains("bootstrapcdn.com") || host.contains("glm.io")) {
                    return super.shouldInterceptRequest(view, request);
                }
                // Host is not permitted, override request with empty response
                return new WebResourceResponse("text/plain", "utf-8",
                        new ByteArrayInputStream("".getBytes()));
            }

            // Handle external links
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String host = request.getUrl().getHost();
                externalURL = url;
                if (url.startsWith("tel") || url.startsWith("sms") ||
                        url.startsWith("mailto") || url.startsWith("whatsapp")) {
                    // Support some common intents
                    openExternalURL();
                    return true;
                } else if (host == null || host.equals("www.golem.de") ||
                        host.equals("video.golem.de") || host.equals("forum.golem.de") ||
                        host.equals("account.golem.de") || host.equals("suche.golem.de") ||
                        host.equals("it-profis.golem.de") || host.equals("pc.golem.de") ||
                        host.equals("redirect.golem.de") || host.equals("glm.io")) {
                    // Don't override and load in this app
                    return false;
                } else {
                    // Open all other URLs in the browser or external app
                    openExternalURL();
                    return true;
                }
            }
        });
    }

    // Open external URLs
    private void openExternalURL() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(externalURL));
        if (intent.resolveActivity(getPackageManager()) != null) {
            // There is at least one app that can handle this link
            startActivity(Intent.createChooser(intent, getString(R.string.chooser_open_app)));
        } else {
            // There is no app
            Snackbar.make(coordinatorLayout, R.string.cannot_load_url, Snackbar.LENGTH_SHORT)
                    .show();
        }
    }

    // Restore fullscreen after losing and gaining focus
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isFullScreen) enableFullScreen();
    }

    // Hide status bar and nav bar when entering fullscreen
    private void enableFullScreen() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    // Prevent the back button from closing the app
    @Override
    public void onBackPressed() {
        boolean isStartPage = webView.getUrl().equals(GOLEM_URL) || webView.getUrl().equals(GOLEM_URL + "#top");
        if (!webView.canGoBack() && !isStartPage && !hasIntent) {
            webView.loadUrl(GOLEM_URL);
        } else if (webView.canGoBack() && !isStartPage) {
            webView.goBack();
        } else if (timeBackPressed + 2000 > System.currentTimeMillis()) {
            super.onBackPressed();
        } else {
            Snackbar.make(coordinatorLayout, R.string.tap_back_again, Snackbar.LENGTH_SHORT)
                    .show();
            timeBackPressed = System.currentTimeMillis();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Load last visited URL on resume
        SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        String lastUrl = sharedPreferences.getString("last_url", GOLEM_URL);
        if (!lastUrl.equals(webView.getUrl()) && !isLoading && !hasIntent) {
            webView.loadUrl(lastUrl);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!hasIntent) {
            // Save last URL on pause
            SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("last_url", webView.getUrl());
            editor.apply();
        }
    }
}
