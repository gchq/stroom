package stroom.util.client;

import stroom.util.shared.NullSafe;

import com.google.gwt.core.shared.GWT;


public class GwtLogUtil {

    // GWT.isProdMode seems to return true when in superdev
    //    private static final GwtLogger GWT_LOGGER = GWT.isProdMode()
//            ? new NoOpGwtLogger()
//            : new GwtLoggerImpl();
    private static final GwtLogger GWT_LOGGER = new GwtLoggerImpl();

    private GwtLogUtil() {
    }

    public static void log(final String template, final Object... args) {
        GWT_LOGGER.log(template, args);
    }


    // --------------------------------------------------------------------------------


    private interface GwtLogger {

        void log(final String template, Object... args);
    }


    // --------------------------------------------------------------------------------


    private static class NoOpGwtLogger implements GwtLogger {

        @Override
        public void log(final String template, final Object... args) {
            // no-op
        }
    }


    // --------------------------------------------------------------------------------


    private static class GwtLoggerImpl implements GwtLogger {

        @Override
        public void log(final String template, final Object... args) {
            if (NullSafe.isEmptyArray(args)
                || template == null
                || !template.contains("{}")) {
                GWT.log(template);
            } else {
                String msg = template;
                Throwable throwable = null;
                for (int i = 0, argsLength = args.length; i < argsLength; i++) {
                    final Object arg = args[i];
                    if (i == argsLength - 1 && arg instanceof final Throwable t) {
                        throwable = t;
                    } else if (msg.contains("{}")) {
                        msg = msg.replaceFirst("\\{}", NullSafe.toString(arg));
                    }
                }
                GWT.log(msg, throwable);
            }
        }
    }
}

