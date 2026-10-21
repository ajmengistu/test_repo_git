package com.aj.firelauncher;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class ContentBlocker {
    private static ContentBlocker instance;
    private final CountDownLatch loaded = new CountDownLatch(1);
    private volatile boolean available;

    static synchronized ContentBlocker get(Context context) {
        if (instance == null) instance = new ContentBlocker(context.getApplicationContext());
        return instance;
    }

    private ContentBlocker(Context context) {
        new Thread(() -> {
            try {
                StringBuilder rules = new StringBuilder();
                for (String name : new String[]{"easylist.txt", "easyprivacy.txt", "adguard-base.txt", "aj.txt"}) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                            context.getAssets().open("filters/" + name), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) rules.append(line).append('\n');
                    }
                }
                available = NativeBlocker.initialize(rules.toString());
            } catch (Exception | LinkageError error) {
                available = false; // Browser remains usable; toolbar explicitly reports unavailable.
            } finally {
                loaded.countDown();
            }
        }, "AJ filter loader").start();
    }

    String status() { return loaded.getCount() > 0 ? "Loading" : available ? "On" : "Unavailable"; }

    boolean blocks(String url, String source, String type, String method, boolean await) {
        if (await) {
            try { loaded.await(8, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return available && NativeBlocker.matches(url, source, type, method);
    }

    String selectors(String url, String tokens) {
        return available && tokens.length() <= 32768 ? NativeBlocker.selectors(url, tokens) : "[]";
    }

    static String requestType(WebResourceRequest request) {
        if (request.isForMainFrame()) return "document";
        Map<String,String> headers = request.getRequestHeaders();
        String dest = header(headers, "Sec-Fetch-Dest");
        switch (dest) {
            case "script": case "image": case "font": return dest;
            case "style": return "stylesheet";
            case "audio": case "video": return "media";
            case "iframe": case "frame": return "subdocument";
        }
        String path = request.getUrl().getPath();
        path = path == null ? "" : path.toLowerCase(Locale.US);
        if (path.matches(".*\\.(js|mjs)$")) return "script";
        if (path.endsWith(".css")) return "stylesheet";
        if (path.matches(".*\\.(png|jpg|jpeg|gif|webp|svg|ico|avif)$")) return "image";
        if (path.matches(".*\\.(mp4|webm|m3u8|mpd|m4s|ts|mp3|m4a)$")) return "media";
        if (path.matches(".*\\.(woff2?|ttf|otf)$")) return "font";
        if (header(headers,"X-Requested-With").equalsIgnoreCase("XMLHttpRequest")) return "xmlhttprequest";
        String accept = header(headers,"Accept");
        if (accept.startsWith("image/")) return "image";
        if (accept.contains("text/css")) return "stylesheet";
        if (accept.contains("text/html")) return "subdocument";
        return "other"; // WebView doesn't expose the initiating frame or every resource type.
    }

    private static String header(Map<String,String> headers, String key) {
        if (headers != null) for (Map.Entry<String,String> entry : headers.entrySet())
            if (key.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        return "";
    }

    static String site(String url) {
        try {
            String host = Uri.parse(url == null ? "" : url).getHost();
            if (host == null) return "";
            host = host.toLowerCase(Locale.US);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (RuntimeException e) { return ""; }
    }
}
