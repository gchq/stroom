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

package stroom.dashboard.server;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import stroom.test.StroomProcessTestFileUtil;
import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;

import stroom.dashboard.server.vis.CompiledStructure;
import stroom.dashboard.server.vis.CompiledStructure.FieldRef;
import stroom.dashboard.server.vis.StructureBuilder;
import stroom.dashboard.server.vis.VisComponentResultCreator;
import stroom.dashboard.server.vis.VisComponentResultCreator.Store;
import stroom.dashboard.server.vis.VisSettings;
import stroom.dashboard.server.vis.VisSettingsUtil;
import stroom.dashboard.shared.ParamUtil;
import stroom.query.Item;
import stroom.query.Items;
import stroom.query.ItemsArrayList;
import stroom.query.shared.Field;
import stroom.query.shared.Format;
import stroom.query.shared.Format.Type;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomUnitTest;

public class TestVisComponentResultCreator extends StroomUnitTest {
    private static final File TEST_DIR = new File(StroomProcessTestFileUtil.getTestResourcesDir(),
            "TestVisComponentResultCreator");

    @Test
    public void testValues() throws Exception {
        final Items<Item> items = createData();

        final CompiledStructure.Field xField = new CompiledStructure.Field(new FieldRef(Type.NUMBER, 0), null);
        final CompiledStructure.Field yField = new CompiledStructure.Field(new FieldRef(Type.NUMBER, 1), null);

        final CompiledStructure.Values values = new CompiledStructure.Values(
                new CompiledStructure.Field[] { xField, yField }, null);

        final VisComponentResultCreator resultCreator = new VisComponentResultCreator(null);
        final Store store = resultCreator.create(items, values);

        final ObjectMapper mapper = createMapper(true);
        final String json = mapper.writeValueAsString(store);
        System.out.println(json);
    }

    @Test
    public void testNest() throws Exception {
        final Items<Item> items = createData();

        final CompiledStructure.Field xField = new CompiledStructure.Field(new FieldRef(Type.NUMBER, 0), null);
        final CompiledStructure.Field yField = new CompiledStructure.Field(new FieldRef(Type.NUMBER, 1), null);
        final CompiledStructure.Field seriesField = new CompiledStructure.Field(new FieldRef(Type.TEXT, 2), null);

        final CompiledStructure.Values values = new CompiledStructure.Values(
                new CompiledStructure.Field[] { xField, yField }, null);
        final CompiledStructure.Nest nest = new CompiledStructure.Nest(seriesField, null, null, values);

        final VisComponentResultCreator resultCreator = new VisComponentResultCreator(null);
        final Store store = resultCreator.create(items, nest);

        final ObjectMapper mapper = createMapper(true);
        final String json = mapper.writeValueAsString(store);
        System.out.println(json);
    }

    @Test
    public void testDeepNest() throws Exception {
        final Items<Item> items = createData();

        final CompiledStructure.Field xField = new CompiledStructure.Field(new FieldRef(Type.NUMBER, 0), null);
        final CompiledStructure.Field yField = new CompiledStructure.Field(new FieldRef(Type.NUMBER, 1), null);
        final CompiledStructure.Field seriesField = new CompiledStructure.Field(new FieldRef(Type.TEXT, 2), null);

        final CompiledStructure.Values values = new CompiledStructure.Values(
                new CompiledStructure.Field[] { xField, yField }, null);
        final CompiledStructure.Nest nestLevel2 = new CompiledStructure.Nest(xField, null, null, values);
        final CompiledStructure.Nest nest = new CompiledStructure.Nest(seriesField, null, nestLevel2, null);

        final VisComponentResultCreator resultCreator = new VisComponentResultCreator(null);
        final Store store = resultCreator.create(items, nest);

        final ObjectMapper mapper = createMapper(true);
        final String json = mapper.writeValueAsString(store);
        System.out.println(json);
    }

    @Test
    public void testAll() throws Exception {
        final File[] dirs = TEST_DIR.listFiles();
        for (final File configDir : dirs) {
            if (configDir.isDirectory()) {
                testDir(configDir);
            }
        }
    }

    @Test
    public void testGroupBySeries() throws Exception {
        testDir(new File(TEST_DIR, "testGroupBySeries"));
    }

    @Test
    public void testGroupBySeriesAndX() throws Exception {
        testDir(new File(TEST_DIR, "testGroupBySeriesAndX"));
    }

    private void testDir(final File dir) throws Exception {
        final File settings = new File(dir, "settings.json");
        final String settingsJSON = StreamUtil.fileToString(settings);
        final VisSettings visSettings = VisSettingsUtil.read(settingsJSON);

        final String expected = VisSettingsUtil.write(visSettings);
        compareFiles(dir, expected, "settings.json.out", "settings.json.tmp");

        final String dashboardSettingsJSON = StreamUtil.fileToString(new File(dir, "dashboardSettings.json"));

        final List<Field> fields = new ArrayList<Field>(3);
        fields.add(createField("xField", Format.Type.NUMBER));
        fields.add(createField("yField", Format.Type.NUMBER));
        fields.add(createField("seriesField", Format.Type.TEXT));

        final StructureBuilder structureBuilder = new StructureBuilder(settingsJSON, dashboardSettingsJSON, fields);
        final CompiledStructure.Structure structure = structureBuilder.create();

        final VisComponentResultCreator resultCreator = new VisComponentResultCreator(structure);

        final Items<Item> items = createData();

        final Store store = resultCreator.create(items);

        ObjectMapper mapper = createMapper(true);
        String json = mapper.writeValueAsString(store);
        compareFiles(dir, json, "data-indented.json", "data-indented.json.tmp");

        mapper = createMapper(false);
        json = mapper.writeValueAsString(store);
        compareFiles(dir, json, "data.json", "data.json.tmp");
    }

    private void compareFiles(final File dir, final String data, final String expectedFileName,
            final String actualFileName) {
        final File expectedFile = new File(dir, expectedFileName);
        if (!expectedFile.isFile()) {
            StreamUtil.stringToFile(data, expectedFile);
        } else {
            final String expected = StreamUtil.fileToString(expectedFile);
            final File actualFile = new File(dir, actualFileName);
            if (!expected.equals(data)) {
                StreamUtil.stringToFile(data, actualFile);
                Assert.fail("Files are not the same");
            } else if (actualFile.isFile()) {
                actualFile.delete();
            }
        }
    }

    private Items<Item> createData() {
        final Items<Item> items = new ItemsArrayList<Item>();
        int seriesCount = 0;
        for (int i = 100; i < 120; i++) {
            for (int j = 20; j < 30; j++) {
                seriesCount++;
                final String series = "series " + seriesCount;
                if (seriesCount == 5) {
                    seriesCount = 0;
                }

                items.add(new Item(null, null, createValues(i + 10, j + 10, series), 0));
                items.add(new Item(null, null, createValues(i - 5, j + 11, series), 0));
                items.add(new Item(null, null, createValues(i + 3, j + 3, series), 0));
                items.add(new Item(null, null, createValues(i - 2, j + 8, series), 0));
            }
        }
        return items;
    }

    private Object[] createValues(final int x, final int y, final String series) {
        final Object[] values = new Object[3];
        values[0] = x;
        values[1] = y;
        values[2] = series;
        return values;
    }

    private Field createField(final String fieldName, final Format.Type type) {
        final Format format = new Format(type);

        final Field field = new Field(fieldName);
        field.setExpression(ParamUtil.makeParam(fieldName));
        field.setFormat(format);
        return field;
    }

    private ObjectMapper createMapper(final boolean indent) {
        final SimpleModule module = new SimpleModule();
        module.addSerializer(Double.class, new MyDoubleSerialiser());

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }
}
