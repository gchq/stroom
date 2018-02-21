/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.server.download;

import stroom.dashboard.server.format.FieldFormatter;
import stroom.dashboard.shared.Field;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class DelimitedTarget implements SearchResultWriter.Target {
    private final FieldFormatter fieldFormatter;
    private final OutputStream outputStream;
    private final String delimiter;

    private DelimitedWriter delimitedWriter;

    public DelimitedTarget(final FieldFormatter fieldFormatter, final OutputStream outputStream, final String delimiter)
            throws IOException {
        this.fieldFormatter = fieldFormatter;
        this.outputStream = outputStream;
        this.delimiter = delimiter;
    }

    @Override
    public void start() throws IOException {
        final Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        delimitedWriter = new DelimitedWriter(delimiter, writer);
    }

    @Override
    public void end() throws IOException {
        delimitedWriter.close();
    }

    @Override
    public void startLine() throws IOException {
        // Do nothing
    }

    @Override
    public void endLine() throws IOException {
        delimitedWriter.newLine();
    }

    @Override
    public void writeHeading(final Field field, final String heading) throws IOException {
        delimitedWriter.writeValue(heading);
    }

    @Override
    public void writeValue(final Field field, final Object value) throws IOException {
        final String formatted = fieldFormatter.format(field, value);
        delimitedWriter.writeValue(formatted);
    }
}
