package com.kp.upscprofessional;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private WebView webView;
    private GoogleSignInClient googleSignInClient;
    private WebViewAssetLoader assetLoader;
    private boolean signInInProgress = false;

    private static final int GOOGLE_SIGN_IN_REQUEST = 7001;
    private static final String APP_URL =
            "https://appassets.androidplatform.net/assets/index.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(11, 13, 15));
        getWindow().setNavigationBarColor(Color.rgb(11, 13, 15));

        String webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim();

        if (webClientId.isEmpty()) {
            throw new IllegalStateException(
                    "GOOGLE_WEB_CLIENT_ID is missing from the build configuration."
            );
        }

        GoogleSignInOptions options =
                new GoogleSignInOptions.Builder(
                        GoogleSignInOptions.DEFAULT_SIGN_IN
                )
                .requestEmail()
                .requestIdToken(webClientId)
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, options);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler(
                        "/assets/",
                        new WebViewAssetLoader.AssetsPathHandler(this)
                )
                .build();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(11, 13, 15));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        webView.addJavascriptInterface(
                new AndroidGoogleAuth(),
                "AndroidGoogleAuth"
        );

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request
            ) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                Uri uri = request.getUrl();

                if ("https".equalsIgnoreCase(uri.getScheme())
                        && "appassets.androidplatform.net".equalsIgnoreCase(
                                uri.getHost())) {
                    return false;
                }

                // Never load external pages inside the WebView that has
                // access to the native Google Sign-In bridge.
                if ("https".equalsIgnoreCase(uri.getScheme())
                        || "http".equalsIgnoreCase(uri.getScheme())) {
                    try {
                        startActivity(new Intent(
                                Intent.ACTION_VIEW, uri
                        ));
                    } catch (Exception ignored) {
                        // No compatible browser available.
                    }
                }

                return true;
            }
        });

        setContentView(webView);
        webView.loadUrl(APP_URL);
    }

    private class AndroidGoogleAuth {

        @JavascriptInterface
        public void signIn() {
            runOnUiThread(() -> {
                if (signInInProgress) {
                    return;
                }

                signInInProgress = true;

                try {
                    Intent intent =
                            googleSignInClient.getSignInIntent();

                    startActivityForResult(
                            intent,
                            GOOGLE_SIGN_IN_REQUEST
                    );
                } catch (Exception e) {
                    signInInProgress = false;
                    notifyJavaScriptError(
                            "Could not launch Google Sign-In. Please try again."
                    );
                }
            });
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != GOOGLE_SIGN_IN_REQUEST) {
            return;
        }

        try {
            GoogleSignInAccount account =
                    GoogleSignIn.getSignedInAccountFromIntent(data)
                            .getResult(ApiException.class);

            if (account == null || account.getIdToken() == null) {
                notifyJavaScriptError(
                        "Google did not return an ID token. Check OAuth configuration."
                );
                return;
            }

            notifyJavaScriptSuccess(account.getIdToken());

        } catch (ApiException e) {
            if (e.getStatusCode() != 12501) {
                notifyJavaScriptError(
                        "Google Sign-In failed (code "
                                + e.getStatusCode() + ")."
                );
            } else {
                notifyJavaScriptError("Google Sign-In was cancelled.");
            }
        } catch (Exception e) {
            notifyJavaScriptError(
                    "Google Sign-In could not be completed."
            );
        } finally {
            signInInProgress = false;
        }
    }

    private void notifyJavaScriptSuccess(String idToken) {
        String safeToken = JSONObject.quote(idToken);

        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(
                        "if (window.__nativeGoogleSignInSuccess) {" +
                                "window.__nativeGoogleSignInSuccess("
                                + safeToken + ");}",
                        null
                );
            }
        });
    }

    private void notifyJavaScriptError(String message) {
        String safeMessage = JSONObject.quote(message);

        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(
                        "if (window.__nativeGoogleSignInError) {" +
                                "window.__nativeGoogleSignInError("
                                + safeMessage + ");}",
                        null
                );
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidGoogleAuth");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
