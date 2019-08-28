package de.badener.golem;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

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

    private boolean isFullScreen;
    private String externalURL;
    private String snackbarText;
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

        // Swipe to refresh layout
        swipe.setProgressBackgroundColorSchemeResource(R.color.colorDarkGrey);
        swipe.setColorSchemeResources(R.color.colorAccent, android.R.color.white);
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

        // Hide/show fab on scrolling down/up
        webView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View view, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                // Calculate the delta of the scrolling position
                int deltaScrollY = scrollY - oldScrollY;
                if (deltaScrollY > 10) fabShare.hide();
                if (deltaScrollY < -10 && progressBar.getVisibility() == View.GONE) fabShare.show();
            }
        });

        // Load the URL
        webView.loadUrl(GOLEM_URL);

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
            }

            // Update the progress bar, FAB and swipe to refresh layout accordingly
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                    fabShare.show();
                    swipe.setRefreshing(false);
                } else {
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
            snackbarText = getString(R.string.cannot_load_url);
            showSnackbar();
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

    // Show snackbar with custom background and text color
    private void showSnackbar() {
        Snackbar snackbar = Snackbar.make(coordinatorLayout, snackbarText, Snackbar.LENGTH_SHORT);
        View view = snackbar.getView();
        view.getBackground().setColorFilter(getColor(R.color.colorPrimary), PorterDuff.Mode.SRC_ATOP);
        TextView textView = view.findViewById(R.id.snackbar_text);
        textView.setTextColor(getColor(android.R.color.white));
        snackbar.show();
    }

    // Prevent the back button from closing the app
    @Override
    public void onBackPressed() {
        boolean isStartPage = webView.getUrl().equals(GOLEM_URL) || webView.getUrl().equals(GOLEM_URL + "#top");
        if (!webView.canGoBack() && !isStartPage) {
            webView.loadUrl(GOLEM_URL);
        } else if (webView.canGoBack() && !isStartPage) {
            webView.goBack();
        } else if (timeBackPressed + 2000 > System.currentTimeMillis()) {
            super.onBackPressed();
        } else {
            snackbarText = getString(R.string.tap_back_again);
            showSnackbar();
            timeBackPressed = System.currentTimeMillis();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        // Load last URL on resume
        SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        String lastUrl = sharedPreferences.getString("last_url", GOLEM_URL);
        if (!lastUrl.equals(webView.getUrl())) {
            webView.loadUrl(lastUrl);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        // Save last URL on pause
        SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("last_url", webView.getUrl());
        editor.apply();
    }
}
