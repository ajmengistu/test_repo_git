package com.aj.firelauncher;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MainActivityTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    @Before
    public void start() {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @After
    public void stop() {
        controller.pause().stop().destroy();
    }

    @Test
    public void startsCollapsedWithPrimaryFavoritesAndTools() {
        assertNotNull(text("Prime Video"));
        assertNotNull(text("Peacock"));
        assertNotNull(text("YouTube"));
        assertNotNull(text("Settings & Tools"));
        assertNotNull(text("NordVPN"));
        assertNotNull(text("AJ Browser"));
        assertEquals("All Apps, collapsed", tile("All Apps").getContentDescription());
    }

    @Test
    public void shortcutsUseExactFragmentsAndExplicitInternalBrowser() {
        for (String[] shortcut : new String[][] {
                {"Streaming Sites", "https://fmhy.net/video#streaming-sites"},
                {"Live Sports", "https://fmhy.net/video#live-sports"}}) {
            tile(shortcut[0]).performClick();
            Intent launched = shadowOf(activity).getNextStartedActivity();
            assertEquals(shortcut[1], launched.getDataString());
            assertEquals(new ComponentName(activity, BrowserActivity.class), launched.getComponent());
        }
    }

    @Test
    public void expandingAndBackCollapsingKeepsToggleFocus() {
        tile("All Apps").requestFocus();
        tile("All Apps").performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("All Apps, expanded", tile("All Apps").getContentDescription());
        assertTrue(tile("All Apps").hasFocus());
        activity.onBackPressed();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("All Apps, collapsed", tile("All Apps").getContentDescription());
        assertTrue(tile("All Apps").hasFocus());
    }

    @Test
    public void returningFromBrowserRestoresShortcutFocus() {
        tile("Live Sports").requestFocus();
        tile("Live Sports").performClick();
        controller.pause().resume();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(tile("Live Sports").hasFocus());
    }

    @Test
    public void recreationRestoresExpansionAndFocus() {
        tile("All Apps").performClick();
        shadowOf(Looper.getMainLooper()).idle();
        tile("NordVPN").requestFocus();
        controller.recreate();
        activity = controller.get();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("All Apps, expanded", tile("All Apps").getContentDescription());
        assertTrue(tile("NordVPN").hasFocus());
    }

    @Test
    public void dpadLinksConnectRowsAndClampAtEdges() {
        View streaming = tile("Streaming Sites");
        View sports = tile("Live Sports");
        assertEquals(sports.getId(), streaming.getNextFocusRightId());
        assertEquals(streaming.getId(), sports.getNextFocusLeftId());
        assertEquals(tile("All Apps").getId(), sports.getNextFocusDownId());
        assertEquals(tile("Settings").getId(), tile("All Apps").getNextFocusDownId());
        assertEquals(streaming.getId(), streaming.getNextFocusLeftId());
    }

    @Test
    public void browserLoadsShortcutWithoutChangingPopupSettings() {
        Intent intent = new Intent().setData(Uri.parse("https://fmhy.net/video#live-sports"));
        try (ActivityController<BrowserActivity> browser =
                     Robolectric.buildActivity(BrowserActivity.class, intent).setup()) {
            WebView web = findWebView(browser.get().findViewById(android.R.id.content));
            assertEquals(intent.getDataString(), web.getUrl());
            assertFalse(web.getSettings().supportMultipleWindows());
            assertFalse(web.getSettings().getJavaScriptCanOpenWindowsAutomatically());
            assertTrue(web.getSettings().getJavaScriptEnabled());
            assertFalse(shadowOf(web).getWebChromeClient().onCreateWindow(web, false, true, null));
        }
    }

    @Test
    public void browserToolKeepsExistingHomePage() {
        try (ActivityController<BrowserActivity> browser =
                     Robolectric.buildActivity(BrowserActivity.class).setup()) {
            WebView web = findWebView(browser.get().findViewById(android.R.id.content));
            assertEquals("https://search.brave.com/", web.getUrl());
        }
    }

    private View tile(String label) {
        View found = findTile(activity.findViewById(android.R.id.content), label);
        assertNotNull("Focusable tile: " + label, found);
        return found;
    }

    private View findTile(View view, String label) {
        if (view.isFocusable() && view.getTag() instanceof String && findText(view, label) != null) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findTile(group.getChildAt(index), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView text(String label) {
        return findText(activity.findViewById(android.R.id.content), label);
    }

    private TextView findText(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = findText(group.getChildAt(index), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                WebView found = findWebView(group.getChildAt(index));
                if (found != null) return found;
            }
        }
        return null;
    }
}
