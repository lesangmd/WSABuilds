package vn.nah.iso15189suite;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URI;
import java.net.URISyntaxException;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int STORAGE_PERMISSION_REQUEST = 1002;
    private static final String APP_HOST = "sachyhoc.com";

    private FrameLayout webContainer;
    private LinearLayout offlinePanel;
    private TextView offlineDetail;
    private Button browserButton;
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private String pendingDownloadUrl;
    private String pendingDownloadUserAgent;
    private String pendingDownloadContentDisposition;
    private String pendingDownloadMimeType;
    private String pendingUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyImmersiveMode();

        webContainer = findViewById(R.id.web_container);
        offlinePanel = findViewById(R.id.offline_panel);
        offlineDetail = findViewById(R.id.offline_detail);
        browserButton = findViewById(R.id.browser_button);
        Button retryButton = findViewById(R.id.retry_button);

        retryButton.setOnClickListener(v -> startWebView(pendingUrl != null ? pendingUrl : getString(R.string.app_url)));
        browserButton.setOnClickListener(v -> openExternal(pendingUrl != null ? pendingUrl : getString(R.string.app_url)));

        Uri data = getIntent() != null ? getIntent().getData() : null;
        pendingUrl = data != null && isInternalUrl(data.toString()) ? data.toString() : getString(R.string.app_url);
        startWebView(pendingUrl);
    }

    private void startWebView(String url) {
        pendingUrl = url;
        destroyWebView();

        PackageInfo provider = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                provider = WebView.getCurrentWebViewPackage();
                if (provider == null) {
                    showFallback("Thiết bị chưa có Android System WebView khả dụng.");
                    return;
                }
            }
            webView = new WebView(this);
            webView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            webContainer.addView(webView);
            configureWebView(webView);
            showWebView();
            webView.loadUrl(url);
        } catch (Throwable t) {
            destroyWebView();
            String detail = "Không thể khởi tạo thành phần WebView.";
            if (provider != null && provider.versionName != null) {
                detail += " Phiên bản WebView: " + provider.versionName + ".";
            }
            showFallback(detail);
        }
    }

    private void configureWebView(WebView view) {
        WebView.setWebContentsDebuggingEnabled(false);
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { s.setSafeBrowsingEnabled(true); } catch (Throwable ignored) {}
        }

        String ua = s.getUserAgentString();
        s.setUserAgentString(ua + " NAHISOAndroid/1.0.3");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(view, true);

        view.setBackgroundColor(Color.rgb(243, 251, 247));
        view.setWebViewClient(new NahWebViewClient());
        view.setWebChromeClient(new NahWebChromeClient());
        view.setDownloadListener(new NahDownloadListener());
    }

    private void showWebView() {
        offlinePanel.setVisibility(View.GONE);
        webContainer.setVisibility(View.VISIBLE);
    }

    private void showFallback(String detail) {
        webContainer.setVisibility(View.GONE);
        offlinePanel.setVisibility(View.VISIBLE);
        offlineDetail.setText(detail + " Có thể mở hệ thống bằng trình duyệt trên thiết bị.");
        browserButton.setVisibility(View.VISIBLE);
    }

    private void showOffline() {
        webContainer.setVisibility(View.GONE);
        offlinePanel.setVisibility(View.VISIBLE);
        offlineDetail.setText(R.string.offline_message);
        browserButton.setVisibility(View.VISIBLE);
    }

    private boolean isInternalUrl(String url) {
        try {
            URI u = new URI(url);
            String host = u.getHost();
            return "https".equalsIgnoreCase(u.getScheme()) && host != null &&
                    (APP_HOST.equalsIgnoreCase(host) || ("www." + APP_HOST).equalsIgnoreCase(host));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Không tìm thấy trình duyệt để mở hệ thống.", Toast.LENGTH_SHORT).show();
        }
    }

    private class NahWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (isInternalUrl(url)) return false;
            openExternal(url);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            pendingUrl = url;
            showWebView();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            CookieManager.getInstance().flush();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) showOffline();
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse response) {
            if (request.isForMainFrame() && response.getStatusCode() >= 500) showOffline();
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            showFallback("Kết nối bảo mật SSL không hợp lệ nên ứng dụng đã dừng tải trang.");
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            destroyWebView();
            showFallback("Thành phần hiển thị web trên thiết bị vừa dừng hoạt động.");
            return true;
        }
    }

    private class NahWebChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (filePathCallback != null) filePathCallback.onReceiveValue(null);
            filePathCallback = callback;

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                    params != null && params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);

            if (params != null) {
                String[] accept = params.getAcceptTypes();
                if (accept != null && accept.length > 0) {
                    java.util.ArrayList<String> cleaned = new java.util.ArrayList<>();
                    for (String a : accept) if (a != null && !a.trim().isEmpty()) cleaned.add(a.trim());
                    if (cleaned.size() == 1) intent.setType(cleaned.get(0));
                    else if (cleaned.size() > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, cleaned.toArray(new String[0]));
                }
            }

            try {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            } catch (ActivityNotFoundException e) {
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
                return false;
            }
        }
    }

    private class NahDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String disposition, String mimetype, long contentLength) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingDownloadUrl = url;
                pendingDownloadUserAgent = userAgent;
                pendingDownloadContentDisposition = disposition;
                pendingDownloadMimeType = mimetype;
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
                return;
            }
            enqueueDownload(url, userAgent, disposition, mimetype);
        }
    }

    private void enqueueDownload(String url, String userAgent, String disposition, String mimetype) {
        try {
            String filename = URLUtil.guessFileName(url, disposition, mimetype);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle(filename);
            req.setDescription("NAH ISO 15189 SUITE");
            if (mimetype != null) req.setMimeType(mimetype);
            req.addRequestHeader("User-Agent", userAgent == null ? webView.getSettings().getUserAgentString() : userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && !cookie.isEmpty()) req.addRequestHeader("Cookie", cookie);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            ((DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(req);
            Toast.makeText(this, "Đang tải tệp xuống thư mục Download.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Không thể tải tệp. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || filePathCallback == null) return;
        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                results = new Uri[count];
                for (int i=0; i<count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && pendingDownloadUrl != null) {
                enqueueDownload(pendingDownloadUrl, pendingDownloadUserAgent, pendingDownloadContentDisposition, pendingDownloadMimeType);
            }
            pendingDownloadUrl = null;
            pendingDownloadUserAgent = null;
            pendingDownloadContentDisposition = null;
            pendingDownloadMimeType = null;
        }
    }

    private void applyImmersiveMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(false);
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveMode();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyWebView();
        super.onDestroy();
    }

    private void destroyWebView() {
        if (webView != null) {
            try {
                webContainer.removeView(webView);
                webView.stopLoading();
                webView.setWebChromeClient(null);
                webView.setWebViewClient(null);
                webView.destroy();
            } catch (Throwable ignored) {}
            webView = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webContainer.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.exit_title)
                .setMessage(R.string.exit_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.exit, (d, which) -> finish())
                .show();
    }
}
