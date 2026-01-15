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

package stroom.headless;

import stroom.pipeline.ErrorWriter;
import stroom.util.shared.ElementId;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.io.Writer;

public class CliErrorWriter implements ErrorWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CliErrorWriter.class);

    private static final String NEW_LINE = "\n";
    private static final String OPEN_BRACKET = "[";
    private static final String CLOSE_BRACKET = "] ";
    private static final String SPACE = " ";
    private static final String COLON = ":";

    private final Writer writer;

    CliErrorWriter(final Writer writer) {
        this.writer = writer;
    }

    @Override
    public void log(final Severity severity, final Location location, final ElementId elementId, final String message) {
        if (message != null && writer != null) {
            final StringBuilder sb = new StringBuilder();
            sb.append(OPEN_BRACKET);
            if (location != null) {
                sb.append(location);
                sb.append(SPACE);
            }
            sb.append(elementId);
            sb.append(CLOSE_BRACKET);
            sb.append(severity.getDisplayValue());
            sb.append(COLON);
            sb.append(SPACE);

            // We have a message so print it without new lines.
            final char[] chars = message.toCharArray();
            for (final char c : chars) {
                switch (c) {
                    case '\n':
                        sb.append(' ');
                        break;
                    case '\r':
                        break;
                    default:
                        sb.append(c);
                        break;
                }
            }

            // Print a new line.
            sb.append(NEW_LINE);

            try {
                writer.write(sb.toString());
            } catch (final IOException e) {
                LOGGER.error(MarkerFactory.getMarker("FATAL"), "Unable to write to error stream", e);
            }
        }
    }
}
