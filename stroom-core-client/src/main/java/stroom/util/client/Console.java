package stroom.util.client;

import com.google.gwt.core.client.GWT;

import java.util.function.Supplier;

public class Console {

    private static boolean enabled = true;

    public static void enable() {
        enabled = true;
    }

    public static void setEnabled(final boolean enabled) {
        Console.enabled = enabled;
    }

    public static void log(final Supplier<String> supplier) {
        try {
            if (enabled) {
                nativeConsoleLog(supplier.get());
            } else {
                GWT.log(supplier.get());
            }
        } catch (final Exception e) {
            GWT.log(e.getMessage(), e);
        }
    }

    public static void log(final Supplier<String> supplier, final Throwable exception) {
        try {
            if (enabled) {
                nativeConsoleLog(supplier.get());
            } else {
                GWT.log(supplier.get(), exception);
            }
        } catch (final Exception e) {
            GWT.log(e.getMessage(), e);
        }
    }

    private static native void nativeConsoleLog(String s)
        /*-{ console.log( s ); }-*/;
}
