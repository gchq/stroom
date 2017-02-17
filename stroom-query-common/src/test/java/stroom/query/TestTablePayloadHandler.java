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

package stroom.query;

import org.junit.Assert;
import org.junit.Test;
import stroom.mapreduce.UnsafePairQueue;
import stroom.query.api.Field;
import stroom.query.api.Format;
import stroom.query.api.OffsetRange;
import stroom.query.api.ResultRequest;
import stroom.query.api.Row;
import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;
import stroom.query.format.FieldFormatter;
import stroom.query.format.FormatterFactory;
import stroom.util.shared.HasTerminate;
import stroom.util.shared.ParamUtil;

import java.util.Collections;

public class TestTablePayloadHandler {
    private final TrimSettings trimSettings = new TrimSettings(new Integer[]{50}, new Integer[]{50});

    @Test
    public void basicTest() {
        final FormatterFactory formatterFactory = new FormatterFactory(null);
        final FieldFormatter fieldFormatter = new FieldFormatter(formatterFactory);

//        final DataSourceFieldsMap dataSourceFieldsMap = new DataSourceFieldsMap();

//        final DataSourceField indexField = new DataSourceField(DataSourceFieldType.FIELD, "Text");
//        dataSourceFieldsMap.put(indexField);

        final Field field = new Field("Text");
        field.setExpression(ParamUtil.makeParam("Text"));
        field.setFormat(new Format(Format.Type.TEXT));

        final TableSettings tableSettings = new TableSettings();
        tableSettings.setFields(new Field[]{field});

        final CompiledDepths compiledDepths = new CompiledDepths(tableSettings.getFields(), tableSettings.showDetail());
        final CompiledFields compiledFields = new CompiledFields(tableSettings.getFields(), null, Collections.emptyMap());

        final UnsafePairQueue<Key, Item> queue = new UnsafePairQueue<>();
        final ItemMapper itemMapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + i;
            final String[] values = new String[1];
            values[0] = text;

            itemMapper.collect(null, values);
        }

        final TablePayloadHandler payloadHandler = new TablePayloadHandler(tableSettings.getFields(),
                tableSettings.showDetail(), trimSettings);
        payloadHandler.addQueue(queue, new Terminatable());
        final Data data = payloadHandler.getData();

        // Make sure we only get 50 results.
        final ResultRequest tableResultRequest = new ResultRequest("componentX", tableSettings, new OffsetRange(0, 3000));
        final TableResultCreator tableComponentResultCreator = new TableResultCreator(fieldFormatter);
        final TableResult searchResult = (TableResult) tableComponentResultCreator.create(data,
                tableResultRequest);
        Assert.assertEquals(50, searchResult.getTotalResults().intValue());
    }

    @Test
    public void sortedTextTest() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

//        final DataSourceFieldsMap dataSourceFieldsMap = new DataSourceFieldsMap();
//
//        final IndexField indexField = new IndexField();
//        indexField.setFieldName("Text");
//        indexField.setFieldType(IndexFieldType.FIELD);
//        dataSourceFieldsMap.put(indexField);

        final Field field = new Field("Text");
        field.setExpression(ParamUtil.makeParam("Text"));
        field.setSort(sort);

        final TableSettings tableSettings = new TableSettings();
        tableSettings.setFields(new Field[]{field});

        final CompiledDepths compiledDepths = new CompiledDepths(tableSettings.getFields(), tableSettings.showDetail());
        final CompiledFields compiledFields = new CompiledFields(tableSettings.getFields(), null, Collections.emptyMap());

        final UnsafePairQueue<Key, Item> queue = new UnsafePairQueue<>();
        final ItemMapper itemMapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            final String[] values = new String[1];
            values[0] = text;

            itemMapper.collect(null, values);
        }

        final TablePayloadHandler payloadHandler = new TablePayloadHandler(tableSettings.getFields(),
                tableSettings.showDetail(), trimSettings);
        payloadHandler.addQueue(queue, new Terminatable());
        final Data data = payloadHandler.getData();

        final ResultRequest tableResultRequest = new ResultRequest("componentX", tableSettings, new OffsetRange(0, 3000));
        checkResults(data, tableResultRequest, 0);
    }

    @Test
    public void sortedNumberTest() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

//        final DataSourceFieldsMap dataSourceFieldsMap = new DataSourceFieldsMap();
//
//        final IndexField indexField = new IndexField();
//        indexField.setFieldName("Number");
//        indexField.setFieldType(IndexFieldType.ID);
//        dataSourceFieldsMap.put(indexField);

        final Field field = new Field("Number");
        field.setExpression(ParamUtil.makeParam("Number"));
        field.setSort(sort);

        final TableSettings tableSettings = new TableSettings();
        tableSettings.setFields(new Field[]{field});

        final CompiledDepths compiledDepths = new CompiledDepths(tableSettings.getFields(), tableSettings.showDetail());
        final CompiledFields compiledFields = new CompiledFields(tableSettings.getFields(), null, Collections.emptyMap());

        final UnsafePairQueue<Key, Item> queue = new UnsafePairQueue<>();
        final ItemMapper itemMapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        for (int i = 0; i < 3000; i++) {
            final String text = String.valueOf((int) (Math.random() * 100));
            final String[] values = new String[1];
            values[0] = text;

            itemMapper.collect(null, values);
        }

        final TablePayloadHandler payloadHandler = new TablePayloadHandler(tableSettings.getFields(),
                tableSettings.showDetail(), trimSettings);
        payloadHandler.addQueue(queue, new Terminatable());
        final Data data = payloadHandler.getData();

        final ResultRequest tableResultRequest = new ResultRequest("componentX", tableSettings, new OffsetRange(0, 3000));
        checkResults(data, tableResultRequest, 0);
    }

    @Test
    public void sortedCountedTextTest1() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

//        final DataSourceFieldsMap dataSourceFieldsMap = new DataSourceFieldsMap();

        final Field count = new Field("Count");
        count.setSort(sort);
        count.setExpression("count()");

//        final IndexField indexField = new IndexField();
//        indexField.setFieldName("Text");
//        indexField.setFieldType(IndexFieldType.FIELD);
//        dataSourceFieldsMap.put(indexField);

        final Field field = new Field("Text");
        field.setExpression(ParamUtil.makeParam("Text"));
        field.setGroup(0);

        final TableSettings tableSettings = new TableSettings();
        tableSettings.setFields(new Field[]{field});

        final CompiledDepths compiledDepths = new CompiledDepths(tableSettings.getFields(), tableSettings.showDetail());
        final CompiledFields compiledFields = new CompiledFields(tableSettings.getFields(), null, Collections.emptyMap());

        final UnsafePairQueue<Key, Item> queue = new UnsafePairQueue<>();
        final ItemMapper itemMapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            final String[] values = new String[2];
            values[1] = text;

            itemMapper.collect(null, values);
        }

        final TablePayloadHandler payloadHandler = new TablePayloadHandler(tableSettings.getFields(),
                tableSettings.showDetail(), trimSettings);
        payloadHandler.addQueue(queue, new Terminatable());
        final Data data = payloadHandler.getData();

        final ResultRequest tableResultRequest = new ResultRequest("componentX", tableSettings, new OffsetRange(0, 3000));
        checkResults(data, tableResultRequest, 0);
    }

    @Test
    public void sortedCountedTextTest2() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

//        final DataSourceFieldsMap dataSourceFieldsMap = new DataSourceFieldsMap();

        final Field count = new Field("Count");
        count.setExpression("count()");

//        final IndexField indexField = new IndexField();
//        indexField.setFieldName("Text");
//        indexField.setFieldType(IndexFieldType.FIELD);
//        dataSourceFieldsMap.put(indexField);

        final Field field = new Field("Text");
        field.setExpression(ParamUtil.makeParam("Text"));
        field.setGroup(0);
        field.setSort(sort);

        final TableSettings tableSettings = new TableSettings();
        tableSettings.setFields(new Field[]{field, count});

        final CompiledDepths compiledDepths = new CompiledDepths(tableSettings.getFields(), tableSettings.showDetail());
        final CompiledFields compiledFields = new CompiledFields(tableSettings.getFields(), null, Collections.emptyMap());

        final UnsafePairQueue<Key, Item> queue = new UnsafePairQueue<>();
        final ItemMapper itemMapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            final String[] values = new String[2];
            values[1] = text;

            itemMapper.collect(null, values);
        }

        final TablePayloadHandler payloadHandler = new TablePayloadHandler(tableSettings.getFields(),
                tableSettings.showDetail(), trimSettings);
        payloadHandler.addQueue(queue, new Terminatable());
        final Data data = payloadHandler.getData();

        final ResultRequest tableResultRequest = new ResultRequest("componentX", tableSettings, new OffsetRange(0, 3000));
        checkResults(data, tableResultRequest, 1);
    }

    @Test
    public void sortedCountedTextTest3() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

//        final DataSourceFieldsMap dataSourceFieldsMap = new DataSourceFieldsMap();

        final Field count = new Field("Count");
        count.setExpression("count()");

//        final IndexField indexField = new IndexField();
//        indexField.setFieldName("Text");
//        indexField.setFieldType(IndexFieldType.FIELD);
//        dataSourceFieldsMap.put(indexField);

        final Field field = new Field("Text");
        field.setExpression(ParamUtil.makeParam("Text"));
        field.setSort(sort);
        field.setGroup(1);

        final TableSettings tableSettings = new TableSettings();
        tableSettings.setFields(new Field[]{field, count});

        final CompiledDepths compiledDepths = new CompiledDepths(tableSettings.getFields(), tableSettings.showDetail());
        final CompiledFields compiledFields = new CompiledFields(tableSettings.getFields(), null, Collections.emptyMap());

        final UnsafePairQueue<Key, Item> queue = new UnsafePairQueue<>();
        final ItemMapper itemMapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            final String[] values = new String[2];
            values[1] = text;

            itemMapper.collect(null, values);
        }

        final TablePayloadHandler payloadHandler = new TablePayloadHandler(tableSettings.getFields(),
                tableSettings.showDetail(), trimSettings);
        payloadHandler.addQueue(queue, new Terminatable());
        final Data data = payloadHandler.getData();

        final ResultRequest tableResultRequest = new ResultRequest("componentX", tableSettings, new OffsetRange(0, 3000));
        checkResults(data, tableResultRequest, 1);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void checkResults(final Data data, final ResultRequest tableResultRequest,
                              final int sortCol) {
        final FormatterFactory formatterFactory = new FormatterFactory(null);
        final FieldFormatter fieldFormatter = new FieldFormatter(formatterFactory);

        // Make sure we only get 2000 results.
        final TableResultCreator tableComponentResultCreator = new TableResultCreator(fieldFormatter);
        final TableResult searchResult = (TableResult) tableComponentResultCreator.create(data,
                tableResultRequest);

        Assert.assertTrue(searchResult.getTotalResults().intValue() <= 50);

        Object lastValue = null;
        for (final Row result : searchResult.getRows()) {
            final String value = result.getValues()[sortCol];

            if (lastValue != null) {
                if (lastValue instanceof Comparable) {
                    final Comparable comparable = (Comparable) lastValue;
                    final int diff = comparable.compareTo(value);
                    Assert.assertTrue(diff <= 0);
                }
            }
            lastValue = value;
        }
    }

    private static class Terminatable implements HasTerminate {
        @Override
        public void terminate() {

        }

        @Override
        public boolean isTerminated() {
            return false;
        }
    }
}
