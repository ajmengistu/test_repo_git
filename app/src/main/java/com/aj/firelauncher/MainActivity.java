package com.aj.firelauncher;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private static final String[] SECTIONS = {"Favorites", "Explore", "All Apps"};
    private static final int TEXT = Color.rgb(250, 250, 250);
    private static final int MUTED = Color.rgb(153, 153, 165);
    private static final int ACCENT = Color.rgb(141, 219, 255);
    private final Handler clockHandler = new Handler();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private SharedPreferences prefs;
    private final Map<String, AppEntry> appsByPackage = new LinkedHashMap<>();
    private List<AppEntry> currentEntries = new ArrayList<>();
    private final List<HomeItem> items = new ArrayList<>();
    private final List<View> visibleTiles = new ArrayList<>();
    private final TextView[] rail = new TextView[3];
    private final String[] selectedKeys = {"", "", ""};
    private final int[] selectedIndices = new int[3];
    private final List<Integer> sectionHistory = new ArrayList<>();
    private int section;
    private int columns = 7;
    private int rows = 2;
    private int page;
    private String focusArea = "content";
    private LinearLayout tileArea;
    private LinearLayout content;
    private TextView clockView;
    private TextView heading;
    private TextView description;
    private TextView focusInfo;
    private TextView pageInfo;
    private TextView previous;
    private TextView next;
    private TextView settings;
    private AlertDialog toolsDialog;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (state != null) {
            section = Math.max(0, Math.min(2, state.getInt("section")));
            focusArea = state.getString("focusArea", "content");
            for (int i = 0; i < 3; i++) {
                selectedKeys[i] = state.getString("itemKey" + i, "");
                selectedIndices[i] = state.getInt("itemIndex" + i);
            }
            int[] history = state.getIntArray("sectionHistory");
            if (history != null) for (int value : history) sectionHistory.add(value);
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
    protected void onDestroy() {
        if (toolsDialog != null) toolsDialog.dismiss();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("section", section);
        state.putString("focusArea", focusArea);
        for (int i = 0; i < 3; i++) {
            state.putString("itemKey" + i, selectedKeys[i]);
            state.putInt("itemIndex" + i, selectedIndices[i]);
        }
        int[] history = new int[sectionHistory.size()];
        for (int i = 0; i < history.length; i++) history[i] = sectionHistory.get(i);
        state.putIntArray("sectionHistory", history);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onBackPressed() {
        if (!"rail".equals(focusArea)) {
            rail[section].requestFocus();
        } else if (!sectionHistory.isEmpty()) {
            section = sectionHistory.remove(sectionHistory.size() - 1);
            renderSection(true);
        } else {
            // A launcher stays at its root rather than exiting into a blank home screen.
            section = 0;
            renderSection(false);
        }
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(18), dp(28), dp(18));
        root.setBackgroundResource(R.drawable.screen_bg);
        root.setTag("home-root");

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView brand = text("AJ / TV", 16, TEXT);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        clockView = text("", 18, TEXT);
        header.addView(clockView, new LinearLayout.LayoutParams(-2, -2));
        settings = text("⚙", 26, TEXT);
        settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("Settings and tools");
        makeFocusable(settings, "settings", () -> showTools());
        LinearLayout.LayoutParams gearLp = new LinearLayout.LayoutParams(dp(44), dp(40));
        gearLp.leftMargin = dp(20);
        header.addView(settings, gearLp);

        LinearLayout body = new LinearLayout(this);
        body.setPadding(0, dp(22), 0, dp(14));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.VERTICAL);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(navigation, new LinearLayout.LayoutParams(dp(120), -1));
        for (int i = 0; i < 3; i++) {
            final int index = i;
            TextView entry = text(SECTIONS[i], 16, MUTED);
            entry.setGravity(Gravity.CENTER_VERTICAL);
            entry.setPadding(dp(12), 0, dp(8), 0);
            makeFocusable(entry, "rail:" + i, () -> {
                if (section != index) switchSection(index, true);
                focusContent();
            });
            entry.setOnFocusChangeListener((v, focused) -> {
                if (focused) {
                    focusArea = "rail";
                    if (section != index) switchSection(index, true);
                }
                updateRail();
            });
            rail[i] = entry;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
            lp.bottomMargin = dp(8);
            navigation.addView(entry, lp);
        }

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(0, -1, 1f);
        contentLp.leftMargin = dp(24);
        body.addView(content, contentLp);
        heading = text("", 26, TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(heading, new LinearLayout.LayoutParams(-1, dp(36)));
        description = text("", 13, MUTED);
        content.addView(description, new LinearLayout.LayoutParams(-1, dp(26)));
        tileArea = new LinearLayout(this);
        tileArea.setOrientation(LinearLayout.VERTICAL);
        tileArea.setGravity(Gravity.CENTER_VERTICAL);
        tileArea.setPadding(dp(4), dp(4), dp(4), dp(4));
        content.addView(tileArea, new LinearLayout.LayoutParams(-1, 0, 1f));
        tileArea.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l <= 0 || b - t <= 0) return;
            int newColumns = Math.max(1, Math.min(7, (r - l - dp(8)) / dp(100)));
            int newRows = Math.max(1, Math.min(2, (b - t - dp(8)) / dp(120)));
            if (newColumns != columns || newRows != rows) {
                columns = newColumns;
                rows = newRows;
                renderSection(false);
            }
        });
        LinearLayout pager = new LinearLayout(this);
        pager.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(pager, new LinearLayout.LayoutParams(-1, dp(40)));
        focusInfo = text("", 14, MUTED);
        pager.addView(focusInfo, new LinearLayout.LayoutParams(0, -2, 1f));
        previous = text("‹", 24, TEXT);
        previous.setGravity(Gravity.CENTER);
        makeFocusable(previous, "previous", () -> changePage(-1));
        previous.setContentDescription("Previous page");
        pager.addView(previous, new LinearLayout.LayoutParams(dp(40), -1));
        pageInfo = text("", 13, MUTED);
        pageInfo.setGravity(Gravity.CENTER);
        pager.addView(pageInfo, new LinearLayout.LayoutParams(dp(76), -2));
        next = text("›", 24, TEXT);
        next.setGravity(Gravity.CENTER);
        makeFocusable(next, "next", () -> changePage(1));
        next.setContentDescription("Next page");
        pager.addView(next, new LinearLayout.LayoutParams(dp(40), -1));
        TextView help = text("↑ ↓  Sections     ← →  Items     OK  Open     Hold OK  Favorite     Menu  Settings", 12, MUTED);
        root.addView(help, new LinearLayout.LayoutParams(-1, dp(24)));
        setContentView(root);
    }

    private void makeFocusable(View view, String key, Runnable action) {
        view.setId(View.generateViewId());
        view.setTag(key);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setBackgroundResource(R.drawable.tile_normal);
        view.setOnClickListener(v -> action.run());
        view.setOnFocusChangeListener((v, focused) -> {
            v.setBackgroundResource(focused ? R.drawable.tile_focused : R.drawable.tile_normal);
            if (focused) focusArea = key;
        });
    }

    private void updateRail() {
        for (int i = 0; i < rail.length; i++) {
            if (rail[i] == null) continue;
            boolean active = i == section;
            rail[i].setSelected(active);
            rail[i].setTextColor(active ? TEXT : MUTED);
            rail[i].setBackgroundResource(rail[i].hasFocus() ? R.drawable.tile_focused
                    : active ? R.drawable.nav_selected : android.R.color.transparent);
        }
    }

    private void refreshApps() {
        appsByPackage.clear();
        PackageManager pm = getPackageManager();
        // Prefer the TV activity when a package also exposes a phone launcher activity.
        queryCategory(pm, Intent.CATEGORY_LAUNCHER);
        queryCategory(pm, Intent.CATEGORY_LEANBACK_LAUNCHER);
        currentEntries = new ArrayList<>(appsByPackage.values());
        Collections.sort(currentEntries, (a, b) -> a.label.compareToIgnoreCase(b.label));
        migrateFavoritesIfNeeded(currentEntries);
        renderSection(false);
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

    private void switchSection(int target, boolean remember) {
        if (target == section) return;
        if (remember) {
            if (sectionHistory.size() == 32) sectionHistory.remove(0);
            sectionHistory.add(section);
        }
        section = target;
        renderSection(true);
    }

    private void renderSection(boolean animate) {
        items.clear();
        if (section == 0) {
            for (String pkg : getFavoritePackages()) {
                AppEntry app = appsByPackage.get(pkg);
                if (app != null) addApp("favorite:", app);
                else if (pkg.startsWith("missing:")) {
                    String label = pkg.substring(8);
                    items.add(new HomeItem(pkg, label, getDrawable(android.R.drawable.ic_media_play),
                            "Not installed", () -> showMissingApp(label), null));
                }
            }
        } else if (section == 1) {
            addShortcut("Streaming Sites", "https://fmhy.net/video#streaming-sites", android.R.drawable.ic_menu_search);
            addShortcut("Live Sports", "https://fmhy.net/video#live-sports", android.R.drawable.ic_media_play);
            items.add(new HomeItem("browser", "AJ Browser", getDrawable(android.R.drawable.ic_menu_search),
                    "Brave Search", () -> openIntent(new Intent(this, BrowserActivity.class)), null));
            addShortcut("IMDb", "https://www.imdb.com/", android.R.drawable.btn_star_big_on);
        } else {
            for (AppEntry app : currentEntries) addApp("app:", app);
        }
        int selected = Math.max(0, Math.min(selectedIndices[section], items.size() - 1));
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).key.equals(selectedKeys[section])) selected = i;
        }
        selectedIndices[section] = selected;
        page = selected / pageSize();
        heading.setText(SECTIONS[section]);
        description.setText(section == 0 ? "Your favorites, one click away"
                : section == 1 ? "Web shortcuts · opens in AJ Browser"
                : "Installed apps · hold OK to add or remove a favorite");
        renderPage();
        updateRail();
        if (animate) {
            content.animate().cancel();
            content.setAlpha(0.35f);
            content.animate().alpha(1f).setDuration(180).start();
        }
    }

    private void addApp(String prefix, AppEntry app) {
        items.add(new HomeItem(prefix + app.packageName, app.label, app.icon,
                getFavoritePackages().contains(app.packageName) ? "Favorite · hold OK to customize" : "Hold OK to favorite",
                () -> launch(app), app.packageName));
    }

    private void addShortcut(String label, String url, int icon) {
        items.add(new HomeItem("web:" + label, label, getDrawable(icon), "Open in AJ Browser",
                () -> openBrowser(url), null));
    }

    private int pageSize() {
        return columns * (section == 2 ? rows : 1);
    }

    private int pageCount() {
        return Math.max(1, (items.size() + pageSize() - 1) / pageSize());
    }

    private void renderPage() {
        // Removing focused views can trigger framework focus search; preserve our logical location.
        String restoreArea = focusArea;
        tileArea.removeAllViews();
        visibleTiles.clear();
        int start = page * pageSize();
        int end = Math.min(items.size(), start + pageSize());
        int rowCount = Math.max(1, (end - start + columns - 1) / columns);
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int available = tileArea.getHeight() - dp(8);
            int height = available > 0 ? Math.min(dp(116), available / rowCount) : dp(116);
            tileArea.addView(row, new LinearLayout.LayoutParams(-1, height));
            for (int col = 0; col < columns; col++) {
                int index = start + rowIndex * columns + col;
                View tile = index < end ? createTile(items.get(index), index) : new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(tile, lp);
                if (index < end) visibleTiles.add(tile);
            }
        }
        if (items.isEmpty()) {
            tileArea.removeAllViews();
            tileArea.addView(text("No launchable apps installed", 16, MUTED));
        }
        pageInfo.setText((page + 1) + " / " + pageCount());
        previous.setVisibility(pageCount() > 1 ? View.VISIBLE : View.GONE);
        next.setVisibility(pageCount() > 1 ? View.VISIBLE : View.GONE);
        focusArea = restoreArea;
        restoreFocus();
    }

    private View createTile(HomeItem item, int index) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(4), dp(8), dp(4), dp(8));
        makeFocusable(tile, item.key, item.action);
        tile.setContentDescription(item.label + ", " + item.hint);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(item.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView name = text(item.label, 14, TEXT);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, dp(24));
        labelLp.topMargin = dp(8);
        tile.addView(name, labelLp);
        if (item.packageName != null) tile.setOnLongClickListener(v -> {
            toggleFavorite(item.packageName);
            return true;
        });
        tile.setOnFocusChangeListener((v, focused) -> {
            v.setBackgroundResource(focused ? R.drawable.tile_focused : R.drawable.tile_normal);
            v.animate().scaleX(focused ? 1.025f : 1f).scaleY(focused ? 1.025f : 1f).setDuration(160).start();
            if (focused) {
                focusArea = "content";
                selectedKeys[section] = item.key;
                selectedIndices[section] = index;
                focusInfo.setText(item.label + " · " + item.hint);
            }
        });
        return tile;
    }

    private void restoreFocus() {
        if ("settings".equals(focusArea)) settings.requestFocus();
        else if ("rail".equals(focusArea)) rail[section].requestFocus();
        else focusContent();
    }

    private void focusContent() {
        if (visibleTiles.isEmpty()) rail[section].requestFocus();
        else {
            int local = Math.max(0, Math.min(visibleTiles.size() - 1,
                    selectedIndices[section] - page * pageSize()));
            visibleTiles.get(local).requestFocus();
        }
    }

    private void changePage(int direction) {
        if (pageCount() <= 1) return;
        page = (page + direction + pageCount()) % pageCount();
        selectedIndices[section] = page * pageSize();
        selectedKeys[section] = items.get(selectedIndices[section]).key;
        focusArea = "content";
        renderPage();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean directional = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (key == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) showTools();
            return true;
        }
        if (!directional) return super.dispatchKeyEvent(event);
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        if ("settings".equals(focusArea)) {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) rail[section].requestFocus();
            else focusContent();
        } else if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            switchSection((section + (key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 2)) % 3, true);
        } else if ("rail".equals(focusArea)) {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) settings.requestFocus();
            else focusContent();
        } else if ("previous".equals(focusArea) || "next".equals(focusArea)) {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                if ("next".equals(focusArea)) previous.requestFocus();
                else focusContent();
            } else if ("previous".equals(focusArea)) next.requestFocus();
            else settings.requestFocus();
        } else {
            int direction = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
            int index = selectedIndices[section] + direction;
            if (index < 0) rail[section].requestFocus();
            else if (index >= items.size()) {
                if (pageCount() > 1) previous.requestFocus();
                else settings.requestFocus();
            } else {
                selectedIndices[section] = index;
                selectedKeys[section] = items.get(index).key;
                if (index / pageSize() != page) {
                    page = index / pageSize();
                    renderPage();
                } else focusContent();
            }
        }
        return true;
    }

    private void showTools() {
        if (toolsDialog != null && toolsDialog.isShowing()) return;
        focusArea = "settings";
        settings.requestFocus();
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Fire TV Settings");
        actions.add(() -> openIntent(new Intent(Settings.ACTION_SETTINGS)));
        labels.add("AJ Browser");
        actions.add(() -> openIntent(new Intent(this, BrowserActivity.class)));
        AppEntry nord = findAppByLabel("nordvpn");
        labels.add(nord == null ? "NordVPN · not installed" : "NordVPN");
        actions.add(() -> {
            if (nord == null) showMissingApp("NordVPN");
            else launch(nord);
        });
        AppEntry home = findAppByLabel("home on fire");
        if (home != null) {
            labels.add("Home on Fire");
            actions.add(() -> launch(home));
        }
        toolsDialog = new AlertDialog.Builder(this).setTitle("Settings & tools")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .setNegativeButton("Close", (dialog, which) -> { })
                .create();
        toolsDialog.setOnDismissListener(dialog -> settings.requestFocus());
        toolsDialog.show();
    }

    private void openBrowser(String url) {
        openIntent(new Intent(this, BrowserActivity.class).setData(Uri.parse(url)));
    }

    private void showMissingApp(String label) {
        Toast.makeText(this, "Install " + label + " from the Amazon Appstore.", Toast.LENGTH_LONG).show();
    }

    private static final class HomeItem {
        final String key;
        final String label;
        final Drawable icon;
        final String hint;
        final Runnable action;
        final String packageName;

        HomeItem(String key, String label, Drawable icon, String hint, Runnable action, String packageName) {
            this.key = key;
            this.label = label;
            this.icon = icon;
            this.hint = hint;
            this.action = action;
            this.packageName = packageName;
        }
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
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Unable to open this item.", Toast.LENGTH_SHORT).show();
        }
    }

    private void migrateFavoritesIfNeeded(List<AppEntry> entries) {
        if (prefs.getBoolean(PREF_V2_MIGRATED, false)) return;

        // Keep configured favorites from earlier installs, including absent packages.
        if (!prefs.getString(PREF_FAVORITE_PACKAGES, "").trim().isEmpty()) {
            prefs.edit().putBoolean(PREF_V2_MIGRATED, true).apply();
            return;
        }

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
