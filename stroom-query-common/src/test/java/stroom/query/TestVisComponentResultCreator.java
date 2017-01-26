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

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.Assert;
import org.junit.Test;
import stroom.query.CompiledStructure.FieldRef;
import stroom.query.api.Field;
import stroom.query.api.Format;
import stroom.query.api.Format.Type;
import stroom.query.api.Node;
import stroom.util.shared.ParamUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestVisComponentResultCreator {
    final Path TEST_DIR = TestFileUtil.getTestResourcesDir().resolve("TestVisComponentResultCreator");

    @Test
    public void testValues() throws Exception {
        final Items<Item> items = createData();

        final CompiledStructure.Field xField = new CompiledStructure.Field(new FieldRef(Type.NUMBER, 0), null);
        final CompiledStructure.Field yField = new CompiledStructure.Field(new FieldRef(Type.NUMBER, 1), null);

        final CompiledStructure.Values values = new CompiledStructure.Values(
                new CompiledStructure.Field[]{xField, yField}, null);

        final VisResultCreator resultCreator = new VisResultCreator(null);
        final Node store = resultCreator.create(items, values);

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
                new CompiledStructure.Field[]{xField, yField}, null);
        final CompiledStructure.Nest nest = new CompiledStructure.Nest(seriesField, null, null, values);

        final VisResultCreator resultCreator = new VisResultCreator(null);
        final Node store = resultCreator.create(items, nest);

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
                new CompiledStructure.Field[]{xField, yField}, null);
        final CompiledStructure.Nest nestLevel2 = new CompiledStructure.Nest(xField, null, null, values);
        final CompiledStructure.Nest nest = new CompiledStructure.Nest(seriesField, null, nestLevel2, null);

        final VisResultCreator resultCreator = new VisResultCreator(null);
        final Node store = resultCreator.create(items, nest);

        final ObjectMapper mapper = createMapper(true);
        final String json = mapper.writeValueAsString(store);
        System.out.println(json);
    }

    @Test
    public void testAll() throws IOException {
        Files.list(TEST_DIR).forEach(f -> {
            if (Files.isDirectory(f)) {
                testDir(f);
            }
        });
    }

    @Test
    public void testGroupBySeries() {
        testDir(TEST_DIR.resolve("testGroupBySeries"));
    }

    @Test
    public void testGroupBySeriesAndX() {
        testDir(TEST_DIR.resolve("testGroupBySeriesAndX"));
    }

    private void testDir(final Path dir) {
//        final File settings = new File(dir, "settings.json");
//        final String settingsJSON = StreamUtil.fileToString(settings);
//        final VisSettings visSettings = VisSettingsUtil.read(settingsJSON);
//
//        final String expected = VisSettingsUtil.write(visSettings);
//        compareFiles(dir, expected, "settings.json.out", "settings.json.tmp");
//
//        final String dashboardSettingsJSON = StreamUtil.fileToString(new File(dir, "dashboardSettings.json"));
//
//        final List<Field> fields = new ArrayList<Field>(3);
//        fields.add(createField("xField", Format.Type.NUMBER));
//        fields.add(createField("yField", Format.Type.NUMBER));
//        fields.add(createField("seriesField", Format.Type.TEXT));
//
//        final StructureBuilder structureBuilder = new StructureBuilder(settingsJSON, dashboardSettingsJSON, fields);
//        final CompiledStructure.Structure structure = structureBuilder.create();
//
//        final VisComponentResultCreator resultCreator = new VisComponentResultCreator(structure);
//
//        final Items<Item> items = createData();
//
//        final Store store = resultCreator.create(items);
//
//        ObjectMapper mapper = createMapper(true);
//        String json = mapper.writeValueAsString(store);
//        compareFiles(dir, json, "data-indented.json", "data-indented.json.tmp");
//
//        mapper = createMapper(false);
//        json = mapper.writeValueAsString(store);
//        compareFiles(dir, json, "data.json", "data.json.tmp");
    }

    private void compareFiles(final Path dir, final String data, final String expectedFileName,
                              final String actualFileName) throws IOException {
        final Path expectedFile = dir.resolve(expectedFileName);
        if (!Files.isRegularFile(expectedFile)) {
            StreamUtil.stringToFile(data, expectedFile);
        } else {
            final String expected = StreamUtil.fileToString(expectedFile);
            final Path actualFile = dir.resolve(actualFileName);
            if (!expected.equals(data)) {
                StreamUtil.stringToFile(data, actualFile);
                Assert.fail("Files are not the same");
            } else if (Files.isRegularFile(actualFile)) {
                Files.delete(actualFile);
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
