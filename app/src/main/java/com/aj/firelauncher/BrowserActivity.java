package com.aj.firelauncher;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
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
            "amazon-adsystem.com",
            "aan.amazon.com",
            "assoc-amazon.com",
            "fls-na.amazon.com",
            "fls-eu.amazon.com",
            "unagi.amazon.com",
            "unagi-na.amazon.com",
            "moatads.com",
            "2mdn.net",
            "rubiconproject.com",
            "pubmatic.com",
            "casalemedia.com",
            "criteo.com",
            "criteo.net",
            "adsrvr.org",
            "demdex.net",
            "omtrdc.net",
            "serving-sys.com",
            "adnxs.com",
            "taboola.com",
            "outbrain.com",
            "popads.net",
            "popcash.net",
            "propellerads.com",
            "trafficjunky.net",
            "scorecardresearch.com",
            "quantserve.com",
            "zedo.com",
            "exoclick.com",
            "magsrv.com",
            "realsrv.com",
            "adsterra.com",
            "onclickads.net",
            "hilltopads.net",
            "juicyads.com",
            "trafficstars.com",
            "acscdn.com",
            "usrpubtrk.com",
            "adtng.com"
    ));

    private WebView webView;
    private EditText address;
    private TextView shield;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean aggressive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        buildUi();
        configureWebView();
        Uri initialUri = getIntent() != null ? getIntent().getData() : null;
        webView.loadUrl(isAllowedInitialUri(initialUri) ? initialUri.toString() : HOME);
        webView.requestFocus();
    }

    private boolean isAllowedInitialUri(Uri uri) {
        if (uri == null || !"https".equals(uri.getScheme()) || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.US);
        return host.equals("fmhy.net") || host.endsWith(".fmhy.net")
                || host.equals("imdb.com") || host.endsWith(".imdb.com");
    }

    private boolean isTrustedHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.US);
        return h.equals("fmhy.net") || h.endsWith(".fmhy.net")
                || h.equals("search.brave.com")
                || h.equals("imdb.com") || h.endsWith(".imdb.com")
                || h.equals("amazon.com") || h.endsWith(".amazon.com");
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
        LinearLayout.LayoutParams shieldLp = new LinearLayout.LayoutParams(dp(136), dp(48));
        shieldLp.leftMargin = dp(10);
        bar.addView(shield, shieldLp);

        TextView clean = button("Clear Ads");
        clean.setTextSize(13);
        clean.setOnClickListener(v -> {
            stripOverlays(webView);
            Toast.makeText(this, "Cleared overlays", Toast.LENGTH_SHORT).show();
            webView.requestFocus();
        });
        LinearLayout.LayoutParams cleanLp = new LinearLayout.LayoutParams(dp(116), dp(48));
        cleanLp.leftMargin = dp(8);
        bar.addView(clean, cleanLp);

        webView = new WebView(this);
        webView.setId(View.generateViewId());
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        for (int i = 0; i < bar.getChildCount(); i++) {
            bar.getChildAt(i).setNextFocusDownId(webView.getId());
        }
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout wrapper = new FrameLayout(this);
        wrapper.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.setVisibility(View.GONE);
        wrapper.addView(fullscreenContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(wrapper);
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
        s.setDatabaseEnabled(true);
        s.setSaveFormData(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        enablePersistentCookies();
        String ua = s.getUserAgentString();
        if (ua != null) {
            ua = ua.replace("; wv)", ")").replace("Version/4.0 ", "");
            if (!ua.contains("Mobile Safari")) {
                ua = ua.replace("Safari/", "Mobile Safari/");
            }
            if (!ua.contains("OPR/")) {
                ua = ua + " OPR/114.0.0.0";
            }
            s.setUserAgentString(ua);
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                          Message resultMsg) {
                if (resultMsg == null) {
                    return false;
                }
                String currentUrl = view != null ? view.getUrl() : null;
                String currentHost = currentUrl != null ? Uri.parse(currentUrl).getHost() : null;
                boolean isDirectory = isTrustedHost(currentHost);
                if ((!aggressive || isDirectory) && isUserGesture && view != null) {
                    WebView.HitTestResult hit = view.getHitTestResult();
                    if (hit != null && hit.getExtra() != null) {
                        view.loadUrl(hit.getExtra());
                        return false;
                    }
                }
                // Create an invisible sink WebView so window.open() returns a real native
                // Window object to stream players (preventing "Remove sandbox attributes"
                // overlays) while discarding the popup ad in the background.
                WebView sink = new WebView(BrowserActivity.this);
                sink.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        if (isDirectory && isUserGesture && req.getUrl() != null) {
                            webView.loadUrl(req.getUrl().toString());
                        }
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(sink);
                resultMsg.sendToTarget();
                webView.postDelayed(sink::destroy, 600);
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                fullscreenContainer.setVisibility(View.VISIBLE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                fullscreenContainer.removeView(customView);
                fullscreenContainer.setVisibility(View.GONE);
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                customView = null;
                customViewCallback = null;
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
                Uri reqUri = request.getUrl();
                String path = reqUri.getPath();
                boolean adPath = path != null && ("/ad.html".equals(path)
                        || path.startsWith("/ads/") || path.startsWith("/aan/"));
                if (aggressive && (isBlocked(reqUri.getHost()) || adPath)) {
                    return new WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                address.setText(url);
                unsandboxIframes(view);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                unsandboxIframes(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                address.setText(url);
                flushCookies();
                unsandboxIframes(view);
                if (aggressive) {
                    stripOverlays(view);
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
                    webView.evaluateJavascript("window.__ajClick ? window.__ajClick() : ''", result -> {
                        if (result != null && (result.startsWith("\"TAP:") || result.startsWith("\"INPUT:"))) {
                            boolean isInput = result.startsWith("\"INPUT:");
                            String coords = result.replace("\"", "").substring(isInput ? 6 : 4);
                            String[] parts = coords.split(",");
                            if (parts.length == 2) {
                                try {
                                    float density = getResources().getDisplayMetrics().density;
                                    float x = Float.parseFloat(parts[0]) * density;
                                    float y = Float.parseFloat(parts[1]) * density;
                                    tapWebView(x, y);
                                    if (isInput) {
                                        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                                        if (imm != null) {
                                            imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
                                        }
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    });
                }
                return true;
            }
            return false;
        });
    }

    private void enablePersistentCookies() {
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(webView, true);
        } catch (Throwable ignored) {
        }
    }

    private void flushCookies() {
        try {
            CookieManager.getInstance().flush();
        } catch (Throwable ignored) {
        }
    }

    private void tapWebView(float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0);
        webView.dispatchTouchEvent(down);
        webView.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private void unsandboxIframes(WebView view) {
        view.evaluateJavascript(
                "(function(){" +
                "try{" +
                "if(!window.__ajPatchedSandbox){" +
                "window.__ajPatchedSandbox=true;" +
                "var origSet=Element.prototype.setAttribute;" +
                "Element.prototype.setAttribute=function(n,v){" +
                "if(this.tagName==='IFRAME'&&String(n).toLowerCase()==='sandbox')return;" +
                "return origSet.call(this,n,v);};" +
                "}" +
                "document.querySelectorAll('iframe[sandbox]').forEach(function(f){" +
                "var c=f.cloneNode(false);" +
                "c.removeAttribute('sandbox');" +
                "if(f.parentNode)f.parentNode.replaceChild(c,f);" +
                "});" +
                "var h=location.hostname.toLowerCase();" +
                "if(h==='fmhy.net'||h.endsWith('.fmhy.net')||h==='search.brave.com'||h==='imdb.com'||h.endsWith('.imdb.com')||h==='amazon.com'||h.endsWith('.amazon.com')){" +
                "document.querySelectorAll('a[target]').forEach(function(a){a.removeAttribute('target');});" +
                "}" +
                "}catch(e){}" +
                "})();", null);
    }

    private void stripOverlays(WebView view) {
        unsandboxIframes(view);
        view.evaluateJavascript(
                "(function(){" +
                "var AD_SEL='#top_ad,#top_ad_wrapper,#bottom_ad_wrapper,[id^=\"inline\"][id$=\"_slot\"],[id^=\"inline10\"],[id^=\"inline20\"],[id^=\"inline30\"],[id^=\"inline40\"],[id^=\"inline50\"],[id*=\"nas-slot\"],[id*=\"nasa_slot\"],[class*=\"nas-slot\"],[class*=\"SlotWrapper\"],[data-testid*=\"nas-slot\"],[data-testid*=\"ad-slot\"],[data-testid*=\"sponsored\"],[data-testid*=\"promoted\"],div[class*=\"sponsored\"],section[class*=\"sponsored\"],aside div[class*=\"ad-\"],iframe[src*=\"amazon-adsystem\"],iframe[src*=\"aan.amazon\"],iframe[id*=\"ad_\"],iframe[title*=\"Advertisement\"],[aria-label=\"Sponsored\"],[aria-label=\"Advertisement\"]';" +
                "if(!document.getElementById('__aj_ad_style')){" +
                "var st=document.createElement('style');st.id='__aj_ad_style';" +
                "st.textContent=AD_SEL+'{display:none !important;}';" +
                "if(document.head)document.head.appendChild(st);}" +
                "function clean(){" +
                "try{" +
                "document.querySelectorAll(AD_SEL).forEach(function(el){el.remove();});" +
                "document.querySelectorAll('span,div,p,small').forEach(function(el){" +
                "if(el.children.length>0)return;" +
                "var tx=(el.innerText||'').trim().toLowerCase();" +
                "if(tx==='sponsored'||tx==='sponsored content'||tx==='advertisement'){" +
                "var box=el.closest('[data-testid*=\"slot\"],[class*=\"Slot\"],[class*=\"nas-\"],aside,section')||el.parentElement;" +
                "if(box&&box.tagName!=='BODY'&&box.tagName!=='MAIN'&&box.getBoundingClientRect().height<520&&!box.querySelector('video')){" +
                "box.remove();}}});" +
                "document.querySelectorAll('div,section,aside,a').forEach(function(el){" +
                "if(el.querySelector('video,iframe'))return;" +
                "var t=(el.innerText||'').toLowerCase();" +
                "var s=window.getComputedStyle(el);" +
                "var pos=s.position;" +
                "var z=parseInt(s.zIndex,10)||0;" +
                "if(t.indexOf('remove sandbox attributes')!==-1||t.indexOf('not a robot')!==-1||" +
                "((pos==='fixed'||pos==='absolute')&&z>=999&&el.getBoundingClientRect().width>200)){" +
                "el.remove();" +
                "}});" +
                "}catch(e){}}" +
                "window.__ajCleanAds=clean;" +
                "clean();setTimeout(clean,1500);setTimeout(clean,3500);" +
                "})();", null);
    }

    private void injectDpadNavigation(WebView view) {
        view.evaluateJavascript(
                "(function(){" +
                "if(!document.getElementById('__aj_style')){" +
                "var st=document.createElement('style');st.id='__aj_style';" +
                "st.textContent='.__aj_focus{outline:3px solid #D8E8FF !important;outline-offset:2px !important;background-color:rgba(49,76,119,0.55) !important;border-radius:5px !important;}';" +
                "document.head.appendChild(st);}" +
                "function vis(){" +
                "return Array.prototype.slice.call(document.querySelectorAll('iframe,video,a[href],button,input,summary,[role=button],[onclick],[class*=close],[class*=play]')).filter(function(el){" +
                "var r=el.getBoundingClientRect(),c=window.getComputedStyle(el);" +
                "return r.width>16&&r.height>16&&c.visibility!=='hidden'&&c.display!=='none';});}" +
                "function select(el){" +
                "document.querySelectorAll('.__aj_focus').forEach(function(x){x.classList.remove('__aj_focus');});" +
                "window.__ajCur=el;" +
                "if(el){el.classList.add('__aj_focus');try{el.focus({preventScroll:true});}catch(e){}el.scrollIntoView({block:'center',inline:'nearest',behavior:'smooth'});}}" +
                "window.__ajStep=function(d){" +
                "if(window.__ajCleanAds)window.__ajCleanAds();" +
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
                "window.__ajClick=function(){" +
                "var el=window.__ajCur;if(!el)return '';" +
                "var r=el.getBoundingClientRect();" +
                "var cx=Math.round(r.left+r.width/2),cy=Math.round(r.top+r.height/2);" +
                "if(el.tagName==='INPUT'||el.tagName==='TEXTAREA'||el.tagName==='SELECT'||el.isContentEditable){" +
                "try{el.focus();}catch(e){}" +
                "var t=(el.type||'').toLowerCase();" +
                "if(el.tagName==='INPUT'&&(t==='submit'||t==='button'||t==='checkbox'||t==='radio')){el.click();return 'CLICK';}" +
                "return 'INPUT:'+cx+','+cy;}" +
                "if(el.tagName==='IFRAME'||el.tagName==='VIDEO'){" +
                "return 'TAP:'+cx+','+cy;}" +
                "var h=location.hostname.toLowerCase();" +
                "if((h==='fmhy.net'||h.endsWith('.fmhy.net')||h==='search.brave.com'||h==='imdb.com'||h.endsWith('.imdb.com')||h==='amazon.com'||h.endsWith('.amazon.com'))&&el.tagName==='A'){" +
                "el.removeAttribute('target');}" +
                "el.click();return 'CLICK';};" +
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
        if (customView != null) {
            if (customViewCallback != null) customViewCallback.onCustomViewHidden();
            fullscreenContainer.removeAllViews();
            fullscreenContainer.setVisibility(View.GONE);
            customView = null;
            customViewCallback = null;
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onPause() {
        flushCookies();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        flushCookies();
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
