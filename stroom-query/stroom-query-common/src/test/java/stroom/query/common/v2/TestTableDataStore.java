/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.Sort;
import stroom.query.api.v2.Sort.SortDirection;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.shared.v2.ParamUtil;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class TestTableDataStore {
    private final Sizes defaultMaxResultsSizes = Sizes.create(50);
    private final Sizes storeSize = Sizes.create(100);

    @Test
    void basicTest() {
        final FormatterFactory formatterFactory = new FormatterFactory(null);
        final FieldFormatter fieldFormatter = new FieldFormatter(formatterFactory);

        final TableSettings tableSettings = new TableSettings.Builder()
                .addFields(new Field.Builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamUtil.makeParam("Text"))
                        .format(Format.Type.TEXT)
                        .build())
                .build();

        final TableDataStore tableDataStore = create(tableSettings);

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + i;
            final Val[] values = new Val[1];
            values[0] = ValString.create(text);
            tableDataStore.add(values);
        }

        final Data data = tableDataStore.getData();

        // Make sure we only get 50 results.
        final ResultRequest tableResultRequest = new ResultRequest(
                "componentX",
                tableSettings, new OffsetRange(0, 3000));
        final TableResultCreator tableComponentResultCreator = new TableResultCreator(
                fieldFormatter,
                defaultMaxResultsSizes);
        final TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                data,
                tableResultRequest);
        assertThat(searchResult.getTotalResults().intValue()).isEqualTo(50);
    }

    @Test
    void sortedTextTest() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

        final TableSettings tableSettings = new TableSettings.Builder()
                .addFields(new Field.Builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamUtil.makeParam("Text"))
                        .sort(sort)
                        .build())
                .build();

        final TableDataStore tableDataStore = create(tableSettings);

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            final Val[] values = new Val[1];
            values[0] = ValString.create(text);
            tableDataStore.add(values);
        }

        final Data data = tableDataStore.getData();

        final ResultRequest tableResultRequest = new ResultRequest("componentX", tableSettings, new OffsetRange(0, 3000));
        checkResults(data, tableResultRequest, 0);
    }

    @Test
    void sortedNumberTest() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

        final TableSettings tableSettings = new TableSettings.Builder()
                .addFields(new Field.Builder()
                        .id("Number")
                        .name("Number")
                        .expression(ParamUtil.makeParam("Number"))
                        .sort(sort)
                        .build())
                .build();

        final TableDataStore tableDataStore = create(tableSettings);

        for (int i = 0; i < 3000; i++) {
            final String text = String.valueOf((int) (Math.random() * 100));
            final Val[] values = new Val[1];
            values[0] = ValString.create(text);
            tableDataStore.add(values);
        }

        final Data data = tableDataStore.getData();

        final ResultRequest tableResultRequest = new ResultRequest("componentX", tableSettings, new OffsetRange(0, 3000));
        checkResults(data, tableResultRequest, 0);
    }

    @Test
    void sortedCountedTextTest1() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

//        final DataSourceFieldsMap dataSourceFieldsMap = new DataSourceFieldsMap();

        final Field count = new Field.Builder()
                .id("Count")
                .name("Count")
                .expression("count()")
                .sort(sort)
                .build();

//        final IndexField indexField = new IndexField();
//        indexField.setFieldName("Text");
//        indexField.setFieldType(IndexFieldType.FIELD);
//        dataSourceFieldsMap.put(indexField);

        final TableSettings tableSettings = new TableSettings.Builder()
                .addFields(new Field.Builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamUtil.makeParam("Text"))
                        .group(0)
                        .build())
                .build();

        final TableDataStore tableDataStore = create(tableSettings);

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            final Val[] values = new Val[2];
            values[1] = ValString.create(text);
            tableDataStore.add(values);
        }

        final Data data = tableDataStore.getData();

        final ResultRequest tableResultRequest = new ResultRequest("componentX", tableSettings, new OffsetRange(0, 3000));
        checkResults(data, tableResultRequest, 0);
    }

    @Test
    void sortedCountedTextTest2() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

//        final DataSourceFieldsMap dataSourceFieldsMap = new DataSourceFieldsMap();


//        final IndexField indexField = new IndexField();
//        indexField.setFieldName("Text");
//        indexField.setFieldType(IndexFieldType.FIELD);
//        dataSourceFieldsMap.put(indexField);

        final TableSettings tableSettings = new TableSettings.Builder()
                .addFields(new Field.Builder()
                        .id("Count")
                        .name("Count")
                        .expression("count()")
                        .build())
                .addFields(new Field.Builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamUtil.makeParam("Text"))
                        .sort(sort)
                        .group(0)
                        .build())
                .build();

        final TableDataStore tableDataStore = create(tableSettings);

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            final Val[] values = new Val[2];
            values[1] = ValString.create(text);
            tableDataStore.add(values);
        }

        final Data data = tableDataStore.getData();

        final ResultRequest tableResultRequest = new ResultRequest("componentX", tableSettings, new OffsetRange(0, 3000));
        checkResults(data, tableResultRequest, 1);
    }

    @Test
    void sortedCountedTextTest3() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

//        final DataSourceFieldsMap dataSourceFieldsMap = new DataSourceFieldsMap();

//        final IndexField indexField = new IndexField();
//        indexField.setFieldName("Text");
//        indexField.setFieldType(IndexFieldType.FIELD);
//        dataSourceFieldsMap.put(indexField);

        final TableSettings tableSettings = new TableSettings.Builder()
                .addFields(new Field.Builder()
                        .id("Count")
                        .name("Count")
                        .expression("count()")
                        .build())
                .addFields(new Field.Builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamUtil.makeParam("Text"))
                        .sort(sort)
                        .group(1)
                        .build())
                .build();

        final TableDataStore tableDataStore = create(tableSettings);

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            final Val[] values = new Val[2];
            values[1] = ValString.create(text);
            tableDataStore.add(values);
        }

        final Data data = tableDataStore.getData();

        final ResultRequest tableResultRequest = new ResultRequest(
                "componentX",
                tableSettings,
                new OffsetRange(0, 3000));
        checkResults(data, tableResultRequest, 1);
    }

    private void checkResults(final Data data, final ResultRequest tableResultRequest,
                              final int sortCol) {
        final FormatterFactory formatterFactory = new FormatterFactory(null);
        final FieldFormatter fieldFormatter = new FieldFormatter(formatterFactory);

        // Make sure we only get 2000 results.
        final TableResultCreator tableComponentResultCreator = new TableResultCreator(
                fieldFormatter,
                defaultMaxResultsSizes);
        final TableResult searchResult = (TableResult) tableComponentResultCreator.create(data,
                tableResultRequest);

        assertThat(searchResult.getTotalResults() <= 50).isTrue();

        String lastValue = null;
        for (final Row result : searchResult.getRows()) {
            final String value = result.getValues().get(sortCol);

            if (lastValue != null) {
                final int diff = lastValue.compareTo(value);
                assertThat(diff <= 0).isTrue();
            }
            lastValue = value;
        }
    }

    private TableDataStore create(final TableSettings tableSettings) {
        // Create a set of sizes that are the minimum values for the combination of user provided sizes for the table and the default maximum sizes.
        final Sizes maxResults = Sizes.min(Sizes.create(tableSettings.getMaxResults()), defaultMaxResultsSizes);

        final FieldIndex fieldIndex = new FieldIndex();

        final CoprocessorKey coprocessorKey = new CoprocessorKey(1, new String[]{"test"});
        return new TableDataStore(coprocessorKey, tableSettings, fieldIndex, Collections.emptyMap(), maxResults, storeSize);
    }
}
