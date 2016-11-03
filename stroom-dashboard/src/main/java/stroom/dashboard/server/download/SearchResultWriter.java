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

package stroom.dashboard.server.download;

import java.io.IOException;
import java.util.List;

import stroom.dashboard.expression.Generator;
import stroom.dashboard.server.SampleGenerator;
import stroom.query.Item;
import stroom.query.Items;
import stroom.query.ResultStore;
import stroom.query.shared.Field;

public class SearchResultWriter {
    public interface Target {
        void start() throws IOException;

        void end() throws IOException;

        void startLine() throws IOException;

        void endLine() throws IOException;

        void writeHeading(Field field, String heading) throws IOException;

        void writeValue(Field field, Object value) throws IOException;
    }

    private final ResultStore resultStore;
    private final List<Field> fields;
    private final SampleGenerator sampleGenerator;

    public SearchResultWriter(final ResultStore resultStore, final List<Field> fields,
            final SampleGenerator sampleGenerator) {
        this.resultStore = resultStore;
        this.fields = fields;
        this.sampleGenerator = sampleGenerator;
    }

    public void write(final Target target) throws IOException {
        // Start writing.
        target.start();

        // Write heading.
        writeHeadings(fields, target);

        // Write content.
        writeContent(resultStore, fields, sampleGenerator, target);

        // End writing.
        target.end();
    }

    private void writeHeadings(final List<Field> fields, final Target target) throws IOException {
        target.startLine();
        for (int i = 0; i < fields.size(); i++) {
            final Field field = fields.get(i);
            if (field.isVisible()) {
                target.writeHeading(field, field.getName());
            }
        }
        target.endLine();
    }

    private void writeContent(final ResultStore resultStore, final List<Field> fields,
            final SampleGenerator sampleGenerator, final Target target) throws IOException {
        final Items<Item> items = resultStore.getChildMap().get(null);
        if (items != null) {
            for (final Item item : items) {
                if (sampleGenerator.includeResult()) {
                    target.startLine();
                    for (int i = 0; i < fields.size(); i++) {
                        final Field field = fields.get(i);
                        if (field.isVisible()) {
                            Object val = null;

                            if (item.getValues().length > i) {
                                final Object o = item.getValues()[i];
                                val = o;

                                if (o != null) {
                                    // Convert all values into fully resolved
                                    // objects evaluating functions where
                                    // necessary.
                                    if (o instanceof Generator) {
                                        final Generator generator = (Generator) o;
                                        val = generator.eval();
                                    }
                                }
                            }

                            target.writeValue(field, val);
                        }
                    }
                    target.endLine();
                }
            }
        }
    }
}
