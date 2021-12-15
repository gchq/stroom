package stroom.util.string;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionStringUtil {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExceptionStringUtil.class);

    public static String getMessage(final Throwable throwable) {
        LOGGER.debug(throwable::getMessage, throwable);
        if (throwable.getMessage() == null) {
            return throwable.getClass().getName();
        }
        return throwable.getMessage();
    }

    public static String getMessageAndClassName(final Throwable throwable) {
        LOGGER.debug(throwable::getMessage, throwable);
        if (throwable.getMessage() == null) {
            return "(" + throwable.getClass().getName() + ")";
        }
        return throwable.getMessage() + " (" + throwable.getClass().getName() + ")";
    }

    public static String getStackTrace(final Throwable throwable) {
        LOGGER.debug(throwable::getMessage, throwable);
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    public static String getDetail(final Throwable throwable) {
        LOGGER.debug(throwable::getMessage, throwable);
        return getMessageAndClassName(throwable) + "\n" + getStackTrace(throwable);
    }
}
