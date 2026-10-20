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
import android.net.Uri;
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
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String PREFS = "launcher_prefs";
    private static final String PREF_FAVORITE_PACKAGES = "favorite_packages_v2";
    private static final String PREF_V2_MIGRATED = "favorites_v4_migrated";

    private final Handler clockHandler = new Handler();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());

    private LinearLayout content;
    private TextView clockView;
    private TextView dateView;
    private TextView focusTitle;
    private TextView focusSubtitle;
    private SharedPreferences prefs;
    private final Map<String, AppEntry> appsByPackage = new LinkedHashMap<>();
    private final Map<String, View> focusTargets = new LinkedHashMap<>();
    private final List<List<View>> navigationRows = new ArrayList<>();
    private String focusedKey = "favorite:prime video";
    private boolean allAppsExpanded;
    private List<AppEntry> currentEntries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (savedInstanceState != null) {
            focusedKey = savedInstanceState.getString("focusedKey", focusedKey);
            allAppsExpanded = savedInstanceState.getBoolean("allAppsExpanded");
        }
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
        if (allAppsExpanded) {
            allAppsExpanded = false;
            focusedKey = "all-apps";
            renderRows(currentEntries);
        } else if (!navigationRows.isEmpty()) {
            navigationRows.get(0).get(0).requestFocus();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("focusedKey", focusedKey);
        state.putBoolean("allAppsExpanded", allAppsExpanded);
        super.onSaveInstanceState(state);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.screen_bg);

        ScrollView verticalScroll = new ScrollView(this);
        verticalScroll.setFillViewport(true);
        verticalScroll.setVerticalScrollBarEnabled(false);
        verticalScroll.setClipToPadding(false);
        root.addView(verticalScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(40), dp(22), dp(40), dp(30));
        verticalScroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        header.addView(hero, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView brand = new TextView(this);
        brand.setText("AJ Home");
        brand.setTextColor(Color.rgb(246, 248, 252));
        brand.setTextSize(16);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hero.addView(brand);

        focusTitle = new TextView(this);
        focusTitle.setText("Favorites");
        focusTitle.setTextColor(Color.WHITE);
        focusTitle.setTextSize(26);
        focusTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(3);
        hero.addView(focusTitle, titleLp);

        focusSubtitle = new TextView(this);
        focusSubtitle.setText("Your most-used apps, one click away");
        focusSubtitle.setTextColor(Color.rgb(190, 198, 211));
        focusSubtitle.setTextSize(14);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = dp(4);
        hero.addView(focusSubtitle, subtitleLp);

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
        dateView.setTextColor(Color.rgb(190, 198, 211));
        dateView.setTextSize(14);
        dateView.setGravity(Gravity.END);
        clockBox.addView(dateView);

        setContentView(root);
    }

    private void refreshApps() {
        appsByPackage.clear();
        PackageManager pm = getPackageManager();

        // Launcher first, Leanback second so TV-specific activities win.
        queryCategory(pm, Intent.CATEGORY_LAUNCHER);
        queryCategory(pm, Intent.CATEGORY_LEANBACK_LAUNCHER);

        List<AppEntry> entries = new ArrayList<>(appsByPackage.values());
        Collections.sort(entries, (a, b) ->
                a.label.toLowerCase(Locale.getDefault()).compareTo(b.label.toLowerCase(Locale.getDefault())));

        migrateFavoritesIfNeeded(entries);
        renderRows(entries);
    }

    private void queryCategory(PackageManager pm, String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(category);
        List<ResolveInfo> results = pm.queryIntentActivities(intent, 0);

        for (ResolveInfo info : results) {
            if (info.activityInfo == null) continue;

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
            appsByPackage.put(packageName, new AppEntry(label, packageName, component, icon));
        }
    }

    private void renderRows(List<AppEntry> entries) {
        currentEntries = entries;
        String restoreKey = focusedKey;
        focusTargets.clear();
        navigationRows.clear();
        while (content.getChildCount() > 1) {
            content.removeViewAt(1);
        }

        List<String> favorites = getFavoritePackages();
        addSectionTitle("Favorites", "Press OK to open · hold OK to customize");
        LinearLayout favoritesRow = addRow(144);
        for (String pkg : favorites) {
            AppEntry entry = appsByPackage.get(pkg);
            if (entry != null) {
                addFavoriteTile(favoritesRow, entry);
            } else if (pkg.startsWith("missing:")) {
                String label = pkg.substring("missing:".length());
                addActionTile(favoritesRow, label, "Not installed", android.R.drawable.ic_media_play,
                        () -> showMissingApp(label));
            }
        }
        collectRow(favoritesRow);

        addSectionTitle("Explore", "Opens in AJ Browser");
        LinearLayout webRow = addRow(104);
        addActionTile(webRow, "Streaming Sites", "FMHY · movies & TV",
                android.R.drawable.ic_menu_search,
                () -> openBrowser("https://fmhy.net/video#streaming-sites"));
        addActionTile(webRow, "Live Sports", "FMHY · live sports",
                android.R.drawable.ic_media_play,
                () -> openBrowser("https://fmhy.net/video#live-sports"));
        collectRow(webRow);

        LinearLayout toggleRow = addRow(66);
        addActionTile(toggleRow, "All Apps", allAppsExpanded ? "▲ Collapse" : "▼ Expand",
                android.R.drawable.ic_menu_sort_by_size, () -> {
                    allAppsExpanded = !allAppsExpanded;
                    focusedKey = "all-apps";
                    renderRows(currentEntries);
                });
        View toggle = toggleRow.getChildAt(0);
        registerTarget(toggle, "all-apps");
        toggle.setContentDescription("All Apps, " + (allAppsExpanded ? "expanded" : "collapsed"));
        collectRow(toggleRow);

        if (allAppsExpanded) {
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(4);
            grid.setUseDefaultMargins(false);
            content.addView(grid, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            List<View> gridRow = new ArrayList<>();
            for (AppEntry entry : entries) {
                addGridTile(grid, entry, favorites.contains(entry.packageName));
                gridRow.add(grid.getChildAt(grid.getChildCount() - 1));
                if (gridRow.size() == 4) {
                    navigationRows.add(gridRow);
                    gridRow = new ArrayList<>();
                }
            }
            if (!gridRow.isEmpty()) navigationRows.add(gridRow);
        }

        addSectionTitle("Settings & Tools", "");
        LinearLayout toolsRow = addRow(104);
        addSystemTile(toolsRow, "Settings", "Fire TV",
                () -> openIntent(new Intent(Settings.ACTION_SETTINGS)), false);
        addActionTile(
                toolsRow,
                "AJ Browser",
                "Popup blocking enabled",
                android.R.drawable.ic_menu_search,
                () -> openIntent(new Intent(this, BrowserActivity.class)));

        AppEntry nord = findAppByLabel("nordvpn");
        if (nord != null) {
            addToolAppTile(toolsRow, nord, "VPN");
        } else {
            addActionTile(
                    toolsRow,
                    "NordVPN",
                    "Install from Amazon Appstore",
                    android.R.drawable.ic_lock_lock,
                    () -> Toast.makeText(this, "Install NordVPN from the Amazon Appstore, then it will appear here.", Toast.LENGTH_LONG).show());
        }

        AppEntry homeOnFire = findAppByLabel("home on fire");
        if (homeOnFire != null) {
            addToolAppTile(toolsRow, homeOnFire, "Home button");
        }

        collectRow(toolsRow);
        wireNavigation();
        content.post(() -> {
            View target = focusTargets.get(restoreKey);
            if (target == null && restoreKey.startsWith("grid:")) target = focusTargets.get("all-apps");
            if (target == null) target = navigationRows.get(0).get(0);
            target.requestFocus();
        });
    }

    private LinearLayout addRow(int height) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setTag(height);
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(height)));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(8), dp(12), dp(8));
        row.setClipChildren(false);
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return row;
    }

    private void collectRow(ViewGroup row) {
        List<View> tiles = new ArrayList<>();
        for (int index = 0; index < row.getChildCount(); index++) tiles.add(row.getChildAt(index));
        if (!tiles.isEmpty()) navigationRows.add(tiles);
    }

    private void registerTarget(View tile, String key) {
        tile.setId(View.generateViewId());
        tile.setTag(key);
        focusTargets.put(key, tile);
    }

    private void wireNavigation() {
        for (int rowIndex = 0; rowIndex < navigationRows.size(); rowIndex++) {
            List<View> row = navigationRows.get(rowIndex);
            List<View> above = navigationRows.get(Math.max(0, rowIndex - 1));
            List<View> below = navigationRows.get(Math.min(navigationRows.size() - 1, rowIndex + 1));
            for (int column = 0; column < row.size(); column++) {
                View tile = row.get(column);
                tile.setNextFocusLeftId(row.get(Math.max(0, column - 1)).getId());
                tile.setNextFocusRightId(row.get(Math.min(row.size() - 1, column + 1)).getId());
                tile.setNextFocusUpId(above.get(Math.min(column, above.size() - 1)).getId());
                tile.setNextFocusDownId(below.get(Math.min(column, below.size() - 1)).getId());
            }
        }
    }

    private void openBrowser(String url) {
        openIntent(new Intent(this, BrowserActivity.class).setData(Uri.parse(url)));
    }

    private void showMissingApp(String label) {
        Toast.makeText(this, "Install " + label + " from the Amazon Appstore.", Toast.LENGTH_LONG).show();
    }

    private void addSectionTitle(String titleText, String helperText) {
        LinearLayout sectionHeader = new LinearLayout(this);
        sectionHeader.setOrientation(LinearLayout.HORIZONTAL);
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.topMargin = dp(14);
        headerLp.bottomMargin = dp(7);
        content.addView(sectionHeader, headerLp);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.rgb(246, 248, 252));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sectionHeader.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView helper = new TextView(this);
        helper.setText(helperText);
        helper.setTextColor(Color.rgb(156, 168, 186));
        helper.setTextSize(12);
        LinearLayout.LayoutParams helperLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        helperLp.leftMargin = dp(14);
        sectionHeader.addView(helper, helperLp);
    }

    private void addFavoriteTile(LinearLayout row, AppEntry entry) {
        View tile = createTile(entry.label, entry.icon, "Favorite", true);
        registerTarget(tile, "favorite:" + entry.packageName);
        bindAppTile(tile, entry);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileWidth(3), dp(124));
        lp.rightMargin = dp(12);
        row.addView(tile, lp);
    }

    private void addGridTile(GridLayout grid, AppEntry entry, boolean favorite) {
        View tile = createTile(entry.label, entry.icon, favorite ? "Favorite" : null, false);
        registerTarget(tile, "grid:" + entry.packageName);
        bindAppTile(tile, entry);

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = tileWidth(4);
        lp.height = dp(100);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        grid.addView(tile, lp);
    }

    private void bindAppTile(View tile, AppEntry entry) {
        tile.setOnClickListener(v -> launch(entry));
        tile.setOnLongClickListener(v -> {
            toggleFavorite(entry.packageName);
            return true;
        });
    }

    private void addSystemTile(LinearLayout row, String title, String subtitle, Runnable action, boolean large) {
        addActionTile(row, title, subtitle, android.R.drawable.ic_menu_manage, action);
    }

    private void addActionTile(LinearLayout row, String title, String subtitle, int iconRes, Runnable action) {
        Drawable icon = getDrawable(iconRes);
        View tile = createTile(title, icon, subtitle, false);
        registerTarget(tile, "action:" + title);
        tile.setOnClickListener(v -> action.run());
        int rowHeight = (Integer) ((View) row.getParent()).getTag();
        if (rowHeight == 66) tile.findViewWithTag("icon").setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                tileWidth("Streaming Sites".equals(title) || "Live Sports".equals(title) ? 2 : 3),
                dp(rowHeight - 20));
        lp.rightMargin = dp(12);
        row.addView(tile, lp);
    }

    private void addToolAppTile(LinearLayout row, AppEntry entry, String subtitle) {
        addActionTile(row, entry.label, subtitle, android.R.drawable.ic_menu_manage, () -> launch(entry));
        ImageView icon = row.getChildAt(row.getChildCount() - 1).findViewWithTag("icon");
        icon.setImageDrawable(entry.icon);
    }

    private int tileWidth(int columns) {
        return (getResources().getDisplayMetrics().widthPixels - dp(92)) / columns - dp(12);
    }

    private AppEntry findAppByLabel(String needle) {
        String wanted = needle.toLowerCase(Locale.getDefault());
        for (AppEntry entry : appsByPackage.values()) {
            if (entry.label.toLowerCase(Locale.getDefault()).equals(wanted)) {
                return entry;
            }
        }
        for (AppEntry entry : appsByPackage.values()) {
            if (entry.label.toLowerCase(Locale.getDefault()).contains(wanted)) {
                return entry;
            }
        }
        return null;
    }

    private View createTile(String title, Drawable icon, String badge, boolean large) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(large ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(16), dp(10), dp(16), dp(10));
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setBackgroundResource(R.drawable.tile_normal);
        tile.setElevation(dp(2));

        ImageView iconView = new ImageView(this);
        iconView.setTag("icon");
        iconView.setImageDrawable(icon);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize = large ? 48 : 30;
        tile.addView(iconView, new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(
                large ? ViewGroup.LayoutParams.MATCH_PARENT : 0, ViewGroup.LayoutParams.WRAP_CONTENT,
                large ? 0f : 1f);
        labelsLp.leftMargin = large ? 0 : dp(12);
        tile.addView(labels, labelsLp);
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(Color.rgb(246, 248, 252));
        name.setTextSize(large ? 16 : 14);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = large ? dp(8) : 0;
        labels.addView(name, nameLp);

        if (badge != null) {
            TextView badgeView = new TextView(this);
            badgeView.setText(badge);
            badgeView.setTextColor(Color.rgb(157, 191, 255));
            badgeView.setTextSize(11);
            badgeView.setSingleLine(true);
            badgeView.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(badgeView);
        }

        tile.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate()
                    .scaleX(hasFocus ? 1.055f : 1.0f)
                    .scaleY(hasFocus ? 1.055f : 1.0f)
                    .translationY(hasFocus ? -dp(3) : 0)
                    .setDuration(105)
                    .start();
            v.setElevation(hasFocus ? dp(18) : dp(2));
            v.setBackgroundResource(hasFocus ? R.drawable.tile_focused : R.drawable.tile_normal);

            if (hasFocus) {
                focusedKey = (String) v.getTag();
                focusTitle.setText(title);
                focusSubtitle.setText(badge == null ? "Press OK to open" : badge + " · press OK to open");

                View parent = (View) v.getParent();
                if (parent != null && parent.getParent() instanceof HorizontalScrollView) {
                    HorizontalScrollView hsv = (HorizontalScrollView) parent.getParent();
                    hsv.smoothScrollTo(Math.max(0, v.getLeft() - dp(50)), 0);
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

    private void migrateFavoritesIfNeeded(List<AppEntry> entries) {
        if (prefs.getBoolean(PREF_V2_MIGRATED, false)) return;

        String[] desiredOrder = {
                "prime video",
                "peacock",
                "youtube"
        };

        List<String> chosen = new ArrayList<>();

        for (String desired : desiredOrder) {
            for (AppEntry entry : entries) {
                String label = entry.label.toLowerCase(Locale.getDefault());
                if (!chosen.contains(entry.packageName) && label.contains(desired)) {
                    chosen.add(entry.packageName);
                    break;
                }
            }
        }

        saveFavoritePackages(chosen);
        prefs.edit().putBoolean(PREF_V2_MIGRATED, true).apply();
    }

    private List<String> getFavoritePackages() {
        String raw = prefs.getString(PREF_FAVORITE_PACKAGES, "");
        List<String> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) raw = "";

        String[] pieces = raw.split("\\|");
        for (String piece : pieces) {
            if (!piece.trim().isEmpty() && !piece.startsWith("missing:") && !result.contains(piece)) {
                result.add(piece);
            }
        }
        String[] primary = {"Prime Video", "Peacock", "YouTube"};
        for (int index = 0; index < primary.length; index++) {
            AppEntry installed = findAppByLabel(primary[index]);
            String key = installed == null ? "missing:" + primary[index] : installed.packageName;
            result.remove(key);
            result.add(index, key);
        }
        return result;
    }

    private void saveFavoritePackages(List<String> packages) {
        StringBuilder out = new StringBuilder();
        for (String pkg : packages) {
            if (out.length() > 0) out.append('|');
            out.append(pkg);
        }
        prefs.edit().putString(PREF_FAVORITE_PACKAGES, out.toString()).apply();
    }

    private void toggleFavorite(String packageName) {
        AppEntry entry = appsByPackage.get(packageName);
        if (entry != null && (entry == findAppByLabel("prime video")
                || entry == findAppByLabel("peacock") || entry == findAppByLabel("youtube"))) {
            Toast.makeText(this, "Prime Video, Peacock and YouTube stay in Favorites.", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> current = getFavoritePackages();
        boolean added;

        if (current.contains(packageName)) {
            current.remove(packageName);
            added = false;
        } else {
            current.add(packageName);
            added = true;
        }

        saveFavoritePackages(current);
        Toast.makeText(this, added ? "Added to Favorites" : "Removed from Favorites", Toast.LENGTH_SHORT).show();
        refreshApps();
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
        final String packageName;
        final ComponentName component;
        final Drawable icon;

        AppEntry(String label, String packageName, ComponentName component, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.component = component;
            this.icon = icon;
        }
    }
}
