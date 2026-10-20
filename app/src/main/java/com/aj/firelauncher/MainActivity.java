package com.aj.firelauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String PREFS = "launcher_prefs";
    private static final String PREF_FAVORITES = "favorites";

    private final Handler clockHandler = new Handler();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());

    private LinearLayout content;
    private TextView clockView;
    private TextView dateView;
    private SharedPreferences prefs;
    private final Map<String, AppEntry> appsByComponent = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        refreshApps();
        startClock();
    }

    @Override
    protected void onPause() {
        super.onPause();
        clockHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        // Keep the launcher as home. Home on Fire provides its own escape route.
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            Toast.makeText(this, "Hold OK on an app to add or remove it from Favorites.", Toast.LENGTH_LONG).show();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(11, 13, 16));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(52), dp(34), dp(52), dp(30));
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(88)));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        header.addView(identity, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("Home");
        title.setTextColor(Color.rgb(245, 247, 250));
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        identity.addView(title);

        TextView helper = new TextView(this);
        helper.setText("Press OK to open · hold OK to favorite");
        helper.setTextColor(Color.rgb(167, 175, 188));
        helper.setTextSize(14);
        LinearLayout.LayoutParams helperLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        helperLp.topMargin = dp(5);
        identity.addView(helper, helperLp);

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);
        header.addView(clockBox, new LinearLayout.LayoutParams(dp(240), ViewGroup.LayoutParams.WRAP_CONTENT));

        clockView = new TextView(this);
        clockView.setTextColor(Color.WHITE);
        clockView.setTextSize(30);
        clockView.setGravity(Gravity.END);
        clockView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        clockBox.addView(clockView);

        dateView = new TextView(this);
        dateView.setTextColor(Color.rgb(167, 175, 188));
        dateView.setTextSize(14);
        dateView.setGravity(Gravity.END);
        clockBox.addView(dateView);

        setContentView(root);
    }

    private void refreshApps() {
        appsByComponent.clear();
        PackageManager pm = getPackageManager();

        Map<String, ResolveInfo> deduped = new HashMap<>();
        queryCategory(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, deduped);
        queryCategory(pm, Intent.CATEGORY_LAUNCHER, deduped);

        List<AppEntry> entries = new ArrayList<>();
        for (ResolveInfo info : deduped.values()) {
            String packageName = info.activityInfo.packageName;
            if (getPackageName().equals(packageName)) continue;

            CharSequence labelCs = info.loadLabel(pm);
            String label = labelCs == null ? packageName : labelCs.toString();
            Drawable icon;
            try {
                icon = info.loadIcon(pm);
            } catch (Exception e) {
                icon = pm.getDefaultActivityIcon();
            }

            ComponentName component = new ComponentName(packageName, info.activityInfo.name);
            AppEntry entry = new AppEntry(label, component, icon);
            entries.add(entry);
            appsByComponent.put(component.flattenToString(), entry);
        }

        Collections.sort(entries, Comparator.comparing(a -> a.label.toLowerCase(Locale.getDefault())));
        ensureInitialFavorites(entries);
        renderRows(entries);
    }

    private void queryCategory(PackageManager pm, String category, Map<String, ResolveInfo> target) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(category);
        List<ResolveInfo> results = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo info : results) {
            if (info.activityInfo == null) continue;
            String key = info.activityInfo.packageName + "/" + info.activityInfo.name;
            target.put(key, info);
        }
    }

    private void renderRows(List<AppEntry> entries) {
        while (content.getChildCount() > 1) {
            content.removeViewAt(1);
        }

        Set<String> favorites = getFavorites();
        List<AppEntry> favoriteEntries = new ArrayList<>();
        for (String component : favorites) {
            AppEntry entry = appsByComponent.get(component);
            if (entry != null) favoriteEntries.add(entry);
        }
        Collections.sort(favoriteEntries, Comparator.comparing(a -> a.label.toLowerCase(Locale.getDefault())));

        LinearLayout quick = addSection("Favorites");
        addSystemTile(quick, "Settings", "System settings", () -> openIntent(new Intent(Settings.ACTION_SETTINGS)));
        for (AppEntry entry : favoriteEntries) addAppTile(quick, entry, true);

        LinearLayout all = addSection("Apps");
        for (AppEntry entry : entries) addAppTile(all, entry, favorites.contains(entry.component.flattenToString()));

        content.post(() -> {
            if (quick.getChildCount() > 0) quick.getChildAt(0).requestFocus();
        });
    }

    private LinearLayout addSection(String titleText) {
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText(titleText);
        sectionTitle.setTextColor(Color.rgb(245, 247, 250));
        sectionTitle.setTextSize(19);
        sectionTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(17);
        titleLp.bottomMargin = dp(11);
        content.addView(sectionTitle, titleLp);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(166)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(7), dp(20), dp(7));
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return row;
    }

    private void addAppTile(LinearLayout row, AppEntry entry, boolean favorite) {
        View tile = createTile(entry.label, entry.icon, favorite ? "Favorite" : null);
        tile.setOnClickListener(v -> launch(entry));
        tile.setOnLongClickListener(v -> {
            toggleFavorite(entry.component.flattenToString());
            return true;
        });
        row.addView(tile, tileLayoutParams());
    }

    private void addSystemTile(LinearLayout row, String title, String subtitle, Runnable action) {
        Drawable icon = getDrawable(android.R.drawable.ic_menu_manage);
        View tile = createTile(title, icon, subtitle);
        tile.setOnClickListener(v -> action.run());
        row.addView(tile, tileLayoutParams());
    }

    private LinearLayout.LayoutParams tileLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(206), dp(142));
        lp.rightMargin = dp(15);
        return lp;
    }

    private View createTile(String title, Drawable icon, String badge) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(18), dp(13), dp(18), dp(13));
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setBackgroundResource(R.drawable.tile_normal);
        tile.setElevation(dp(2));

        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(icon);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(iconView, new LinearLayout.LayoutParams(dp(62), dp(62)));

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(Color.rgb(245, 247, 250));
        name.setTextSize(15);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(9);
        tile.addView(name, nameLp);

        if (badge != null) {
            TextView badgeView = new TextView(this);
            badgeView.setText(badge);
            badgeView.setTextColor(Color.rgb(167, 175, 188));
            badgeView.setTextSize(11);
            badgeView.setSingleLine(true);
            tile.addView(badgeView);
        }

        tile.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate()
                    .scaleX(hasFocus ? 1.065f : 1.0f)
                    .scaleY(hasFocus ? 1.065f : 1.0f)
                    .translationY(hasFocus ? -dp(4) : 0)
                    .setDuration(115)
                    .start();
            v.setElevation(hasFocus ? dp(18) : dp(2));
            v.setBackgroundResource(hasFocus ? R.drawable.tile_focused : R.drawable.tile_normal);

            if (hasFocus) {
                View parent = (View) v.getParent();
                if (parent != null && parent.getParent() instanceof HorizontalScrollView) {
                    HorizontalScrollView hsv = (HorizontalScrollView) parent.getParent();
                    hsv.smoothScrollTo(Math.max(0, v.getLeft() - dp(45)), 0);
                }
            }
        });

        return tile;
    }

    private void launch(AppEntry entry) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(entry.component);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        openIntent(intent);
    }

    private void openIntent(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Unable to open this item.", Toast.LENGTH_SHORT).show();
        }
    }

    private Set<String> getFavorites() {
        return new HashSet<>(prefs.getStringSet(PREF_FAVORITES, Collections.emptySet()));
    }

    private void toggleFavorite(String component) {
        Set<String> current = getFavorites();
        boolean added;
        if (current.contains(component)) {
            current.remove(component);
            added = false;
        } else {
            current.add(component);
            added = true;
        }
        prefs.edit().putStringSet(PREF_FAVORITES, current).apply();
        Toast.makeText(this, added ? "Added to Favorites" : "Removed from Favorites", Toast.LENGTH_SHORT).show();
        refreshApps();
    }

    private void ensureInitialFavorites(List<AppEntry> entries) {
        if (prefs.contains(PREF_FAVORITES)) return;

        String[] preferredPackages = {
                "com.google.android.youtube.tv",
                "com.netflix.ninja",
                "com.amazon.firebat",
                "com.amazon.avod.thirdpartyclient",
                "com.spotify.tv.android",
                "com.plexapp.android"
        };

        Set<String> initial = new HashSet<>();
        for (String pkg : preferredPackages) {
            for (AppEntry entry : entries) {
                if (entry.component.getPackageName().equals(pkg)) {
                    initial.add(entry.component.flattenToString());
                    break;
                }
            }
        }

        for (AppEntry entry : entries) {
            if (initial.size() >= 7) break;
            initial.add(entry.component.flattenToString());
        }
        prefs.edit().putStringSet(PREF_FAVORITES, initial).apply();
    }

    private void startClock() {
        clockHandler.removeCallbacksAndMessages(null);
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                Date now = new Date();
                clockView.setText(timeFormat.format(now));
                dateView.setText(dateFormat.format(now));
                clockHandler.postDelayed(this, 30_000);
            }
        };
        tick.run();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class AppEntry {
        final String label;
        final ComponentName component;
        final Drawable icon;

        AppEntry(String label, ComponentName component, Drawable icon) {
            this.label = label;
            this.component = component;
            this.icon = icon;
        }
    }
}
