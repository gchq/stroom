/*
 * Copyright 2026 Crown Copyright
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

import stroom.dashboard.impl.SampleGenerator;
import stroom.query.api.Column;
import stroom.query.api.Row;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSearchResultWriter {

    private static final Column VISIBLE_A = Column.builder().id("a").name("A").visible(true).build();
    private static final Column HIDDEN_B = Column.builder().id("b").name("B").visible(false).build();
    private static final Column VISIBLE_C = Column.builder().id("c").name("C").visible(true).build();

    @Test
    void writesOnlyVisibleHeadings() throws IOException {
        final RecordingTarget target = write(List.of(VISIBLE_A, HIDDEN_B, VISIBLE_C));
        assertThat(target.headings).containsExactly("A", "C");
    }

    /**
     * Row values are produced for every column, visible or not, so the writer must index into them by the position
     * of the column in the full list. Hiding a middle column must not shift the values that follow it.
     */
    @Test
    void writesTheValuesThatBelongToTheVisibleColumns() throws IOException {
        final RecordingTarget target = write(List.of(VISIBLE_A, HIDDEN_B, VISIBLE_C));
        assertThat(target.values).containsExactly("valueA", "valueC");
    }

    /**
     * The heading index is used by {@link ExcelTarget} to track a column for auto-sizing, and must therefore match
     * the cell the heading is actually written to, not the column's position in the unfiltered list.
     */
    @Test
    void numbersHeadingsByOutputPositionNotByColumnPosition() throws IOException {
        final RecordingTarget target = write(List.of(HIDDEN_B, VISIBLE_A, VISIBLE_C));
        assertThat(target.headings).containsExactly("A", "C");
        assertThat(target.headingIndexes).containsExactly(0, 1);
    }

    @Test
    void countsRowsThatWereWritten() throws IOException {
        final SearchResultWriter writer = new SearchResultWriter(new SampleGenerator(false, 100),
                new RecordingTarget());
        writer.columns(List.of(VISIBLE_A, HIDDEN_B, VISIBLE_C));
        writer.addRow(row());
        writer.addRow(row());
        assertThat(writer.getRowCount()).isEqualTo(2);
    }

    private static RecordingTarget write(final List<Column> columns) throws IOException {
        final RecordingTarget target = new RecordingTarget();
        final SearchResultWriter writer = new SearchResultWriter(new SampleGenerator(false, 100), target);
        writer.columns(columns);
        writer.addRow(row());
        return target;
    }

    /**
     * @return A row with one value per column, in the order the columns were declared.
     */
    private static Row row() {
        return Row.builder()
                .values(List.of("valueA", "valueB", "valueC"))
                .depth(0)
                .build();
    }


    // --------------------------------------------------------------------------------


    private static class RecordingTarget implements SearchResultWriter.Target {

        private final List<String> headings = new ArrayList<>();
        private final List<Integer> headingIndexes = new ArrayList<>();
        private final List<String> values = new ArrayList<>();

        @Override
        public void start() {
        }

        @Override
        public void end() {
        }

        @Override
        public void startTable(final String tableName) {
        }

        @Override
        public void endTable() {
        }

        @Override
        public void startLine() {
        }

        @Override
        public void endLine() {
        }

        @Override
        public void writeHeading(final int fieldIndex, final Column column, final String heading) {
            headingIndexes.add(fieldIndex);
            headings.add(heading);
        }

        @Override
        public void writeValue(final Column column, final String value) {
            values.add(value);
        }
    }
}
