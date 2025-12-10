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

import stroom.query.api.Column;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class DelimitedTarget implements SearchResultWriter.Target {

    private final OutputStream outputStream;
    private final String delimiter;

    private DelimitedWriter delimitedWriter;

    public DelimitedTarget(final OutputStream outputStream, final String delimiter) {
        this.outputStream = outputStream;
        this.delimiter = delimiter;
    }

    @Override
    public void start() {
        final Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        delimitedWriter = new DelimitedWriter(delimiter, writer);
    }

    @Override
    public void end() throws IOException {
        delimitedWriter.close();
    }

    @Override
    public void startTable(final String tableName) {
        // Do nothing
    }

    @Override
    public void endTable() {
        // Do nothing
    }

    @Override
    public void startLine() {
        // Do nothing
    }

    @Override
    public void endLine() throws IOException {
        delimitedWriter.newLine();
    }

    @Override
    public void writeHeading(final int fieldIndex, final Column column, final String heading) throws IOException {
        delimitedWriter.writeValue(heading);
    }

    @Override
    public void writeValue(final Column column, final String value) throws IOException {
        delimitedWriter.writeValue(value);
    }
}
