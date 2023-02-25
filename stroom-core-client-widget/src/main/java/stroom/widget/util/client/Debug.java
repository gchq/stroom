package stroom.widget.util.client;

import com.google.gwt.core.client.GWT;

public class Debug {

    private static boolean isEnabled_ = false;

    public static void enable() {
        isEnabled_ = true;
    }

    public static void setEnabled(final boolean isEnabled) {
        isEnabled_ = isEnabled;
    }

    public static void log(final String s) {
        GWT.log(s);
        if (isEnabled_) {
            nativeConsoleLog(s);
        }
    }

    public static void log(final String s, final Throwable e) {
        GWT.log(s, e);
        if (isEnabled_) {
            nativeConsoleLog(s);
        }
    }

    private static native void nativeConsoleLog(String s)
        /*-{ console.log( s ); }-*/;
}
