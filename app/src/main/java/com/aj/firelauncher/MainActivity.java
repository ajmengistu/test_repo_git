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
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String PREFS = "launcher_prefs";
    private static final String PREF_FAVORITE_PACKAGES = "favorite_packages_v2";
    private static final String PREF_V2_MIGRATED = "favorites_v2_migrated";

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
        // Stay on the launcher.
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
        content.setPadding(dp(54), dp(32), dp(54), dp(54));
        verticalScroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(104)));

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
        focusTitle.setTextSize(31);
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
        Collections.sort(entries, Comparator.comparing(a -> a.label.toLowerCase(Locale.getDefault())));

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
        while (content.getChildCount() > 1) {
            content.removeViewAt(1);
        }

        List<String> favorites = getFavoritePackages();

        addSectionTitle("Favorites", "Press OK to open · hold OK to add or remove");

        HorizontalScrollView favoritesScroll = new HorizontalScrollView(this);
        favoritesScroll.setHorizontalScrollBarEnabled(false);
        favoritesScroll.setClipToPadding(false);
        content.addView(favoritesScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(176)));

        LinearLayout favoritesRow = new LinearLayout(this);
        favoritesRow.setOrientation(LinearLayout.HORIZONTAL);
        favoritesRow.setGravity(Gravity.CENTER_VERTICAL);
        favoritesRow.setPadding(dp(3), dp(9), dp(24), dp(9));
        favoritesScroll.addView(favoritesRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        for (String pkg : favorites) {
            AppEntry entry = appsByPackage.get(pkg);
            if (entry != null) {
                addFavoriteTile(favoritesRow, entry);
            }
        }

        addSystemTile(favoritesRow, "Settings", "Fire TV", () -> openIntent(new Intent(Settings.ACTION_SETTINGS)), true);

        addSectionTitle("Tools", "Private browsing, VPN, and launcher controls");

        HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        toolsScroll.setClipToPadding(false);
        content.addView(toolsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(176)));

        LinearLayout toolsRow = new LinearLayout(this);
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        toolsRow.setGravity(Gravity.CENTER_VERTICAL);
        toolsRow.setPadding(dp(3), dp(9), dp(24), dp(9));
        toolsScroll.addView(toolsRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        addActionTile(
                toolsRow,
                "AJ Browser",
                "Aggressive popup blocking",
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

        addSectionTitle("All apps", "Everything installed, without duplicates");

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        grid.setUseDefaultMargins(false);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridLp.topMargin = dp(4);
        content.addView(grid, gridLp);

        for (AppEntry entry : entries) {
            addGridTile(grid, entry, favorites.contains(entry.packageName));
        }

        content.post(() -> {
            if (favoritesRow.getChildCount() > 0) favoritesRow.getChildAt(0).requestFocus();
        });
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
        bindAppTile(tile, entry);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(224), dp(150));
        lp.rightMargin = dp(16);
        row.addView(tile, lp);
    }

    private void addGridTile(GridLayout grid, AppEntry entry, boolean favorite) {
        View tile = createTile(entry.label, entry.icon, favorite ? "Favorite" : null, false);
        bindAppTile(tile, entry);

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(206);
        lp.height = dp(126);
        lp.setMargins(0, 0, dp(16), dp(16));
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
        View tile = createTile(title, icon, subtitle, true);
        tile.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(224), dp(150));
        lp.rightMargin = dp(16);
        row.addView(tile, lp);
    }

    private void addToolAppTile(LinearLayout row, AppEntry entry, String subtitle) {
        View tile = createTile(entry.label, entry.icon, subtitle, true);
        tile.setOnClickListener(v -> launch(entry));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(224), dp(150));
        lp.rightMargin = dp(16);
        row.addView(tile, lp);
    }

    private AppEntry findAppByLabel(String needle) {
        String wanted = needle.toLowerCase(Locale.getDefault());
        for (AppEntry entry : appsByPackage.values()) {
            if (entry.label.toLowerCase(Locale.getDefault()).contains(wanted)) {
                return entry;
            }
        }
        return null;
    }

    private View createTile(String title, Drawable icon, String badge, boolean large) {
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
        int iconSize = large ? 66 : 52;
        tile.addView(iconView, new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)));

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(Color.rgb(246, 248, 252));
        name.setTextSize(large ? 16 : 14);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(8);
        tile.addView(name, nameLp);

        if (badge != null) {
            TextView badgeView = new TextView(this);
            badgeView.setText(badge);
            badgeView.setTextColor(Color.rgb(157, 191, 255));
            badgeView.setTextSize(11);
            badgeView.setSingleLine(true);
            tile.addView(badgeView);
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
                "youtube",
                "hulu",
                "disney",
                "max",
                "plex",
                "spotify"
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

        // Fill remaining spaces with installed apps, but do not auto-favorite Netflix.
        for (AppEntry entry : entries) {
            if (chosen.size() >= 8) break;
            String label = entry.label.toLowerCase(Locale.getDefault());
            if (label.contains("netflix")) continue;
            if (!chosen.contains(entry.packageName)) chosen.add(entry.packageName);
        }

        saveFavoritePackages(chosen);
        prefs.edit().putBoolean(PREF_V2_MIGRATED, true).apply();
    }

    private List<String> getFavoritePackages() {
        String raw = prefs.getString(PREF_FAVORITE_PACKAGES, "");
        List<String> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return result;

        String[] pieces = raw.split("\\|");
        for (String piece : pieces) {
            if (!piece.trim().isEmpty() && !result.contains(piece)) {
                result.add(piece);
            }
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
