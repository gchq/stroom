/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.client;

import com.google.gwt.core.client.GWT;

import java.util.function.Supplier;

public class Console {

    private static Level level = Level.DEBUG;

    public static void setLevel(final Level level) {
        Console.level = level;
    }

    public static void debug(final String message) {
        log(Level.DEBUG, message);
    }

    public static void debug(final Supplier<String> supplier) {
        log(Level.DEBUG, supplier);
    }

    public static void debug(final String message,
                             final Throwable exception) {
        log(Level.DEBUG, message, exception);
    }

    public static void debug(final Supplier<String> supplier,
                             final Throwable exception) {
        log(Level.DEBUG, supplier, exception);
    }

    public static void info(final String message) {
        log(Level.INFO, message);
    }

    public static void info(final Supplier<String> supplier) {
        log(Level.INFO, supplier);
    }

    public static void info(final String message,
                            final Throwable exception) {
        log(Level.INFO, message, exception);
    }

    public static void info(final Supplier<String> supplier,
                            final Throwable exception) {
        log(Level.INFO, supplier, exception);
    }

    public static void warn(final String message) {
        log(Level.WARN, message);
    }

    public static void warn(final Supplier<String> supplier) {
        log(Level.WARN, supplier);
    }

    public static void warn(final String message,
                            final Throwable exception) {
        log(Level.WARN, message, exception);
    }

    public static void warn(final Supplier<String> supplier,
                            final Throwable exception) {
        log(Level.WARN, supplier, exception);
    }

    public static void error(final String message) {
        log(Level.ERROR, message);
    }

    public static void error(final Supplier<String> supplier) {
        log(Level.ERROR, supplier);
    }

    public static void error(final String message,
                             final Throwable exception) {
        log(Level.ERROR, message, exception);
    }

    public static void error(final Supplier<String> supplier,
                             final Throwable exception) {
        log(Level.ERROR, supplier, exception);
    }

    public static void log(final Level level,
                           final String message) {
        try {
            if (shouldLog(level)) {
                innerLog(level, message);
            }
        } catch (final Exception e) {
            GWT.log(e.getMessage(), e);
        }
    }

    public static void log(final Level level,
                           final Supplier<String> supplier) {
        try {
            if (shouldLog(level)) {
                innerLog(level, supplier.get());
            }
        } catch (final Exception e) {
            GWT.log(e.getMessage(), e);
        }
    }

    public static void log(final Level level,
                           final String message,
                           final Throwable exception) {
        try {
            if (shouldLog(level)) {
                innerLog(level, message, exception);
            }
        } catch (final Exception e) {
            GWT.log(e.getMessage(), e);
        }
    }

    public static void log(final Level level,
                           final Supplier<String> supplier,
                           final Throwable exception) {
        try {
            if (shouldLog(level)) {
                innerLog(level, supplier.get(), exception);
            }
        } catch (final Exception e) {
            GWT.log(e.getMessage(), e);
        }
    }

    private static boolean shouldLog(final Level level) {
        return GWT.isClient() && (!GWT.isProdMode() || Console.level.order <= level.order);
    }

    private static void innerLog(final Level level,
                                 final String message) {
        nativeConsoleLog(level.name() + ": " + message);
    }

    private static void innerLog(final Level level,
                                 final String message,
                                 final Throwable exception) {
        String stack = "";
        if (exception != null) {
            try {
                stack = getBackingErrorStack(exception);
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }

        nativeConsoleLog(level.name() + ": " + message + "\n" + stack);
    }

    public enum Level {
        DEBUG(0),
        INFO(1),
        WARN(2),
        ERROR(3);

        private int order;

        Level(final int order) {
            this.order = order;
        }

        public int getOrder() {
            return order;
        }
    }

    private static native void nativeConsoleLog(String s)
        /*-{ console.log( s ); }-*/;

    @SuppressWarnings("unusable-by-js")
    private static native String getBackingErrorStack(Throwable t)
        /*-{
        var backingError = t.@Throwable::backingJsObject;

        // Converts CollectorLegacy (IE8/IE9/Safari5) function stack to something readable.
        function stringify(fnStack) {
          if (!fnStack || fnStack.length == 0) {
            return "";
          }
          return "\t" + fnStack.join("\n\t");
        }

        return backingError && (backingError.stack || stringify(t["fnStack"]));
      }-*/;
}
