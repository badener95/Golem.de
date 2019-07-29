package de.badener.golem;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayInputStream;

import im.delight.android.webview.AdvancedWebView;

public class MainActivity extends AppCompatActivity implements AdvancedWebView.Listener {

    private static final String GOLEM_URL = "https://www.golem.de/";

    private CoordinatorLayout coordinatorLayout;
    private SwipeRefreshLayout swipe;
    private AdvancedWebView webView;
    private ProgressBar progressBar;
    private FloatingActionButton fabShare;
    private FrameLayout fullScreen;
    private ConstraintLayout errorScreen;

    private boolean isFullScreen;
    private boolean hasError;
    private String snackbarText;
    private long timeBackPressed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        swipe = findViewById(R.id.swipeRefreshLayout);
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        fabShare = findViewById(R.id.fabShare);
        fullScreen = findViewById(R.id.fullScreen);
        errorScreen = findViewById(R.id.errorScreen);

        webView.setListener(this, this);

        // WebView options
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);

        // AdvancedWebView options
        webView.setThirdPartyCookiesEnabled(false);
        webView.setMixedContentAllowed(false);
        webView.setGeolocationEnabled(false);
        // Define permitted host names
        webView.addPermittedHostname("www.golem.de");
        webView.addPermittedHostname("video.golem.de");
        webView.addPermittedHostname("forum.golem.de");
        webView.addPermittedHostname("account.golem.de");
        webView.addPermittedHostname("suche.golem.de");
        webView.addPermittedHostname("redirect.golem.de");
        webView.addPermittedHostname("glm.io");

        // Swipe to refresh layout
        swipe.setProgressBackgroundColorSchemeResource(R.color.colorPrimary);
        swipe.setColorSchemeResources(R.color.colorAccent, android.R.color.white);
        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                webView.reload();
            }
        });

        // Fab with share action
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

        // Button with reload action shown in the error screen
        MaterialButton buttonReload = findViewById(R.id.buttonReload);
        buttonReload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                webView.reload();
                snackbarText = getString(R.string.reloading_page);
                showSnackbar();
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

            // Update the progress bar
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
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
        });
    }

    // Show progress bar and hide fab on page started loading
    @Override
    public void onPageStarted(String url, Bitmap favicon) {
        progressBar.setVisibility(View.VISIBLE);
        fabShare.hide();
    }

    // Hide progress bar and show fab on page finished loading;
    // also hide the error screen if it has been visible before because of an error
    @Override
    public void onPageFinished(String url) {
        hasError = false;
        progressBar.setVisibility(View.GONE);
        swipe.setRefreshing(false);
        if (!hasError) {
            swipe.setVisibility(View.VISIBLE);
            webView.setVisibility(View.VISIBLE);
            fabShare.show();
            errorScreen.setVisibility(View.GONE);
        }
    }

    // Hide ui elements and show error screen if an error occurs
    @Override
    public void onPageError(int errorCode, String description, String failingUrl) {
        hasError = true;
        progressBar.setVisibility(View.GONE);
        swipe.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        fabShare.hide();
        errorScreen.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDownloadRequested(String url, String suggestedFilename, String mimeType,
                                    long contentLength, String contentDisposition, String userAgent) {
    }

    // Handle external links
    @Override
    public void onExternalPageRequest(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
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
        } else if (webView.canGoBack() && !isStartPage && !hasError) {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        webView.onDestroy();
    }
}
