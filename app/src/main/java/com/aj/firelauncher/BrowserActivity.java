package com.aj.firelauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
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
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrowserActivity extends Activity {
    private static final String HOME = "https://search.brave.com/";
    private static final int[] ZOOMS = {75,85,90,100,110,125};
    private static final int[] FONTS = {75,85,100,110,125,150};
    private WebView webView;
    private BrowserCursor cursor;
    private FrameLayout root;
    private LinearLayout toolbar;
    private EditText address;
    private TextView shield;
    private TextView zoomButton;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private BrowserStore store;
    private ContentBlocker blocker;
    private String mobileAgent;
    private String desktopAgent;
    private volatile String pageUrl = HOME;
    private volatile boolean playerCompatibility = true;
    private final ConcurrentHashMap<String,Boolean> allowedSites = new ConcurrentHashMap<>();
    private final AtomicInteger blockedCount = new AtomicInteger();
    private int appliedZoom = 100;
    private boolean zoomPending;
    private long lastClick = -10000;
    private long lastCompatibility;
    private final List<WebView> popupSinks = new ArrayList<>();
    private AlertDialog dialog;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        hideSystemUi();
        store = new BrowserStore(this);
        blocker = ContentBlocker.get(this);
        buildUi();
        configureWebView();
        Uri initial = getIntent() == null ? null : getIntent().getData();
        String url = isAllowedInitialUri(initial) ? initial.toString() : HOME;
        if (state == null || webView.restoreState(state) == null) load(url);
        else { pageUrl = webView.getUrl() == null ? HOME : webView.getUrl(); applySite(pageUrl); }
        webView.requestFocus();
    }

    private boolean isAllowedInitialUri(Uri uri) {
        if (uri == null || !"https".equals(uri.getScheme()) || uri.getHost() == null) return false;
        String host = ContentBlocker.site(uri.toString());
        return host.equals("fmhy.net") || host.endsWith(".fmhy.net")
                || host.equals("imdb.com") || host.endsWith(".imdb.com");
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(7,7,8));
        root.setPadding(dp(24),dp(16),dp(24),dp(16));
        root.setTag("browser-root");
        webView = new WebView(this);
        webView.setId(View.generateViewId());
        webView.setTag("web");
        webView.setBackgroundColor(Color.rgb(7,7,8));
        root.addView(webView,new FrameLayout.LayoutParams(-1,-1));
        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.setVisibility(View.GONE);
        root.addView(fullscreenContainer,new FrameLayout.LayoutParams(-1,-1));
        cursor = new BrowserCursor(this);
        cursor.setTag("cursor");
        cursor.setTarget(webView);
        root.addView(cursor,new FrameLayout.LayoutParams(-1,-1));

        toolbar = new LinearLayout(this);
        toolbar.setTag("browser-toolbar");
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setPadding(dp(12),dp(10),dp(12),dp(10));
        toolbar.setBackgroundResource(R.drawable.browser_panel);
        toolbar.setElevation(dp(4));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1,dp(112),Gravity.TOP);
        root.addView(toolbar,panelLp);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(top,new LinearLayout.LayoutParams(-1,dp(40)));
        TextView brand = label("AJ / WEB",14);
        top.addView(brand,new LinearLayout.LayoutParams(dp(80),-1));
        address = new EditText(this);
        address.setTag("address");
        address.setId(View.generateViewId());
        address.setSingleLine(true);
        address.setTextColor(Color.rgb(250,250,250));
        address.setHintTextColor(Color.rgb(153,153,165));
        address.setTextSize(16);
        address.setHint("Search or enter address");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setBackgroundResource(R.drawable.browser_address);
        address.setPadding(dp(12),0,dp(12),0);
        address.setSelectAllOnFocus(true);
        address.setOnEditorActionListener((v,id,event) -> {
            if (event != null && event.getAction() != KeyEvent.ACTION_UP) return true;
            navigate(address.getText().toString()); return true;
        });
        top.addView(address,new LinearLayout.LayoutParams(0,-1,1f));
        TextView go = addButton(top,"Go", "go", () -> navigate(address.getText().toString()),dp(48));
        TextView close = addButton(top,"×", "close-toolbar", () -> showToolbar(false),dp(40));
        go.setNextFocusLeftId(address.getId()); go.setNextFocusRightId(close.getId());
        close.setNextFocusLeftId(go.getId()); close.setNextFocusRightId(close.getId());
        address.setOnKeyListener((v,key,event) -> {
            if(!keyboardVisible() && (key==KeyEvent.KEYCODE_DPAD_RIGHT || key==KeyEvent.KEYCODE_DPAD_LEFT)) {
                if(event.getAction()==KeyEvent.ACTION_DOWN) { if(key==KeyEvent.KEYCODE_DPAD_RIGHT)go.requestFocus();else close.requestFocus(); }
                return true;
            }
            return false;
        });
        LinearLayout actions = new LinearLayout(this);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1,dp(40));
        actionsLp.topMargin = dp(10);
        toolbar.addView(actions,actionsLp);
        addButton(actions,"Back","browser-back",() -> { showToolbar(false); if(customView!=null)hideVideo();else if(webView.canGoBack())webView.goBack();else finish(); },0);
        addButton(actions,"Forward","forward",() -> { showToolbar(false); if(webView.canGoForward()) webView.goForward(); },0);
        addButton(actions,"Home","home",() -> load(HOME),0);
        addButton(actions,"Refresh","refresh",() -> { showToolbar(false); webView.reload(); },0);
        addButton(actions,"Bookmarks","bookmarks",this::showBookmarks,0);
        zoomButton = addButton(actions,"Zoom 90%","zoom",this::showZoom,0);
        addButton(actions,"Settings","browser-settings",this::showSettings,0);
        shield = addButton(actions,"Block: On","blocking",this::toggleSiteBlocking,0);
        // Explicit neighbors keep focus inside the overlay; the webpage receives keys only after closing it.
        for (int i=0;i<actions.getChildCount();i++) {
            View item = actions.getChildAt(i);
            item.setNextFocusLeftId(actions.getChildAt(Math.max(0,i-1)).getId());
            item.setNextFocusRightId(actions.getChildAt(Math.min(actions.getChildCount()-1,i+1)).getId());
            item.setNextFocusDownId(item.getId());
            item.setNextFocusUpId(address.getId());
        }
        address.setNextFocusDownId(actions.getChildAt(0).getId());
        toolbar.setVisibility(View.GONE);
        setContentView(root);
    }

    private TextView label(String text,int size) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextColor(Color.rgb(250,250,250)); v.setTextSize(size);
        v.setGravity(Gravity.CENTER); v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return v;
    }
    private TextView addButton(LinearLayout row,String title,String tag,Runnable action,int width) {
        TextView v = label(title,14);
        v.setId(View.generateViewId()); v.setTag(tag);
        v.setFocusable(true); v.setFocusableInTouchMode(true);
        v.setBackgroundResource(R.drawable.browser_button);
        v.setOnFocusChangeListener((view,focused) -> view.setBackgroundResource(
                focused ? R.drawable.browser_button_focused : R.drawable.browser_button));
        v.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, -1,width == 0 ? 1f : 0f);
        lp.leftMargin = dp(6); row.addView(v,lp);
        return v;
    }

    private void showToolbar(boolean visible) {
        cursor.stop();
        toolbar.setVisibility(visible ? View.VISIBLE : View.GONE);
        cursor.setVisibility(visible ? View.GONE : View.VISIBLE);
        if (visible) {
            updateStatus(); address.setText(pageUrl); address.requestFocus(); address.selectAll();
        } else {
            hideKeyboard(); webView.requestFocus();
        }
    }
    private void updateStatus() {
        String site = ContentBlocker.site(pageUrl);
        shield.setText(allowedSites.containsKey(site) ? "Block: Off" : "Block: " + blocker.status() + " · " + blockedCount.get());
        zoomButton.setText("Zoom " + store.zoom(site) + "%");
    }
    private void toggleSiteBlocking() {
        String site = ContentBlocker.site(pageUrl);
        if (allowedSites.containsKey(site)) allowedSites.remove(site); else allowedSites.put(site,true);
        updateStatus();
        Toast.makeText(this,allowedSites.containsKey(site) ? "Blocking paused for this site in this browser session" : "Blocking enabled",Toast.LENGTH_SHORT).show();
        showToolbar(false); webView.reload();
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setSaveFormData(true); s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true); s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView,true);
        mobileAgent = s.getUserAgentString();
        Matcher version = Pattern.compile("Chrome/([0-9.]+)").matcher(mobileAgent == null ? "" : mobileAgent);
        desktopAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                + (version.find() ? version.group(1) : "84.0.4147.125") + " Safari/537.36";
        WebView.setWebContentsDebuggingEnabled((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        webView.addJavascriptInterface(new CosmeticBridge(),"AJFilters");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView view,boolean isDialog,boolean gesture,Message result) {
                if (result == null || !(result.obj instanceof WebView.WebViewTransport) || popupSinks.size() >= 2) return false;
                // Preserve the real Window sink needed by some existing players; only user-initiated,
                // non-blocked HTTP(S) destinations may replace the current page.
                WebView sink = new WebView(BrowserActivity.this);
                popupSinks.add(sink);
                sink.setWebViewClient(new WebViewClient() {
                    private boolean relay(String url) {
                        if (gesture && http(url) && !blocked(url,"document","GET",false)) load(url);
                        sink.post(() -> { popupSinks.remove(sink); sink.destroy(); });
                        return true;
                    }
                    @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r) { return relay(r.getUrl().toString()); }
                    @Override public boolean shouldOverrideUrlLoading(WebView v,String url) { return relay(url); }
                });
                ((WebView.WebViewTransport)result.obj).setWebView(sink); result.sendToTarget();
                webView.postDelayed(() -> { if(popupSinks.remove(sink)) sink.destroy(); },1200);
                return true;
            }
            @Override public void onShowCustomView(View view,CustomViewCallback callback) {
                if(customView != null) { callback.onCustomViewHidden(); return; }
                showToolbar(false); customView = view; customViewCallback = callback;
                fullscreenContainer.addView(view,new FrameLayout.LayoutParams(-1,-1));
                fullscreenContainer.setVisibility(View.VISIBLE); webView.setVisibility(View.INVISIBLE);
                view.setFocusableInTouchMode(true); view.requestFocus();
                cursor.setTarget(fullscreenContainer); hideSystemUi();
            }
            @Override public void onHideCustomView() { hideVideo(); }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                String url = request.getUrl().toString();
                if(!http(url)) return external(url,request.hasGesture());
                if(blocked(url,"document",request.getMethod(),false)) return true;
                if(request.isForMainFrame()) applySite(url);
                return false;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url) {
                if(!http(url)) return external(url,false);
                return blocked(url,"document","GET",false);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) { return intercept(request); }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) {
                pageUrl = url; blockedCount.set(0); appliedZoom = 100; zoomPending = true;
                if(!address.hasFocus()) address.setText(url);
                applySite(url);
            }
            @Override public void onLoadResource(WebView view,String url) {
                // Retain the existing stream-player compatibility workaround, with bounded work.
                long now = SystemClock.uptimeMillis();
                if(playerCompatibility && now - lastCompatibility > 1500) {
                    lastCompatibility = now; unsandboxIframes(view);
                }
            }
            @Override public void onPageFinished(WebView view,String url) {
                pageUrl = url;
                if(!address.hasFocus()) address.setText(url);
                flushCookies(); store.remember("history",url,view.getTitle());
                if(playerCompatibility) unsandboxIframes(view);
                injectCosmetics();
                if(zoomPending) {
                    zoomPending = false;
                    webView.postDelayed(() -> {
                        if(url.equals(pageUrl)) applyZoom(store.zoom(ContentBlocker.site(url)));
                    },120);
                }
                if(toolbar.getVisibility() == View.VISIBLE) updateStatus();
            }
        });
        if(Build.VERSION.SDK_INT >= 24) {
            ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
                @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) { return intercept(request); }
            });
        }
    }

    private boolean blocked(String url,String type,String method,boolean await) {
        String source = pageUrl;
        if(allowedSites.containsKey(ContentBlocker.site(source))) return false;
        if(blocker.blocks(url,source,type,method,await)) { blockedCount.incrementAndGet(); return true; }
        return false;
    }
    private WebResourceResponse intercept(WebResourceRequest request) {
        if(!blocked(request.getUrl().toString(),ContentBlocker.requestType(request),request.getMethod(),true)) return null;
        return new WebResourceResponse("text/plain","UTF-8",200,"OK",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
    }

    public final class CosmeticBridge {
        @JavascriptInterface public String selectors(String url,String tokens) {
            // Read-only API: no activity, file, navigation or settings access from web content.
            if(url == null || url.length() > 8192 || tokens == null || tokens.length() > 32768 || !http(url)
                    || !ContentBlocker.site(url).equals(ContentBlocker.site(pageUrl))
                    || allowedSites.containsKey(ContentBlocker.site(pageUrl))) return "[]";
            return blocker.selectors(url,tokens);
        }
    }

    private void injectCosmetics() {
        if(isFinishing() || isDestroyed()) return;
        if("Loading".equals(blocker.status())) { webView.postDelayed(this::injectCosmetics,400); return; }
        if(toolbar.getVisibility()==View.VISIBLE) updateStatus();
        if(allowedSites.containsKey(ContentBlocker.site(pageUrl))) return;
        webView.evaluateJavascript("(function(){try{" +
            "if(window.__ajCosmetics){window.__ajCosmetics();return;}" +
            "var classes=new Set(),ids=new Set(),rules=new Set(),timer,queue=[document];" +
            "var st=document.createElement('style');st.id='__aj_ad_style';(document.head||document.documentElement).appendChild(st);" +
            "function scan(){timer=0;var cs=[],is=[],count=0,roots=queue.splice(0,128);" +
            "roots.forEach(function(root){var nodes=[root];if(root.querySelectorAll)nodes=nodes.concat(Array.from(root.querySelectorAll('[class],[id]')).slice(0,1500));" +
            "nodes.forEach(function(el){if(++count>2000||!el.classList)return;Array.from(el.classList).forEach(function(c){if(!classes.has(c)&&cs.length<512){classes.add(c);cs.push(c);}});" +
            "if(el.id&&!ids.has(el.id)&&is.length<512){ids.add(el.id);is.push(el.id);}});});" +
            "JSON.parse(AJFilters.selectors(location.href,JSON.stringify({classes:cs,ids:is}))).forEach(function(s){if(!rules.has(s)){try{st.sheet.insertRule(s+'{display:none!important}',st.sheet.cssRules.length);rules.add(s);}catch(e){}}});}" +
            "function schedule(){if(!timer)timer=setTimeout(scan,500);}" +
            "window.__ajCosmetics=function(){queue.push(document);schedule();};" +
            "new MutationObserver(function(ms){ms.forEach(function(m){if(queue.length>=128)return;if(m.type==='attributes')queue.push(m.target);else Array.from(m.addedNodes).forEach(function(n){if(n.nodeType===1&&n!==st&&queue.length<128)queue.push(n);});});schedule();})" +
            ".observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['class','id']});scan();" +
            "}catch(e){}})();",null);
    }

    private void applySite(String url) {
        String site = ContentBlocker.site(url);
        WebSettings s = webView.getSettings();
        String agent = store.desktop(site) ? desktopAgent : mobileAgent;
        if(agent != null && !agent.equals(s.getUserAgentString())) s.setUserAgentString(agent);
        s.setTextZoom(store.font(site));
        playerCompatibility = store.playerCompatibility(site);
    }
    private void applyZoom(int percent) {
        // Relative to WebView's own fitted overview; honor the page's minimum/maximum scale.
        webView.zoomBy(percent / (float)appliedZoom); appliedZoom = percent;
        updateStatus();
    }
    private void load(String url) {
        showToolbar(false); applySite(url); webView.setInitialScale(0); webView.loadUrl(url);
    }
    private void navigate(String raw) {
        String value = raw == null ? "" : raw.trim();
        if(value.isEmpty()) return;
        if(value.contains("://") && !http(value)) { toast("Use an HTTP or HTTPS address"); return; }
        if(value.contains(" ") || (!value.contains(".") && !value.startsWith("http"))) {
            try { value = HOME + "search?q=" + URLEncoder.encode(value,"UTF-8"); }
            catch(Exception ignored) { value = HOME; }
        } else if(!http(value)) value = "https://" + value;
        if(Uri.parse(value).getHost() == null) { toast("Check the address"); return; }
        load(value);
    }
    private static boolean http(String url) {
        if(url == null) return false;
        Uri uri = Uri.parse(url);
        return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null;
    }
    private boolean external(String url,boolean gesture) {
        if(!gesture && SystemClock.uptimeMillis() - lastClick > 1500) return true;
        try {
            Intent parsed = url.startsWith("intent:") ? Intent.parseUri(url,Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW,Uri.parse(url));
            Uri data = parsed.getData();
            if(data == null || "file".equals(data.getScheme()) || "content".equals(data.getScheme()) || "javascript".equals(data.getScheme())) return true;
            Intent safe = new Intent(Intent.ACTION_VIEW,data).addCategory(Intent.CATEGORY_BROWSABLE);
            if(parsed.getPackage() != null) safe.setPackage(parsed.getPackage());
            try { startActivity(safe); }
            catch(ActivityNotFoundException | SecurityException e) {
                String fallback = parsed.getStringExtra("browser_fallback_url");
                if(http(fallback)) load(fallback); else toast("No installed app can open this link");
            }
        } catch(Exception e) { toast("Unable to open this app link"); }
        return true;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if(event.getAction() == KeyEvent.ACTION_UP) showToolbar(toolbar.getVisibility() != View.VISIBLE);
            return true;
        }
        if(toolbar.getVisibility() == View.VISIBLE || keyboardVisible()) return super.dispatchKeyEvent(event);
        if(cursor.direction(event)) return true;
        if(event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
            if(event.getAction() == KeyEvent.ACTION_UP) {
                lastClick = SystemClock.uptimeMillis(); cursor.click();
                if(customView == null) webView.evaluateJavascript(
                    "(function(){var e=document.activeElement;return !!(e&&(e.isContentEditable||e.tagName==='TEXTAREA'||(e.tagName==='INPUT'&&!/^(button|submit|checkbox|radio|range|file|color)$/i.test(e.type))));})()",
                    value -> { if("true".equals(value)) {
                        InputMethodManager ime=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                        if(ime!=null)ime.showSoftInput(webView,InputMethodManager.SHOW_IMPLICIT);
                    }});
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean keyboardVisible() {
        Rect r=new Rect(); root.getWindowVisibleDisplayFrame(r);
        return root.getRootView().getHeight()-r.bottom > dp(120);
    }
    private void hideKeyboard() {
        InputMethodManager ime=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if(ime!=null)ime.hideSoftInputFromWindow(webView.getWindowToken(),0);
    }
    private void hideVideo() {
        if(customView==null)return;
        fullscreenContainer.removeAllViews(); fullscreenContainer.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE); customView=null;
        WebChromeClient.CustomViewCallback callback=customViewCallback; customViewCallback=null;
        cursor.setTarget(webView); if(callback!=null)callback.onCustomViewHidden(); hideSystemUi();
    }

    private void showZoom() {
        String site=ContentBlocker.site(pageUrl);
        String[] labels=new String[ZOOMS.length]; int selected=2;
        for(int i=0;i<ZOOMS.length;i++){labels[i]=ZOOMS[i]+"% of fitted page";if(store.zoom(site)==ZOOMS[i])selected=i;}
        dialog=new AlertDialog.Builder(this).setTitle("Page zoom · this site")
                .setSingleChoiceItems(labels,selected,(d,which)->{store.setZoom(site,ZOOMS[which]);applyZoom(ZOOMS[which]);d.dismiss();showToolbar(false);})
                .setNegativeButton("Close",null).show();
    }
    private void showFonts() {
        String site=ContentBlocker.site(pageUrl);String[] labels=new String[FONTS.length];int selected=2;
        for(int i=0;i<FONTS.length;i++){labels[i]=FONTS[i]+"%";if(store.font(site)==FONTS[i])selected=i;}
        dialog=new AlertDialog.Builder(this).setTitle("Text size · this site")
                .setSingleChoiceItems(labels,selected,(d,which)->{store.setFont(site,FONTS[which]);webView.getSettings().setTextZoom(FONTS[which]);d.dismiss();showToolbar(false);})
                .setNegativeButton("Close",null).show();
    }
    private void showSettings() {
        String site=ContentBlocker.site(pageUrl);
        String[] labels={(store.desktop(site)?"Desktop site ✓":"Mobile site ✓"),"Text size: "+store.font(site)+"%",
                cursor.isScrollMode()?"Use mouse cursor":"Use scroll mode","History","Clear Ads",
                "Player compatibility: "+(store.playerCompatibility(site)?"On":"Off"),"Blocking & open-source notices"};
        dialog=new AlertDialog.Builder(this).setTitle("Browser settings · "+site).setItems(labels,(d,which)->{
            switch(which){
                case 0:store.setDesktop(site,!store.desktop(site));applySite(pageUrl);showToolbar(false);webView.reload();break;
                case 1:showFonts();break;
                case 2:cursor.setScrollMode(!cursor.isScrollMode());showToolbar(false);toast(cursor.isScrollMode()?"Scroll mode · arrows scroll; Menu → Settings to restore mouse":"Mouse cursor mode");break;
                case 3:showHistory();break;
                case 4:injectCosmetics();showToolbar(false);break;
                case 5:store.setPlayerCompatibility(site,!store.playerCompatibility(site));playerCompatibility=store.playerCompatibility(site);showToolbar(false);webView.reload();break;
                case 6:showNotices();break;
            }
        }).setNegativeButton("Close",null).show();
    }
    private void showBookmarks() {
        JSONArray entries=store.entries("bookmarks");
        boolean saved=false;for(int i=0;i<entries.length();i++)if(pageUrl.equals(entries.optJSONObject(i).optString("url")))saved=true;
        final boolean remove=saved;
        String[] labels=new String[entries.length()+1];labels[0]=saved?"Remove current bookmark":"Bookmark current page";
        for(int i=0;i<entries.length();i++)labels[i+1]=entries.optJSONObject(i).optString("title");
        dialog=new AlertDialog.Builder(this).setTitle("Bookmarks").setItems(labels,(d,which)->{
            if(which==0){if(remove)store.removeBookmark(pageUrl);else store.remember("bookmarks",pageUrl,webView.getTitle());toast(remove?"Bookmark removed":"Bookmark saved");}
            else load(entries.optJSONObject(which-1).optString("url"));
        }).setNegativeButton("Close",null).show();
    }
    private void showHistory() {
        JSONArray entries=store.entries("history");String[] labels=new String[entries.length()];
        for(int i=0;i<entries.length();i++)labels[i]=entries.optJSONObject(i).optString("title");
        dialog=new AlertDialog.Builder(this).setTitle(labels.length==0?"No browsing history yet":"History")
                .setItems(labels,(d,which)->load(entries.optJSONObject(which).optString("url"))).setNegativeButton("Close",null).show();
    }
    private void showNotices() {
        dialog=new AlertDialog.Builder(this).setTitle("Content blocking")
            .setMessage("""
Brave adblock-rust engine (MPL-2.0), EasyList and EasyPrivacy (CC BY-SA 3.0), AdGuard Base (GPL-3.0). Bundled list snapshots; no Acceptable Ads subscription or background downloads. Some WebView traffic, advanced filter actions and first-party ads cannot be fully filtered.

Cursor implementation informed by mrowser (MIT).

The retained Player compatibility option relaxes iframe sandbox attributes for existing streaming players. Turn it off for untrusted sites. Full licenses and source links are included in the APK assets and repository THIRD_PARTY_NOTICES.md.
""")
            .setPositiveButton("Close",null).show();
    }

    private void toast(String text) {Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    private void flushCookies(){CookieManager.getInstance().flush();}
    private void hideSystemUi(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);}
    @Override public void onBackPressed(){
        if(customView!=null)hideVideo();
        else if(toolbar.getVisibility()==View.VISIBLE)showToolbar(false);
        else if(webView.canGoBack())webView.goBack();
        else finish();
    }
    @Override protected void onSaveInstanceState(Bundle state){webView.saveState(state);super.onSaveInstanceState(state);}
    @Override protected void onResume(){super.onResume();if(webView!=null)webView.onResume();hideSystemUi();}
    @Override protected void onPause(){cursor.stop();flushCookies();webView.onPause();super.onPause();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused&&cursor!=null)cursor.stop();}
    @Override protected void onDestroy(){
        cursor.stop();if(dialog!=null)dialog.dismiss();flushCookies();
        if(Build.VERSION.SDK_INT>=24)ServiceWorkerController.getInstance().setServiceWorkerClient(null);
        for(WebView sink:popupSinks)sink.destroy();popupSinks.clear();
        webView.removeJavascriptInterface("AJFilters");webView.stopLoading();root.removeView(webView);webView.destroy();super.onDestroy();
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}

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

}
