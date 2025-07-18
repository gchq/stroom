package stroom.util.client;

import stroom.util.shared.NullSafe;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.DateTimeFormat.PredefinedFormat;

import java.util.Date;


public class GwtLogUtil {

    // GWT.isProdMode seems to return true when in superdev
    //    private static final GwtLogger GWT_LOGGER = GWT.isProdMode()
//            ? new NoOpGwtLogger()
//            : new GwtLoggerImpl();
    private static final GwtLogger GWT_LOGGER = new GwtLoggerImpl();

    private GwtLogUtil() {
    }

    public static void log(final Class<?> clazz, final String template, final Object... args) {
        GWT_LOGGER.log(clazz, template, args);
    }


    // --------------------------------------------------------------------------------


    private interface GwtLogger {

        void log(final Class<?> clazz,
                 final String template,
                 final Object... args);
    }


    // --------------------------------------------------------------------------------


    private static class NoOpGwtLogger implements GwtLogger {

        @Override
        public void log(final Class<?> clazz, final String template, final Object... args) {
            // no-op
        }
    }


    // --------------------------------------------------------------------------------


    private static class GwtLoggerImpl implements GwtLogger {

        @Override
        public void log(final Class<?> clazz,
                        final String template,
                        final Object... args) {

            final String dateTimeStr = DateTimeFormat.getFormat(PredefinedFormat.ISO_8601)
                    .format(new Date());
            final String prefix = dateTimeStr + " " + clazz.getSimpleName();
            if (NullSafe.isEmptyArray(args)
                || template == null
                || !template.contains("{}")) {
                GWT.log(prefix + " - " + template);
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
                GWT.log(prefix + " - " + msg, throwable);
            }
        }
    }
}

