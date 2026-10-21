package com.aj.firelauncher;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w960dp-h540dp-land-mdpi")
public class MainActivityTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    @Before
    public void start() {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        settle();
        layout(960, 540);
    }

    @After
    public void stop() {
        controller.pause().stop().destroy();
    }

    @Test
    public void startsWithPinnedFavoritesAndOnlyThreeSections() {
        assertNotNull(target("missing:Prime Video"));
        assertNotNull(target("missing:Peacock"));
        assertNotNull(target("missing:YouTube"));
        assertEquals("Favorites", textOf(target("rail:0")));
        assertEquals("Explore", textOf(target("rail:1")));
        assertEquals("All Apps", textOf(target("rail:2")));
        assertTrue(target("missing:Prime Video").hasFocus());
        target("missing:Prime Video").performClick(); // Missing package must not crash.
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void upDownLoopsBothDirectionsAndRestoresEachSection() {
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(target("missing:Peacock").hasFocus());
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(target("web:Live Sports").hasFocus());
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue(target("rail:2").hasFocus()); // Empty apps still has a focus target.
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue(target("rail:0").hasFocus());
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(target("missing:Peacock").hasFocus());
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(target("rail:2").hasFocus());
        key(KeyEvent.KEYCODE_DPAD_UP);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(target("web:Live Sports").hasFocus());
    }

    @Test
    public void shortcutsKeepExactUrlsAndBrowserIntegration() {
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        for (String[] shortcut : new String[][] {
                {"Streaming Sites", "https://fmhy.net/video#streaming-sites"},
                {"Live Sports", "https://fmhy.net/video#live-sports"},
                {"IMDb", "https://www.imdb.com/"}}) {
            target("web:" + shortcut[0]).performClick();
            Intent launched = shadowOf(activity).getNextStartedActivity();
            assertEquals(shortcut[1], launched.getDataString());
            assertEquals(new ComponentName(activity, BrowserActivity.class), launched.getComponent());
        }
        target("browser").performClick();
        assertEquals(new ComponentName(activity, BrowserActivity.class),
                shadowOf(activity).getNextStartedActivity().getComponent());
    }

    @Test
    public void settingsGearAndToolsAreReachableUsingOnlyRemote() {
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertTrue(target("rail:0").hasFocus());
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertTrue(target("settings").hasFocus());
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.isShowing());
        dialog.getListView().performItemClick(null, 0, 0);
        settle();
        assertEquals(Settings.ACTION_SETTINGS, shadowOf(activity).getNextStartedActivity().getAction());
        assertTrue(target("settings").hasFocus());
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue(target("missing:Prime Video").hasFocus());
        key(KeyEvent.KEYCODE_MENU);
        assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());
        ShadowAlertDialog.getLatestAlertDialog().dismiss();
        assertTrue(target("settings").hasFocus());
    }

    @Test
    public void returningAndRecreatingPreservesShortcutAndSectionHistory() {
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        target("web:Live Sports").performClick();
        controller.pause().resume();
        settle();
        assertTrue(target("web:Live Sports").hasFocus());
        controller.recreate();
        activity = controller.get();
        settle();
        assertTrue(target("web:Live Sports").hasFocus());
        activity.onBackPressed();
        assertTrue(target("rail:1").hasFocus());
        activity.onBackPressed();
        assertTrue(target("rail:0").hasFocus());
    }

    @Test
    public void installedAppsPaginateLaunchAndRetainFavorites() {
        installApps(40);
        resume();
        key(KeyEvent.KEYCODE_DPAD_UP);
        layout(960, 540);
        assertTrue(target("app:example.app00").hasFocus());
        for (int i = 0; i < 15; i++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(target("app:example.app15").hasFocus());
        assertNull(find("app:example.app00"));
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        Intent launched = shadowOf(activity).getNextStartedActivity();
        assertEquals(new ComponentName("example.app15", "example.app15.Tv"), launched.getComponent());
        controller.pause().resume();
        settle();
        assertTrue(target("app:example.app15").hasFocus());
        target("app:example.app15").performLongClick();
        settle();
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertNotNull(target("favorite:example.app15"));
        assertNotNull(target("missing:Prime Video"));
        assertNotNull(target("missing:Peacock"));
        assertNotNull(target("missing:YouTube"));
    }

    @Test
    public void remoteLongSelectTogglesFavoriteWithoutLaunchingApp() {
        installApps(3);
        resume();
        key(KeyEvent.KEYCODE_DPAD_UP);
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
        activity.dispatchKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER, 1, 0, -1, 0, KeyEvent.FLAG_LONG_PRESS));
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        settle();
        assertNull(shadowOf(activity).getNextStartedActivity());
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertNotNull(target("favorite:example.app00"));
    }

    @Test
    public void allPagesAndPagerControlsAreRemoteReachable() {
        installApps(40);
        resume();
        key(KeyEvent.KEYCODE_DPAD_UP);
        layout(960, 540);
        for (int i = 0; i < 39; i++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(target("app:example.app39").hasFocus());
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(target("previous").hasFocus());
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(target("app:example.app14").hasFocus());
        for (int i = 14; i < 39; i++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(target("next").hasFocus());
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(target("app:example.app00").hasFocus());
    }

    @Test
    public void keepsConfiguredFavoritesIncludingTemporarilyUninstalledPackages() {
        installApps(20);
        activity.getSharedPreferences("launcher_prefs", 0).edit()
                .putString("favorite_packages_v2", "example.app10|temporarily.absent|example.app05")
                .putBoolean("favorites_v4_migrated", false).commit();
        resume();
        assertNotNull(target("favorite:example.app10"));
        assertNotNull(target("favorite:example.app05"));
        assertTrue(activity.getSharedPreferences("launcher_prefs", 0)
                .getString("favorite_packages_v2", "").contains("temporarily.absent"));
    }

    @Test
    public void manyFavoritesUseSingleRowPagesAndRememberFocus() {
        installApps(20);
        StringBuilder favorites = new StringBuilder();
        for (int i = 0; i < 20; i++) favorites.append(String.format(java.util.Locale.US, "example.app%02d|", i));
        activity.getSharedPreferences("launcher_prefs", 0).edit()
                .putString("favorite_packages_v2", favorites.toString()).commit();
        resume();
        for (int i = 0; i < 12; i++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        View focused = root().findFocus();
        assertNotNull("Favorite retains focus after pagination", focused);
        String key = (String) focused.getTag();
        assertTrue(key.startsWith("favorite:"));
        assertNull(find("missing:Prime Video"));
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(target(key).hasFocus());
        layout(960, 540);
        assertBounds(root(), root().getWidth(), root().getHeight());
    }

    @Test
    public void fixedViewportHasNoScrollingOrClippingAtTvSizes() {
        installApps(40);
        resume();
        for (int[] size : new int[][] {{960, 540}, {1280, 720}, {1920, 1080}, {640, 360}}) {
            for (int section = 0; section < 3; section++) {
                target("rail:" + section).requestFocus();
                layout(size[0], size[1]);
                assertBounds(root(), size[0], size[1]);
                assertNoScroll(root());
            }
        }
    }

    @Test
    public void browserLoadsShortcutWithoutChangingPopupSettings() {
        Intent intent = new Intent().setData(Uri.parse("https://fmhy.net/video#live-sports"));
        try (ActivityController<BrowserActivity> browser =
                     Robolectric.buildActivity(BrowserActivity.class, intent).setup()) {
            WebView web = findWebView(browser.get().findViewById(android.R.id.content));
            assertEquals(intent.getDataString(), web.getUrl());
            assertTrue(web.getSettings().supportMultipleWindows());
            assertFalse(web.getSettings().getJavaScriptCanOpenWindowsAutomatically());
            assertTrue(web.getSettings().getJavaScriptEnabled());
            assertFalse(shadowOf(web).getWebChromeClient().onCreateWindow(web, false, true, null));
        }
    }

    @Test
    public void browserToolKeepsExistingHomePage() {
        try (ActivityController<BrowserActivity> browser =
                     Robolectric.buildActivity(BrowserActivity.class).setup()) {
            assertEquals("https://search.brave.com/",
                    findWebView(browser.get().findViewById(android.R.id.content)).getUrl());
        }
    }

    private void installApps(int count) {
        for (int i = 0; i < count; i++) {
            String pkg = String.format(java.util.Locale.US, "example.app%02d", i);
            for (String category : new String[] {Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER}) {
                ResolveInfo info = new ResolveInfo();
                info.nonLocalizedLabel = String.format(java.util.Locale.US, "App %02d", i);
                info.activityInfo = new ActivityInfo();
                info.activityInfo.packageName = pkg;
                info.activityInfo.name = pkg + (category.equals(Intent.CATEGORY_LAUNCHER) ? ".Phone" : ".Tv");
                info.activityInfo.applicationInfo = new ApplicationInfo();
                info.activityInfo.applicationInfo.packageName = pkg;
                shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).addResolveInfoForIntent(
                        new Intent(Intent.ACTION_MAIN).addCategory(category), info);
            }
        }
    }

    private void resume() { controller.pause().resume(); settle(); layout(960, 540); }
    private void settle() { shadowOf(Looper.getMainLooper()).idle(); }
    private void key(int code) {
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        settle();
    }
    private View root() { return ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0); }
    private void layout(int width, int height) {
        settle();
        // Measure the home itself; pending decor traversals use the simulated display size.
        for (int pass = 0; pass < 3; pass++) {
            root().measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root().layout(0, 0, width, height);
        }
    }
    private View find(String key) { return root().findViewWithTag(key); }
    private View target(String key) {
        View view = find(key);
        assertNotNull("Target: " + key, view);
        return view;
    }
    private String textOf(View view) { return ((TextView) view).getText().toString(); }
    private void assertNoScroll(View view) {
        assertFalse(view instanceof ScrollView || view instanceof HorizontalScrollView);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            assertNoScroll(((ViewGroup) view).getChildAt(i));
    }
    private void assertBounds(View view, int width, int height) {
        if (view.getVisibility() == View.GONE) return;
        int[] xy = new int[2];
        int[] origin = new int[2];
        view.getLocationOnScreen(xy);
        root().getLocationOnScreen(origin);
        assertTrue("left: " + view.getTag(), xy[0] >= origin[0]);
        assertTrue("top: " + view.getTag(), xy[1] >= origin[1]);
        assertTrue("right: " + view.getTag(), xy[0] + view.getWidth() <= origin[0] + width);
        assertTrue("bottom: " + view.getTag(), xy[1] + view.getHeight() <= origin[1] + height);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            assertBounds(((ViewGroup) view).getChildAt(i), width, height);
    }
    private WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            WebView result = findWebView(((ViewGroup) view).getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }
}
