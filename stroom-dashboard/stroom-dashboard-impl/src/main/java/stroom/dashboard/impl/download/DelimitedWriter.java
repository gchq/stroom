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

package stroom.dashboard.impl.download;

import java.io.IOException;
import java.io.Writer;
import java.util.regex.Pattern;

public class DelimitedWriter implements AutoCloseable {

    private static final String NEW_LINE = "\n";
    private static final String QUOTE = "\"";
    private static final String DOUBLE_QUOTE = "\"\"";
    private static final Pattern QUOTE_PATTERN = Pattern.compile(QUOTE);

    private final String delimiter;
    private final Writer writer;
    private boolean writtenValue;
    private boolean writtenAnything;

    public DelimitedWriter(final String delimiter, final Writer writer) {
        this.delimiter = delimiter;
        this.writer = writer;
    }

    public void newLine() throws IOException {
        writtenValue = false;
    }

    public void writeValue(final String value) throws IOException {
        if (writtenValue) {
            // If we have output a value on this line then output a delimiter.
            writer.write(delimiter);
        } else if (writtenAnything) {
            // If we haven't output any value since new line was called but have
            // written some data then write a new line.
            writer.write(NEW_LINE);
        }

        // Write the value making sure we escape double quotes with
        // double-double quotes.
        writer.write(QUOTE);
        if (value != null) {
            final String escapedVal = QUOTE_PATTERN.matcher(value).replaceAll(DOUBLE_QUOTE);
            writer.write(escapedVal);
        }
        writer.write(QUOTE);

        // Update the flags as we have just written a value and we have
        // definitely written some output.
        writtenValue = true;
        writtenAnything = true;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
