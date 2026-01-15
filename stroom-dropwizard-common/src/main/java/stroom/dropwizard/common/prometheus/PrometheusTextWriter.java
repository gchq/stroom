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

/*
 * This file was copied from https://github.com/dhatim/dropwizard-prometheus
 * at commit a674a1696a67186823a464383484809738665282 (v4.0.1-2)
 * and modified to work within the Stroom code base. All subsequent
 * modifications from the original are also made under the Apache 2.0 licence
 * and are subject to Crown Copyright.
 */

/*
 * Copyright 2025 github.com/dhatim
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

package stroom.dropwizard.common.prometheus;

import stroom.util.shared.NullSafe;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Map;

class PrometheusTextWriter extends FilterWriter {

    public PrometheusTextWriter(final Writer out) {
        super(out);
    }

    public void writeHelp(final String name, final String value) {
        try {
            write("# HELP ");
            write(name);
            write(' ');
            writeEscapedHelp(value);
            write('\n');
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeType(final String name, final PrometheusMetricType type) {
        try {
            write("# TYPE ");
            write(name);
            write(' ');
            write(type.getText());
            write('\n');
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeLongSample(final String name,
                                final long value) {
        writeLongSample(name, null, value);
    }

    public void writeLongSample(final String name,
                                final Map<String, String> labels,
                                final long value) {
        try {
            write(name);
            writeLabels(labels);
            write(' ');
            write(Long.toString(value));
            write('\n');
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeDoubleSample(final String name,
                                  final double value) {
        writeDoubleSample(name, null, value);
    }

    public void writeDoubleSample(final String name,
                                  final Map<String, String> labels,
                                  final double value) {
        try {
            write(name);
            writeLabels(labels);
            write(' ');
            write(doubleToGoString(value));
            write('\n');
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeLabels(final Map<String, String> labels) throws IOException {
        if (NullSafe.hasEntries(labels)) {
            try {
                write('{');
                boolean isFirst = true;
                for (final Map.Entry<String, String> entry : labels.entrySet()) {
                    if (!isFirst) {
                        write(",");
                    }
                    write(entry.getKey());
                    write("=\"");
                    writeEscapedLabelValue(entry.getValue());
                    write("\"");
                    isFirst = false;
                }
                write('}');
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void writeEscapedHelp(final String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '\\':
                    append("\\\\");
                    break;
                case '\n':
                    append("\\n");
                    break;
                default:
                    append(c);
            }
        }
    }

    private void writeEscapedLabelValue(final String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '\\':
                    append("\\\\");
                    break;
                case '\"':
                    append("\\\"");
                    break;
                case '\n':
                    append("\\n");
                    break;
                default:
                    append(c);
            }
        }
    }

    private static String doubleToGoString(final double d) {
        if (d == Double.POSITIVE_INFINITY) {
            return "+Inf";
        }
        if (d == Double.NEGATIVE_INFINITY) {
            return "-Inf";
        }
        if (Double.isNaN(d)) {
            return "NaN";
        }
        return Double.toString(d);
    }
}
