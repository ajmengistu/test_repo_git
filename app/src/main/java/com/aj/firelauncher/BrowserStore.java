package com.aj.firelauncher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small bounded local stores; independent site zoom/font/mode settings. */
final class BrowserStore {
    private final SharedPreferences prefs;
    BrowserStore(Context context) { prefs = context.getSharedPreferences("browser_prefs",Context.MODE_PRIVATE); }
    int zoom(String site) { return prefs.getInt("zoom:" + site,90); }
    int font(String site) { return prefs.getInt("font:" + site,100); }
    boolean desktop(String site) { return prefs.getBoolean("desktop:" + site,true); }
    boolean playerCompatibility(String site) { return prefs.getBoolean("player:" + site,true); }
    void setZoom(String site,int value) { prefs.edit().putInt("zoom:"+site,value).apply(); }
    void setFont(String site,int value) { prefs.edit().putInt("font:"+site,value).apply(); }
    void setDesktop(String site,boolean value) { prefs.edit().putBoolean("desktop:"+site,value).apply(); }
    void setPlayerCompatibility(String site,boolean value) { prefs.edit().putBoolean("player:"+site,value).apply(); }
    JSONArray entries(String kind) {
        try { return new JSONArray(prefs.getString(kind,"[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
    void remember(String kind,String url,String title) {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return;
        JSONArray old = entries(kind), out = new JSONArray();
        try {
            out.put(new JSONObject().put("url",url).put("title",title == null || title.isEmpty() ? url : title));
            for (int i=0; i<old.length() && out.length()<100; i++) {
                JSONObject entry = old.optJSONObject(i);
                if (entry != null && !url.equals(entry.optString("url"))) out.put(entry);
            }
            prefs.edit().putString(kind,out.toString()).apply();
        } catch (Exception ignored) { }
    }
    void removeBookmark(String url) {
        JSONArray old = entries("bookmarks"), out = new JSONArray();
        for (int i=0;i<old.length();i++) {
            JSONObject entry=old.optJSONObject(i);
            if (entry != null && !url.equals(entry.optString("url"))) out.put(entry);
        }
        prefs.edit().putString("bookmarks",out.toString()).apply();
    }
}
