package com.aj.firelauncher;

/** JNI boundary only. No downloaded code, network client, or native browser engine. */
final class NativeBlocker {
    static { System.loadLibrary("ajblock"); }
    static native boolean initialize(String filters);
    static native boolean matches(String url, String source, String type, String method);
    static native String selectors(String url, String tokens);
    private NativeBlocker() { }
}
