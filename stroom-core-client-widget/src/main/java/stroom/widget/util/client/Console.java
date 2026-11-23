/*
 * Copyright 2022 Crown Copyright
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

package stroom.widget.util.client;

import com.google.gwt.core.client.GWT;

import java.util.function.Supplier;

/// The browser console's log, the one way Stroom's client code writes to it (checkstyle refuses
/// `GWT.log`), so that it is turned up or down in one place. Super Dev Mode logs everything, and any
/// other build warnings and errors; the [#LEVEL_PARAMETER] URL parameter changes it, e.g.
/// `?consoleLevel=debug`. Anyone can turn it up, so never log a secret (e.g. a token or a request's
/// body) at any level.
public class Console {

    /// The URL parameter giving the level to log at. Not `logLevel`, which GWT's own logging reads
    /// and fails on if it isn't one of its own levels.
    public static final String LEVEL_PARAMETER = "consoleLevel";

    private static Level level = defaultLevel(isSuperDevMode());

    /// Sets what is logged.
    ///
    /// @param level The least severe level logged.
    public static void setLevel(final Level level) {
        Console.level = level;
    }

    /// Sets what is logged from the [#LEVEL_PARAMETER] URL parameter, leaving it as it is if the
    /// parameter isn't given or isn't a level.
    ///
    /// @param name The parameter's value, e.g. `debug`, or null if not given.
    public static void setLevelFromParameter(final String name) {
        final Level parsed = Level.parse(name);
        if (parsed != null) {
            setLevel(parsed);
        }
    }

    // What is logged unless the URL says otherwise
    static Level defaultLevel(final boolean superDevMode) {
        return superDevMode
                ? Level.DEBUG
                : Level.WARN;
    }

    // Whether this was compiled by Super Dev Mode, from the property the compiler sets. Not
    // `GWT.isProdMode()`, which is true of anything compiled to JavaScript
    private static boolean isSuperDevMode() {
        return "on".equals(System.getProperty("superdevmode", "off"));
    }

    static Level getLevel() {
        return level;
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
            nativeConsoleLog(Level.ERROR.name(), "ERROR: Unable to log: " + e.getMessage());
        }
    }

    public static void log(final Level level,
                           final Supplier<String> supplier) {
        try {
            if (shouldLog(level)) {
                innerLog(level, supplier.get());
            }
        } catch (final Exception e) {
            nativeConsoleLog(Level.ERROR.name(), "ERROR: Unable to log: " + e.getMessage());
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
            nativeConsoleLog(Level.ERROR.name(), "ERROR: Unable to log: " + e.getMessage());
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
            nativeConsoleLog(Level.ERROR.name(), "ERROR: Unable to log: " + e.getMessage());
        }
    }

    private static boolean shouldLog(final Level level) {
        return GWT.isClient() && level.isLoggedAt(Console.level);
    }

    private static void innerLog(final Level level,
                                 final String message) {
        nativeConsoleLog(level.name(), level.name() + ": " + message);
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

        nativeConsoleLog(level.name(), level.name() + ": " + message + "\n" + stack);
    }

    // By the console's own levels, so that its filters (e.g. Errors) work. Debug is `console.log`, as
    // browsers hide `console.debug` unless asked
    private static native void nativeConsoleLog(String level, String s)
        /*-{
        switch (level) {
            case "ERROR":
                console.error(s);
                break;
            case "WARN":
                console.warn(s);
                break;
            case "INFO":
                console.info(s);
                break;
            default:
                console.log(s);
        }
    }-*/;

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


    // --------------------------------------------------------------------------------


    /// How severe what is logged is, least first.
    public enum Level {
        DEBUG(0),
        INFO(1),
        WARN(2),
        ERROR(3);

        private final int order;

        Level(final int order) {
            this.order = order;
        }

        /// @return Where the level is among the others, the least severe first.
        public int getOrder() {
            return order;
        }

        /// @param threshold The least severe level logged.
        /// @return Whether something of this level is logged.
        public boolean isLoggedAt(final Level threshold) {
            return threshold.order <= order;
        }

        /// @param name A level's name in any case, e.g. `debug`, or null.
        /// @return The level, or null if there is none of that name.
        public static Level parse(final String name) {
            if (name != null) {
                for (final Level level : values()) {
                    if (level.name().equalsIgnoreCase(name.trim())) {
                        return level;
                    }
                }
            }
            return null;
        }
    }
}
