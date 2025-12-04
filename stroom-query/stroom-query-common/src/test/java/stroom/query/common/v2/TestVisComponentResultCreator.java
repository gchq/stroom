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

package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.TableSettings;

import org.junit.jupiter.api.Disabled;

@Disabled
class TestVisComponentResultCreator {
//    final Path TEST_DIR = TestFileUtil.getTestResourcesDir().resolve("TestVisComponentResultCreator");
//
//    @Test
//    public void testValues() {
//        final TableSettings parentTableSettings = getParentTableSettings();
//        final Data parentData = createData();
//
//        final VisField fieldX = new VisField("x");
//        final VisField fieldY = new VisField("y");
//        final VisValues visValues = new VisValues();
//        visValues.setFields(new VisField[]{fieldX, fieldY});
//        visValues.setLimit(new VisLimit(100));
//        VisStructure visStructure = new VisStructure();
//        visStructure.setValues(visValues);
//
//        final TableSettings childTableSettings = visStructureToTableSettings(parentTableSettings, visStructure);
//
//        final ResultRequest tableResultRequest = new ResultRequest();
//        tableResultRequest.setMappings(new TableSettings[]{parentTableSettings, childTableSettings});
//
//        final VisResultCreator resultCreator = new VisResultCreator(tableResultRequest, Collections.emptyMap(), null);
//        final Result visResult = resultCreator.create(parentData, tableResultRequest);
//
//        final ObjectMapper mapper = createMapper(true);
//        final String json = mapper.writeValueAsString(visResult);
//        System.out.println(json);
//    }
//
//    @Test
//    public void testNest() {
//        final TableSettings parentTableSettings = getParentTableSettings();
//        final Data parentData = createData();
//
//        final VisField fieldX = new VisField("x");
//        final VisField fieldY = new VisField("y");
//        final VisField fieldSeries = new VisField("series");
//        final VisValues visValues = new VisValues();
//        visValues.setFields(new VisField[]{fieldX, fieldY});
//
//        VisNest visNest = new VisNest(fieldSeries);
//        visNest.setValues(visValues);
//
//        VisStructure visStructure = new VisStructure();
//        visStructure.setNest(visNest);
//
//        final TableSettings childTableSettings = visStructureToTableSettings(parentTableSettings, visStructure);
//
//        final ResultRequest tableResultRequest = new ResultRequest();
//        tableResultRequest.setMappings(new TableSettings[]{parentTableSettings, childTableSettings});
//
//        final VisResultCreator resultCreator = new VisResultCreator(tableResultRequest, Collections.emptyMap(), null);
//        final Result visResult = resultCreator.create(parentData, tableResultRequest);
//
//        final ObjectMapper mapper = createMapper(true);
//        final String json = mapper.writeValueAsString(visResult);
//        System.out.println(json);
//    }
//
////    @Test
////    public void testDeepNest() {
////        final Items<Item> items = createData();
////
////        final CompiledStructure.Field xField = new CompiledStructure.Field(new FieldRef(Type.NUMBER, 0), null);
////        final CompiledStructure.Field yField = new CompiledStructure.Field(new FieldRef(Type.NUMBER, 1), null);
////        final CompiledStructure.Field seriesField = new CompiledStructure.Field(new FieldRef(Type.TEXT, 2), null);
////
////        final CompiledStructure.Values values = new CompiledStructure.Values(
////                new CompiledStructure.Field[]{xField, yField}, null);
////        final CompiledStructure.Nest nestLevel2 = new CompiledStructure.Nest(xField, null, null, values);
////        final CompiledStructure.Nest nest = new CompiledStructure.Nest(seriesField, null, nestLevel2, null);
////
////        final VisResultCreator resultCreator = new VisResultCreator(null);
////        final Node store = resultCreator.create(items, nest);
////
////        final ObjectMapper mapper = createMapper(true);
////        final String json = mapper.writeValueAsString(store);
////        System.out.println(json);
////    }
//
//    private TableSettings visStructureToTableSettings(
//    final TableSettings parentTableSettings, final VisStructure visStructure) {
//        final Field[] parentFields = parentTableSettings.getFields();
//        final Map<String, Field> parentFieldMap = new HashMap<>();
//        for (final Field field : parentFields) {
//            parentFieldMap.put(field.getName(), field);
//        }
//
//        List<Field> fields = new ArrayList<>();
//        List<Integer> limits = new ArrayList<>();
//
//        VisNest nest = visStructure.getNest();
//        VisValues values = visStructure.getValues();
//
//        int group = 0;
//        while (nest != null) {
//            Field field = convertField(visStructure.getNest().getKey(), parentFieldMap);
//            field.setGroup(group++);
//
//            fields.add(field);
//
//            // Get limit from nest.
//            Integer limit = null;
//            if (nest.getLimit() != null) {
//                limit = nest.getLimit().getSize();
//            }
//            limits.add(limit);
//
//            values = nest.getValues();
//            nest = nest.getNest();
//        }
//
//        if (values != null) {
//            // Get limit from values.
//            Integer limit = null;
//            if (values.getLimit() != null) {
//                limit = values.getLimit().getSize();
//            }
//            limits.add(limit);
//
//            if (values.getFields() != null) {
//                for (final VisField visField : values.getFields()) {
//                    fields.add(convertField(visField, parentFieldMap));
//                }
//            }
//        }
//
//        final TableSettings tableSettings = new TableSettings();
//        tableSettings.setFields(fields.toArray(new Field[fields.size()]));
//        tableSettings.setMaxResults(limits.toArray(new Integer[limits.size()]));
//        tableSettings.setShowDetail(true);
//
//        return tableSettings;
//    }
//
//    private Field convertField(final VisField visField, final Map<String, Field> parentFieldMap) {
//        final Field parentField = parentFieldMap.get(visField.getId());
//        Format format;
//        if (parentField != null && parentField.getFormat() != null) {
//            format = parentField.getFormat();
//        } else {
//            format = new Format();
//            format.setType(Type.GENERAL);
//        }
//
//        final Field field = new Field();
//        field.setExpression("${" + visField.getId() + "}");
//        field.setSort(visField.getSort());
//        field.setFormat(format);
//
//        return field;
//    }

    private TableSettings getParentTableSettings() {
        return TableSettings.builder()
                .addColumns(Column.builder()
                        .id("x")
                        .name("x")
                        .expression("${x}")
                        .build())
                .addColumns(Column.builder()
                        .id("y")
                        .name("y")
                        .expression("${y}")
                        .build())
                .addColumns(Column.builder()
                        .id("series")
                        .name("series")
                        .expression("${series}")
                        .build())
                .build();
    }

//    @Test
//    public void testNest() {
//        final Items<Item> items = createData();
//
//        final CompiledStructure.Field xField = new CompiledStructure.Field(new FieldRef(Type.NUMBER, 0), null);
//        final CompiledStructure.Field yField = new CompiledStructure.Field(new FieldRef(Type.NUMBER, 1), null);
//        final CompiledStructure.Field seriesField = new CompiledStructure.Field(new FieldRef(Type.TEXT, 2), null);
//
//        final CompiledStructure.Values values = new CompiledStructure.Values(
//                new CompiledStructure.Field[]{xField, yField}, null);
//        final CompiledStructure.Nest nest = new CompiledStructure.Nest(seriesField, null, null, values);
//
//        final VisResultCreator resultCreator = new VisResultCreator(null);
//        final Node store = resultCreator.create(items, nest);
//
//        final ObjectMapper mapper = createMapper(true);
//        final String json = mapper.writeValueAsString(store);
//        System.out.println(json);
//    }
//
//    @Test
//    public void testDeepNest() {
//        final Items<Item> items = createData();
//
//        final CompiledStructure.Field xField = new CompiledStructure.Field(new FieldRef(Type.NUMBER, 0), null);
//        final CompiledStructure.Field yField = new CompiledStructure.Field(new FieldRef(Type.NUMBER, 1), null);
//        final CompiledStructure.Field seriesField = new CompiledStructure.Field(new FieldRef(Type.TEXT, 2), null);
//
//        final CompiledStructure.Values values = new CompiledStructure.Values(
//                new CompiledStructure.Field[]{xField, yField}, null);
//        final CompiledStructure.Nest nestLevel2 = new CompiledStructure.Nest(xField, null, null, values);
//        final CompiledStructure.Nest nest = new CompiledStructure.Nest(seriesField, null, nestLevel2, null);
//
//        final VisResultCreator resultCreator = new VisResultCreator(null);
//        final Node store = resultCreator.create(items, nest);
//
//        final ObjectMapper mapper = createMapper(true);
//        final String json = mapper.writeValueAsString(store);
//        System.out.println(json);
//    }

//    @Test
//    public void testAll() throws IOException {
//        Files.list(TEST_DIR).forEach(f -> {
//            if (Files.isDirectory(f)) {
//                testDir(f);
//            }
//        });
//    }
//
//    @Test
//    public void testGroupBySeries() {
//        testDir(TEST_DIR.resolve("testGroupBySeries"));
//    }
//
//    @Test
//    public void testGroupBySeriesAndX() {
//        testDir(TEST_DIR.resolve("testGroupBySeriesAndX"));
//    }
//
//    private void testDir(final Path dir) {
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
//    }
//
//    private void compareFiles(final Path dir, final String data, final String expectedFileName,
//                              final String actualFileName) throws IOException {
//        final Path expectedFile = dir.resolve(expectedFileName);
//        if (!Files.isRegularFile(expectedFile)) {
//            StreamUtil.stringToFile(data, expectedFile);
//        } else {
//            final String expected = StreamUtil.fileToString(expectedFile);
//            final Path actualFile = dir.resolve(actualFileName);
//            if (!expected.equals(data)) {
//                StreamUtil.stringToFile(data, actualFile);
//                fail("Files are not the same");
//            } else if (Files.isRegularFile(actualFile)) {
//                Files.delete(actualFile);
//            }
//        }
//    }
//
//    private Data createData() {
//        final Items<Item> items = new ItemsArrayList<>();
//        int seriesCount = 0;
//        for (int i = 100; i < 120; i++) {
//            for (int j = 20; j < 30; j++) {
//                seriesCount++;
//                final String series = "series " + seriesCount;
//                if (seriesCount == 5) {
//                    seriesCount = 0;
//                }
//
//                items.add(new Item(null, createGenerators(i + 10, j + 10, series), 0));
//                items.add(new Item(null, createGenerators(i - 5, j + 11, series), 0));
//                items.add(new Item(null, createGenerators(i + 3, j + 3, series), 0));
//                items.add(new Item(null, createGenerators(i - 2, j + 8, series), 0));
//            }
//        }
//
//        final Map<GroupKey, Items<Item>> map = new HashMap<>();
//        map.put(null, items);
//
//        return new Data(map, items.size(), items.size());
//    }
//
//    private Generator[] createGenerators(final int x, final int y, final String series) {
//        final Generator[] generators = new Generator[3];
//        generators[0] = new StaticValueFunction(ValInteger.create(x)).createGenerator();
//        generators[1] = new StaticValueFunction(ValInteger.create(y)).createGenerator();
//        generators[2] = new StaticValueFunction(ValString.create(series)).createGenerator();
//        return generators;
//    }

//    private Field createField(final String fieldName, final Format.Type type) {
//        return Field.builder().name(fieldName).expression(ParamUtil.makeParam(fieldName)).format(type).build();
//    }
}
