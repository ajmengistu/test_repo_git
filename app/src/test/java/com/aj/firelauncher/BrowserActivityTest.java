package com.aj.firelauncher;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.AlertDialog;
import android.net.Uri;
import android.content.Intent;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
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
import org.robolectric.shadows.ShadowAlertDialog;
import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w960dp-h540dp-land-mdpi")
public class BrowserActivityTest {
    private ActivityController<BrowserActivity> controller;
    private BrowserActivity activity;
    private View root;
    private WebView web;
    @Before public void start(){
        controller=Robolectric.buildActivity(BrowserActivity.class,new Intent().setData(Uri.parse("https://www.imdb.com/"))).setup().visible();
        activity=controller.get();root=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
        web=(WebView)root.findViewWithTag("web");
        root.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.EXACTLY));
        root.layout(0,0,960,540);
    }
    @After public void stop(){if(controller!=null)controller.pause().stop().destroy();}
    private void key(int code){activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));shadowOf(Looper.getMainLooper()).idle();}
    private View target(String tag){View v=root.findViewWithTag(tag);assertNotNull(tag,v);return v;}

    @Test public void startsWithCursorAndFittedDesktopWebView(){
        assertEquals("https://www.imdb.com/",web.getUrl());
        assertEquals(View.GONE,target("browser-toolbar").getVisibility());
        assertEquals(View.VISIBLE,target("cursor").getVisibility());
        assertTrue(web.getSettings().getUseWideViewPort());assertTrue(web.getSettings().getLoadWithOverviewMode());
        assertTrue(web.getSettings().getUserAgentString().contains("X11; Linux x86_64"));
        assertFalse(web.getSettings().getUserAgentString().contains("OPR/"));
        assertEquals(100,web.getSettings().getTextZoom());
    }
    @Test public void dpadMovesCursorAndAcceleratesUntilRelease(){
        BrowserCursor cursor=(BrowserCursor)target("cursor");float before=cursor.cursorX();
        int[] clicks={0},scrolls={0};
        web.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP)clicks[0]++;return false;});
        web.setOnGenericMotionListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_SCROLL)scrolls[0]++;return false;});
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));
        float after=cursor.cursorX();assertTrue(after>before);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));assertEquals(after,cursor.cursorX(),0.01f);
        assertTrue(BrowserCursor.speedForHold(900)>BrowserCursor.speedForHold(0));
        key(KeyEvent.KEYCODE_DPAD_CENTER);assertEquals(1,clicks[0]);
        cursor.setScrollMode(true);
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN));
        assertTrue(scrolls[0]>0);
    }
    @Test public void menuAndBackToggleToolbarWithoutLeavingPage(){
        key(KeyEvent.KEYCODE_MENU);assertEquals(View.VISIBLE,target("browser-toolbar").getVisibility());
        assertEquals(View.GONE,target("cursor").getVisibility());
        target("address").dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));
        assertTrue(target("go").hasFocus());
        target("blocking").performClick();
        key(KeyEvent.KEYCODE_MENU);
        assertEquals("Block: Off",((TextView)target("blocking")).getText().toString());
        activity.onBackPressed();assertEquals(View.GONE,target("browser-toolbar").getVisibility());assertFalse(activity.isFinishing());
    }
    @Test public void siteZoomAndFontAreIndependentAndPersist(){
        key(KeyEvent.KEYCODE_MENU);target("zoom").performClick();
        AlertDialog picker=ShadowAlertDialog.getLatestAlertDialog();picker.getListView().performItemClick(null,4,4);
        BrowserStore prefs=new BrowserStore(activity);assertEquals(110,prefs.zoom("imdb.com"));assertEquals(100,prefs.font("imdb.com"));
        prefs.setFont("imdb.com",125);prefs.setDesktop("imdb.com",false);
        controller.recreate();activity=controller.get();root=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);web=(WebView)root.findViewWithTag("web");
        assertEquals(125,web.getSettings().getTextZoom());assertFalse(web.getSettings().getUserAgentString().contains("X11; Linux x86_64"));
        assertEquals(90,prefs.zoom("example.com"));
    }
    @Test public void storesBookmarksAndBoundedHistoryLocally(){
        BrowserStore prefs=new BrowserStore(activity);prefs.remember("bookmarks","https://imdb.com/","IMDb");
        prefs.remember("bookmarks","https://imdb.com/","IMDb updated");assertEquals(1,prefs.entries("bookmarks").length());
        for(int i=0;i<120;i++)prefs.remember("history","https://example.com/"+i,"Page "+i);
        assertEquals(100,prefs.entries("history").length());prefs.removeBookmark("https://imdb.com/");assertEquals(0,prefs.entries("bookmarks").length());
    }
    @Test public void fullscreenBackRestoresBrowserAndCursor(){
        View video=new View(activity);boolean[] hidden={false};
        shadowOf(web).getWebChromeClient().onShowCustomView(video,()->hidden[0]=true);
        assertEquals(View.INVISIBLE,web.getVisibility());activity.onBackPressed();
        assertTrue(hidden[0]);assertEquals(View.VISIBLE,web.getVisibility());assertEquals(View.VISIBLE,target("cursor").getVisibility());
    }
    @Test public void browserToolbarFitsViewport(){
        key(KeyEvent.KEYCODE_MENU);
        root.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.EXACTLY));
        root.layout(0,0,960,540);
        checkBounds(root,0,0);
    }
    private void checkBounds(View view,int x,int y){
        if(view.getVisibility()==View.GONE)return;
        int right=x+view.getWidth(),bottom=y+view.getHeight();
        assertTrue("right "+view.getTag(),right<=960);assertTrue("bottom "+view.getTag(),bottom<=540);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            View child=((ViewGroup)view).getChildAt(i);checkBounds(child,x+child.getLeft(),y+child.getTop());
        }
    }
}
