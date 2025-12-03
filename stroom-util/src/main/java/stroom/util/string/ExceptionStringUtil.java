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
