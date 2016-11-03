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

package stroom.search;

import org.junit.Assert;
import org.junit.Test;

import stroom.dashboard.server.TableComponentResultCreator;
import stroom.dashboard.server.format.FieldFormatter;
import stroom.dashboard.server.format.FormatterFactory;
import stroom.dashboard.shared.ParamUtil;
import stroom.dashboard.shared.Row;
import stroom.dashboard.shared.TableResult;
import stroom.dashboard.shared.TableResultRequest;
import stroom.query.CompiledDepths;
import stroom.query.CompiledFields;
import stroom.query.Item;
import stroom.query.ItemMapper;
import stroom.query.ResultStore;
import stroom.query.TablePayloadHandler;
import stroom.query.shared.Field;
import stroom.query.shared.Format;
import stroom.query.shared.IndexField;
import stroom.query.shared.IndexFieldType;
import stroom.query.shared.IndexFieldsMap;
import stroom.query.shared.Sort;
import stroom.query.shared.Sort.SortDirection;
import stroom.query.shared.TableSettings;
import stroom.mapreduce.UnsafePairQueue;
import stroom.util.shared.SharedObject;
import stroom.util.task.MonitorImpl;
import stroom.util.test.StroomUnitTest;

public class TestTablePayloadHandler extends StroomUnitTest {
    @Test
    public void basicTest() {
        final FormatterFactory formatterFactory = new FormatterFactory(null);
        final FieldFormatter fieldFormatter = new FieldFormatter(formatterFactory);

        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap();

        final IndexField indexField = new IndexField();
        indexField.setFieldName("Text");
        indexField.setFieldType(IndexFieldType.FIELD);
        indexFieldsMap.put(indexField);

        final Field field = new Field("Text");
        field.setExpression(ParamUtil.makeParam(indexField.getFieldName()));
        field.setFormat(new Format(Format.Type.TEXT));

        final TableSettings tableSettings = new TableSettings();
        tableSettings.addField(field);

        final CompiledDepths compiledDepths = new CompiledDepths(tableSettings.getFields(), tableSettings.showDetail());
        final CompiledFields compiledFields = new CompiledFields(indexFieldsMap, tableSettings.getFields(), null);

        final UnsafePairQueue<String, Item> queue = new UnsafePairQueue<>();
        final ItemMapper itemMapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + i;
            final String[] values = new String[1];
            values[0] = text;

            itemMapper.collect(null, values);
        }

        final TablePayloadHandler payloadHandler = new TablePayloadHandler(tableSettings.getFields(),
                tableSettings.showDetail(), new int[] { 50 });
        payloadHandler.addQueue(queue, new MonitorImpl());
        final ResultStore resultStore = payloadHandler.getResultStore();

        // Make sure we only get 50 results.
        final TableResultRequest tableResultRequest = new TableResultRequest(0, 3000);
        tableResultRequest.setTableSettings(tableSettings);
        final TableComponentResultCreator tableComponentResultCreator = new TableComponentResultCreator(fieldFormatter);
        final TableResult searchResult = (TableResult) tableComponentResultCreator.create(resultStore,
                tableResultRequest);
        Assert.assertEquals(50, searchResult.getTotalResults().intValue());
    }

    @Test
    public void sortedTextTest() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap();

        final IndexField indexField = new IndexField();
        indexField.setFieldName("Text");
        indexField.setFieldType(IndexFieldType.FIELD);
        indexFieldsMap.put(indexField);

        final Field field = new Field("Text");
        field.setExpression(ParamUtil.makeParam(indexField.getFieldName()));
        field.setSort(sort);

        final TableSettings tableSettings = new TableSettings();
        tableSettings.addField(field);

        final CompiledDepths compiledDepths = new CompiledDepths(tableSettings.getFields(), tableSettings.showDetail());
        final CompiledFields compiledFields = new CompiledFields(indexFieldsMap, tableSettings.getFields(), null);

        final UnsafePairQueue<String, Item> queue = new UnsafePairQueue<>();
        final ItemMapper itemMapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            final String[] values = new String[1];
            values[0] = text;

            itemMapper.collect(null, values);
        }

        final TablePayloadHandler payloadHandler = new TablePayloadHandler(tableSettings.getFields(),
                tableSettings.showDetail(), new int[] { 50 });
        payloadHandler.addQueue(queue, new MonitorImpl());
        final ResultStore resultStore = payloadHandler.getResultStore();

        final TableResultRequest tableResultRequest = new TableResultRequest(0, 3000);
        tableResultRequest.setTableSettings(tableSettings);
        checkResults(resultStore, tableResultRequest, 0);
    }

    @Test
    public void sortedNumberTest() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap();

        final IndexField indexField = new IndexField();
        indexField.setFieldName("Number");
        indexField.setFieldType(IndexFieldType.ID);
        indexFieldsMap.put(indexField);

        final Field field = new Field("Number");
        field.setExpression(ParamUtil.makeParam(indexField.getFieldName()));
        field.setSort(sort);

        final TableSettings tableSettings = new TableSettings();
        tableSettings.addField(field);

        final CompiledDepths compiledDepths = new CompiledDepths(tableSettings.getFields(), tableSettings.showDetail());
        final CompiledFields compiledFields = new CompiledFields(indexFieldsMap, tableSettings.getFields(), null);

        final UnsafePairQueue<String, Item> queue = new UnsafePairQueue<>();
        final ItemMapper itemMapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        for (int i = 0; i < 3000; i++) {
            final String text = String.valueOf((int) (Math.random() * 100));
            final String[] values = new String[1];
            values[0] = text;

            itemMapper.collect(null, values);
        }

        final TablePayloadHandler payloadHandler = new TablePayloadHandler(tableSettings.getFields(),
                tableSettings.showDetail(), new int[] { 50 });
        payloadHandler.addQueue(queue, new MonitorImpl());
        final ResultStore resultStore = payloadHandler.getResultStore();

        final TableResultRequest tableResultRequest = new TableResultRequest(0, 3000);
        tableResultRequest.setTableSettings(tableSettings);
        checkResults(resultStore, tableResultRequest, 0);
    }

    @Test
    public void sortedCountedTextTest1() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap();

        final Field count = new Field("Count");
        count.setSort(sort);
        count.setExpression("count()");

        final IndexField indexField = new IndexField();
        indexField.setFieldName("Text");
        indexField.setFieldType(IndexFieldType.FIELD);
        indexFieldsMap.put(indexField);

        final Field field = new Field("Text");
        field.setExpression(ParamUtil.makeParam(indexField.getFieldName()));
        field.setGroup(0);

        final TableSettings tableSettings = new TableSettings();
        tableSettings.addField(field);

        final CompiledDepths compiledDepths = new CompiledDepths(tableSettings.getFields(), tableSettings.showDetail());
        final CompiledFields compiledFields = new CompiledFields(indexFieldsMap, tableSettings.getFields(), null);

        final UnsafePairQueue<String, Item> queue = new UnsafePairQueue<>();
        final ItemMapper itemMapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            final String[] values = new String[2];
            values[1] = text;

            itemMapper.collect(null, values);
        }

        final TablePayloadHandler payloadHandler = new TablePayloadHandler(tableSettings.getFields(),
                tableSettings.showDetail(), new int[] { 50 });
        payloadHandler.addQueue(queue, new MonitorImpl());
        final ResultStore resultStore = payloadHandler.getResultStore();

        final TableResultRequest tableResultRequest = new TableResultRequest(0, 3000);
        tableResultRequest.setTableSettings(tableSettings);
        checkResults(resultStore, tableResultRequest, 0);
    }

    @Test
    public void sortedCountedTextTest2() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap();

        final Field count = new Field("Count");
        count.setExpression("count()");

        final IndexField indexField = new IndexField();
        indexField.setFieldName("Text");
        indexField.setFieldType(IndexFieldType.FIELD);
        indexFieldsMap.put(indexField);

        final Field field = new Field("Text");
        field.setExpression(ParamUtil.makeParam(indexField.getFieldName()));
        field.setGroup(0);
        field.setSort(sort);

        final TableSettings tableSettings = new TableSettings();
        tableSettings.addField(count);
        tableSettings.addField(field);

        final CompiledDepths compiledDepths = new CompiledDepths(tableSettings.getFields(), tableSettings.showDetail());
        final CompiledFields compiledFields = new CompiledFields(indexFieldsMap, tableSettings.getFields(), null);

        final UnsafePairQueue<String, Item> queue = new UnsafePairQueue<>();
        final ItemMapper itemMapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            final String[] values = new String[2];
            values[1] = text;

            itemMapper.collect(null, values);
        }

        final TablePayloadHandler payloadHandler = new TablePayloadHandler(tableSettings.getFields(),
                tableSettings.showDetail(), new int[] { 50 });
        payloadHandler.addQueue(queue, new MonitorImpl());
        final ResultStore resultStore = payloadHandler.getResultStore();

        final TableResultRequest tableResultRequest = new TableResultRequest(0, 3000);
        tableResultRequest.setTableSettings(tableSettings);
        checkResults(resultStore, tableResultRequest, 1);
    }

    @Test
    public void sortedCountedTextTest3() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap();

        final Field count = new Field("Count");
        count.setExpression("count()");

        final IndexField indexField = new IndexField();
        indexField.setFieldName("Text");
        indexField.setFieldType(IndexFieldType.FIELD);
        indexFieldsMap.put(indexField);

        final Field field = new Field("Text");
        field.setExpression(ParamUtil.makeParam(indexField.getFieldName()));
        field.setSort(sort);
        field.setGroup(1);

        final TableSettings tableSettings = new TableSettings();
        tableSettings.addField(count);
        tableSettings.addField(field);

        final CompiledDepths compiledDepths = new CompiledDepths(tableSettings.getFields(), tableSettings.showDetail());
        final CompiledFields compiledFields = new CompiledFields(indexFieldsMap, tableSettings.getFields(), null);

        final UnsafePairQueue<String, Item> queue = new UnsafePairQueue<>();
        final ItemMapper itemMapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            final String[] values = new String[2];
            values[1] = text;

            itemMapper.collect(null, values);
        }

        final TablePayloadHandler payloadHandler = new TablePayloadHandler(tableSettings.getFields(),
                tableSettings.showDetail(), new int[] { 50 });
        payloadHandler.addQueue(queue, new MonitorImpl());
        final ResultStore resultStore = payloadHandler.getResultStore();

        final TableResultRequest tableResultRequest = new TableResultRequest(0, 3000);
        tableResultRequest.setTableSettings(tableSettings);
        checkResults(resultStore, tableResultRequest, 1);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void checkResults(final ResultStore resultStore, final TableResultRequest tableResultRequest,
            final int sortCol) {
        final FormatterFactory formatterFactory = new FormatterFactory(null);
        final FieldFormatter fieldFormatter = new FieldFormatter(formatterFactory);

        // Make sure we only get 2000 results.
        final TableComponentResultCreator tableComponentResultCreator = new TableComponentResultCreator(fieldFormatter);
        final TableResult searchResult = (TableResult) tableComponentResultCreator.create(resultStore,
                tableResultRequest);

        Assert.assertTrue(searchResult.getTotalResults().intValue() <= 50);

        Object lastValue = null;
        for (final Row result : searchResult.getRows()) {
            final SharedObject value = result.getValues()[sortCol];

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
}
