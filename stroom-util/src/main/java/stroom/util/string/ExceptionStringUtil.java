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

    /**
     * Useful for exceptions whose message is useless without the name of the exception class.
     * @return The message with the class simple name in brackets, e.g. "some message (RuntimeException)"
     */
    private static String getMessageAndClassName(final Throwable throwable) {
        LOGGER.debug(throwable::getMessage, throwable);
        if (throwable.getMessage() == null) {
            return "(" + throwable.getClass().getName() + ")";
        }
        return throwable.getMessage() + " (" + throwable.getClass().getName() + ")";
    }

    private static String getStackTrace(final Throwable throwable) {
        LOGGER.debug(throwable::getMessage, throwable);
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    private static String getDetail(final Throwable throwable) {
        LOGGER.debug(throwable::getMessage, throwable);
        return getMessageAndClassName(throwable) + "\n" + getStackTrace(throwable);
    }
}
