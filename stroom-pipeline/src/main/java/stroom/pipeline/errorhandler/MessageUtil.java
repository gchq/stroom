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

package stroom.pipeline.errorhandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.regex.Pattern;

public final class MessageUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageUtil.class);

    private static final Pattern PATTERN = Pattern.compile("[\n\t]+");
    private static final String SPACE = " ";
    private static final String MORE = "...";
    private static final String UNKNOWN_ERROR = "UNKNOWN ERROR";

    private MessageUtil() {
        // Utility class.
    }

    public static String getMessage(final String message, final Throwable t) {
        // If there is a message then just return it.
        if (message != null && message.trim().length() > 0) {
            return message;
        }

        // If there is no message then try and return a partial stack trace.
        return getMessage(t);
    }

    public static String getMessage(final Throwable t) {
        final String message = getFirstMessage(t);
        // If there is a message then just return it.
        if (message != null && message.trim().length() > 0) {
            return message;
        }

        // If there is no message then try and return a partial stack trace.
        if (t != null) {
            String trace = null;

            try {
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                t.printStackTrace(new PrintStream(outputStream));
                outputStream.flush();
                outputStream.close();
                trace = new String(outputStream.toByteArray());
            } catch (final IOException e) {
                LOGGER.error("Unable to flush and close output stream!", e);
            }

            trace = PATTERN.matcher(trace).replaceAll(SPACE);
            if (trace.length() > 1000) {
                trace = trace.substring(0, 1000) + MORE;
            }

            return trace;
        }

        // We really don't know why this was called.
        try {
            throw new RuntimeException(UNKNOWN_ERROR);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return UNKNOWN_ERROR;
    }

    private static String getFirstMessage(final Throwable e) {
        if (e == null) {
            return null;
        }

        if (e.getMessage() != null) {
            return e.getMessage();
        }

        return getFirstMessage(e.getCause());
    }
}
