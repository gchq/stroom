package stroom.util.client;

import com.google.gwt.core.client.GWT;

public class Console {

    private static boolean enabled = true;

    public static void enable() {
        enabled = true;
    }

    public static void setEnabled(final boolean enabled) {
        Console.enabled = enabled;
    }

    public static void log(final String s) {
        if (enabled) {
            nativeConsoleLog(s);
        } else {
            GWT.log(s);
        }
    }

    public static void log(final String s, final Throwable e) {
        if (enabled) {
            nativeConsoleLog(s);
        } else {
            GWT.log(s, e);
        }
    }

    private static native void nativeConsoleLog(String s)
        /*-{ console.log( s ); }-*/;
}
