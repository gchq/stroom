/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.logging;

import org.slf4j.helpers.MessageFormatter;

import java.util.IllegalFormatException;

/**
 * Build a message from array argument.
 */
public class LoggerUtil {
    public static final String buildMessage(final Object... args) {
        if (args == null) {
            return "";
        }
        IllegalFormatException ilEx = null;
        try {
            if (args[0] != null && args[0] instanceof String) {
                if (args.length > 1) {
                    final Object[] otherArgs = new Object[args.length - 1];
                    System.arraycopy(args, 1, otherArgs, 0, otherArgs.length);
                    return MessageFormatter.format((String) args[0], otherArgs).getMessage();
                } else {
                    return (String) args[0];
                }
            }
        } catch (final IllegalFormatException il) {
            ilEx = il;
        }
        final StringBuilder builder = new StringBuilder();
        if (ilEx != null) {
            builder.append(ilEx.getMessage());
        }
        for (final Object arg : args) {
            if (arg != null) {
                if (builder.length() > 0) {
                    builder.append(" - ");
                }
                builder.append(String.valueOf(arg));
            }
        }
        return builder.toString();
    }
}
