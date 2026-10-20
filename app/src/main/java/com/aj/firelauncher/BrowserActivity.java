package com.aj.firelauncher;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class BrowserActivity extends Activity {
    private static final String HOME = "https://search.brave.com/";
    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",
            "adsystem.com",
            "adnxs.com",
            "taboola.com",
            "outbrain.com",
            "popads.net",
            "popcash.net",
            "propellerads.com",
            "trafficjunky.net",
            "scorecardresearch.com",
            "quantserve.com",
            "zedo.com"
    ));

    private WebView webView;
    private EditText address;
    private TextView shield;
    private boolean aggressive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        buildUi();
        configureWebView();
        Uri initialUri = getIntent() != null ? getIntent().getData() : null;
        webView.loadUrl(initialUri != null && "https".equals(initialUri.getScheme())
                && "fmhy.net".equals(initialUri.getHost()) ? initialUri.toString() : HOME);
        webView.requestFocus();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12, 17, 27));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(12), dp(18), dp(12));
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));

        TextView back = button("‹");
        back.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });
        bar.addView(back, new LinearLayout.LayoutParams(dp(58), dp(48)));

        TextView forward = button("›");
        forward.setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });
        LinearLayout.LayoutParams small = new LinearLayout.LayoutParams(dp(58), dp(48));
        small.leftMargin = dp(8);
        bar.addView(forward, small);

        TextView home = button("⌂");
        home.setOnClickListener(v -> webView.loadUrl(HOME));
        LinearLayout.LayoutParams homeLp = new LinearLayout.LayoutParams(dp(58), dp(48));
        homeLp.leftMargin = dp(8);
        bar.addView(home, homeLp);

        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.rgb(145, 157, 177));
        address.setHint("Search or enter address");
        address.setTextSize(16);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setBackgroundResource(R.drawable.browser_address);
        address.setPadding(dp(18), 0, dp(18), 0);
        address.setOnEditorActionListener((v, actionId, event) -> {
            navigate(address.getText().toString());
            return true;
        });
        LinearLayout.LayoutParams addressLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        addressLp.leftMargin = dp(12);
        bar.addView(address, addressLp);

        shield = button("Shields: ON");
        shield.setTextSize(13);
        shield.setOnClickListener(v -> {
            aggressive = !aggressive;
            shield.setText(aggressive ? "Shields: ON" : "Shields: OFF");
            Toast.makeText(this,
                    aggressive ? "Aggressive popup/ad blocking enabled" : "Aggressive blocking disabled",
                    Toast.LENGTH_SHORT).show();
            webView.reload();
        });
        LinearLayout.LayoutParams shieldLp = new LinearLayout.LayoutParams(dp(154), dp(48));
        shieldLp.leftMargin = dp(12);
        bar.addView(shield, shieldLp);

        webView = new WebView(this);
        webView.setId(View.generateViewId());
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        for (int i = 0; i < bar.getChildCount(); i++) {
            bar.getChildAt(i).setNextFocusDownId(webView.getId());
        }
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private TextView button(String label) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setTextColor(Color.WHITE);
        v.setTextSize(24);
        v.setGravity(Gravity.CENTER);
        v.setFocusable(true);
        v.setBackgroundResource(R.drawable.browser_button);
        v.setOnFocusChangeListener((view, focused) -> {
            view.setBackgroundResource(focused ? R.drawable.browser_button_focused : R.drawable.browser_button);
            view.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(90).start();
        });
        return v;
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                          android.os.Message resultMsg) {
                // Aggressive mode never permits popups/new tabs.
                return false;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (aggressive && isBlocked(uri.getHost())) {
                    toastBlocked();
                    return true;
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (aggressive && isBlocked(request.getUrl().getHost())) {
                    return new WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                address.setText(url);
                if (aggressive) {
                    view.evaluateJavascript(
                            "(function(){"+
                            "try{window.open=function(){return null;};"+
                            "document.querySelectorAll('a[target]').forEach(function(a){a.removeAttribute('target');});"+
                            "}catch(e){}"+
                            "})();", null);
                }
                injectDpadNavigation(view);
            }
        });

        webView.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    address.requestFocus();
                    address.selectAll();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int delta = (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) ? 1 : -1;
                    webView.evaluateJavascript("window.__ajStep ? window.__ajStep(" + delta + ") : 'NONE'", result -> {
                        if ("\"TOP\"".equals(result) && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            address.requestFocus();
                        }
                    });
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    webView.evaluateJavascript("window.__ajClick && window.__ajClick(" + aggressive + ")", null);
                }
                return true;
            }
            return false;
        });
    }

    private void injectDpadNavigation(WebView view) {
        view.evaluateJavascript(
                "(function(){" +
                "if(!document.getElementById('__aj_style')){" +
                "var st=document.createElement('style');st.id='__aj_style';" +
                "st.textContent='.__aj_focus{outline:3px solid #D8E8FF !important;outline-offset:2px !important;background-color:rgba(49,76,119,0.55) !important;border-radius:5px !important;}';" +
                "document.head.appendChild(st);}" +
                "function vis(){" +
                "return Array.prototype.slice.call(document.querySelectorAll('a[href],button,input,summary,[role=button]')).filter(function(el){" +
                "var r=el.getBoundingClientRect(),c=window.getComputedStyle(el);" +
                "return r.width>4&&r.height>4&&c.visibility!=='hidden'&&c.display!=='none';});}" +
                "function select(el){" +
                "document.querySelectorAll('.__aj_focus').forEach(function(x){x.classList.remove('__aj_focus');});" +
                "window.__ajCur=el;" +
                "if(el){el.classList.add('__aj_focus');try{el.focus({preventScroll:true});}catch(e){}el.scrollIntoView({block:'center',inline:'nearest',behavior:'smooth'});}}" +
                "window.__ajStep=function(d){" +
                "var list=vis();if(!list.length){window.scrollBy(0,d*240);return 'SCROLL';}" +
                "var idx=list.indexOf(window.__ajCur);" +
                "if(idx===-1){" +
                "var topY=40;" +
                "if(location.hash){var h=document.querySelector(location.hash);if(h)topY=Math.max(0,h.getBoundingClientRect().top-20);}" +
                "idx=0;for(var i=0;i<list.length;i++){if(list[i].getBoundingClientRect().top>=topY){idx=i;break;}}" +
                "select(list[idx]);return 'OK';}" +
                "var next=idx+d;" +
                "if(next<0){if(window.scrollY<=10)return 'TOP';window.scrollBy(0,-240);select(list[0]);return 'TOP_SCROLL';}" +
                "if(next>=list.length){window.scrollBy(0,240);return 'END';}" +
                "select(list[next]);return 'OK';};" +
                "window.__ajClick=function(noTarget){" +
                "var el=window.__ajCur;if(!el)return;" +
                "if(noTarget&&el.tagName==='A')el.removeAttribute('target');" +
                "el.click();};" +
                "if(!window.__ajCur&&location.hash){setTimeout(function(){window.__ajStep(1);},250);}" +
                "})();", null);
    }

    private boolean isBlocked(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.US);
        for (String blocked : BLOCKED_HOSTS) {
            if (h.equals(blocked) || h.endsWith("." + blocked)) return true;
        }
        return false;
    }

    private void navigate(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return;

        if (value.contains(" ") || (!value.contains(".") && !value.startsWith("http"))) {
            try {
                webView.loadUrl("https://search.brave.com/search?q=" +
                        URLEncoder.encode(value, "UTF-8"));
            } catch (Exception e) {
                webView.loadUrl(HOME);
            }
        } else if (value.startsWith("http://") || value.startsWith("https://")) {
            webView.loadUrl(value);
        } else {
            webView.loadUrl("https://" + value);
        }

        webView.requestFocus();
    }

    private void toastBlocked() {
        Toast.makeText(this, "Blocked ad / redirect", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
