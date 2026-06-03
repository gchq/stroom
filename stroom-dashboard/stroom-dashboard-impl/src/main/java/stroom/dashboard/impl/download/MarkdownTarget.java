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

public class MarkdownTarget implements SearchResultWriter.Target {

    private final OutputStream outputStream;

    private Writer writer;
    private int headingCount;
    private boolean inHeadings;

    public MarkdownTarget(final OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public void start() {
        writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    }

    @Override
    public void end() throws IOException {
        writer.flush();
        writer.close();
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
        headingCount = 0;
    }

    @Override
    public void endLine() throws IOException {
        writer.write("|\n");
        if (inHeadings) {
            inHeadings = false;
            // Write the separator row.
            for (int i = 0; i < headingCount; i++) {
                writer.write("| --- ");
            }
            writer.write("|\n");
        }
    }

    @Override
    public void writeHeading(final int fieldIndex, final Column column, final String heading) throws IOException {
        inHeadings = true;
        headingCount++;
        writer.write("| ");
        writer.write(heading != null
                ? heading.replace("|", "\\|")
                : "");
        writer.write(' ');
    }

    @Override
    public void writeValue(final Column column, final String value) throws IOException {
        writer.write("| ");
        if (value != null) {
            writer.write(value.replace("|", "\\|").replace("\n", " "));
        }
        writer.write(' ');
    }
}
