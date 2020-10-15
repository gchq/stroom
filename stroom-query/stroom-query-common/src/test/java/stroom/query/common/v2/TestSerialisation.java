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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.assertj.core.util.diff.DiffUtils;
import org.assertj.core.util.diff.Patch;
import org.junit.jupiter.api.Test;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.query.api.v2.DateTimeFormat;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Filter;
import stroom.query.api.v2.FlatResult;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.Format.Type;
import stroom.query.api.v2.NumberFormat;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.Sort;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableSettings;
import stroom.query.api.v2.TimeZone;
import stroom.query.test.util.ConsoleColour;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestSerialisation {
    private static DataSource getDataSource() {
        return new DataSource.Builder()
                .addFields(new TextField("field1"), new LongField("field2"))
                .build();
    }

    private static SearchRequest getSearchRequest() {
        return new SearchRequest.Builder()
                .key("1234")
                .query(new Query.Builder()
                        .dataSource("docRefType", "docRefUuid", "docRefName")
                        .expression(new ExpressionOperator.Builder(Op.AND)
                                .addTerm("field1", Condition.EQUALS, "value1")
                                .addOperator(new ExpressionOperator.Builder(Op.AND).build())
                                .addTerm("field2", Condition.BETWEEN, "value2")
                                .build())
                        .addParam("param1", "val1")
                        .addParam("param2", "val2")
                        .build())
                .addResultRequests(new ResultRequest.Builder()
                        .componentId("componentX")
                        .requestedRange(new OffsetRange.Builder()
                                .offset(1L)
                                .length(100L)
                                .build())
                        .addMappings(new TableSettings.Builder()
                                .queryId("someQueryId")
                                .addFields(new Field.Builder()
                                        .id("id1")
                                        .name("name1")
                                        .expression("expression1")
                                        .sort(new Sort(1, Sort.SortDirection.ASCENDING))
                                        .filter(new Filter("include1", "exclude1"))
                                        .format(new Format(new NumberFormat(1, false)))
                                        .group(1)
                                        .build())
                                .addFields(new Field.Builder()
                                        .id("id2")
                                        .name("name2")
                                        .expression("expression2")
                                        .sort(new Sort(2, Sort.SortDirection.DESCENDING))
                                        .filter(new Filter("include2", "exclude2"))
                                        .format(new Format(createDateTimeFormat()))
                                        .group(2)
                                        .build())
                                .extractValues(false)
                                .extractionPipeline("docRefName2", "docRefUuid2", "docRefType2")
                                .addMaxResults(1, 2)
                                .showDetail(false)
                                .build())
                        .build())
                .dateTimeLocale("en-gb")
                .incremental(true)
                .build();
    }

    private static DateTimeFormat createDateTimeFormat() {
        final TimeZone timeZone = TimeZone.fromOffset(2, 30);
        return new DateTimeFormat("yyyy-MM-dd'T'HH:mm:ss", timeZone);
    }

    @Test
    void testPolymorphic() throws IOException, JAXBException {
        final List<Base> list = new ArrayList<>();
        list.add(new Sub1(2, 5));
        list.add(new Sub2(8, "test"));
        final Lst lst = new Lst(list);

        test(lst, Lst.class, "testPolymorphic");
    }

    @Test
    void testPolymorphic2() throws IOException, JAXBException {
        final List<Object> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        list.add("this");
        list.add(0.5);
        list.add(56.0);
        list.add("that");
        final Multi multi = new Multi(list);

        test(multi, Multi.class, "testPolymorphic2");
    }

    @Test
    void testDataSourceSerialisation() throws IOException, JAXBException {
        test(getDataSource(), DataSource.class, "testDataSourceSerialisation");
    }

    @Test
    void testSearchRequestSerialisation() throws IOException, JAXBException {
        test(getSearchRequest(), SearchRequest.class, "testSearchRequestSerialisation");
    }

    @Test
    void testSearchResponseSerialisation() throws IOException, JAXBException {
        test(getSearchResponse(), SearchResponse.class, "testSearchResponseSerialisation");
    }

    private <T> void test(final T objIn, final Class<T> type, final String testName) throws IOException, JAXBException {
        testJSON(objIn, type, testName);
//        testXML(objIn, type, testName);
    }

    private <T> void testJSON(final T objIn, final Class<T> type, final String testName) throws IOException {
        ObjectMapper mapper = createMapper(true);

        final Path dir = TestFileUtil.getTestResourcesDir().resolve("SerialisationTest");
        Path expectedFile = dir.resolve(testName + "-JSON.expected.json");
        Path actualFileIn = dir.resolve(testName + "-JSON.actual.in.json");
        Path actualFileOut = dir.resolve(testName + "-JSON.actual.out.json");

        String serialisedIn = mapper.writeValueAsString(objIn);
//        System.out.println(serialisedIn);

        if (!Files.isRegularFile(expectedFile)) {
            StreamUtil.stringToFile(serialisedIn, expectedFile);
        }
        StreamUtil.stringToFile(serialisedIn, actualFileIn);

        // Compare serialised form to expected
        final boolean areDifferent = diffFiles(actualFileIn, expectedFile);

        if (areDifferent) {
            System.out.println("\n If you are satisfied that the differences are justified, i.e. the java model has " +
                    "changed then run the following:");
            System.out.println("cp "
                    + actualFileIn.toAbsolutePath().normalize()
                    + " "
                    + expectedFile.toAbsolutePath().normalize());
        }

        final String expected = StreamUtil.fileToString(expectedFile);
        assertEqualsIgnoreWhitespace(expected, serialisedIn);

        // Now deserialise the string from the serialised object
        T objOut = mapper.readValue(serialisedIn, type);
        String serialisedOut = mapper.writeValueAsString(objOut);
//        System.out.println(serialisedOut);
        StreamUtil.stringToFile(serialisedOut, actualFileOut);

        diffFiles(actualFileOut, actualFileOut);

        assertEqualsIgnoreWhitespace(serialisedIn, serialisedOut);
        assertThat(objOut).isEqualTo(objIn);
    }

    /**
     * Show a proper coloured unified diff of the two files
     */
    private boolean diffFiles(final Path actualFile, final Path expectedFile) throws IOException {

        final String expected = Files.readString(expectedFile);
        final String actual = Files.readString(actualFile);

        // The expected file has already had the DW lines removed
        final List<String> expectedLines = expected.lines().collect(Collectors.toList());
        final List<String> actualLines = actual.lines().collect(Collectors.toList());

        // write the actual out so we can compare in other tools
        Files.write(actualFile, actualLines);
        final Patch<String> patch = DiffUtils.diff(expectedLines, actualLines);

        final List<String> unifiedDiff = DiffUtils.generateUnifiedDiff(
                expectedFile.toString(),
                actualFile.toString(),
                expectedLines,
                patch,
                3);

        if (!unifiedDiff.isEmpty()) {
            System.out.println("\n  Differences exist!");

            System.out.println("");
            unifiedDiff.forEach(diffLine -> {

                final ConsoleColour lineColour;
                if (diffLine.startsWith("+")) {
                    lineColour = ConsoleColour.GREEN;
                } else if (diffLine.startsWith("-")) {
                    lineColour = ConsoleColour.RED;
                } else {
                    lineColour = ConsoleColour.NO_COLOUR;
                }

                System.out.println(ConsoleColour.colourise(diffLine, lineColour));
            });
            System.out.println("\n To see the diff in Vim run:");
            System.out.println("vimdiff "
                    + expectedFile.toAbsolutePath().normalize()
                    + " "
                    + actualFile.toAbsolutePath().normalize());
            return true;
        } else {
            System.out.println("\n Files are the same");
            return false;
        }
    }

    private void assertEqualsIgnoreWhitespace(final String expected, final String actual) {
//        final String str1 = removeWhitespace(expected);
//        final String str2 = removeWhitespace(actual);
//        assertThat(str2).isEqualTo(str1);
        assertThat(expected).isEqualToIgnoringWhitespace(actual);
    }

    private String removeWhitespace(final String in) {
        return in.replaceAll("\\s", "");
    }

    private SearchResponse getSearchResponse() {
        final List<Field> fields = Collections.singletonList(new Field.Builder().id("test").name("test").expression("${test}").build());
        final List<String> values = Collections.singletonList("test");
        final List<Row> rows = Collections.singletonList(new Row("groupKey", values, 5));

        final TableResult tableResult = new TableResult("table-1234", fields, rows, new OffsetRange(1, 2), 1, "tableResultError");
        return new SearchResponse(Arrays.asList("highlight1", "highlight2"), Arrays.asList(tableResult, getVisResult1()), Collections.singletonList("some error"), false);
    }

    private FlatResult getVisResult1() {
        final List<Field> structure = new ArrayList<>();
        structure.add(new Field.Builder().id("val1").name("val1").expression("${val1}").format(Type.GENERAL).build());
        structure.add(new Field.Builder().id("val2").name("val2").expression("${val2}").format(Type.NUMBER).build());
        structure.add(new Field.Builder().id("val3").name("val3").expression("${val3}").format(Type.NUMBER).build());
        structure.add(new Field.Builder().id("val4").name("val4").expression("${val4}").format(Type.GENERAL).build());

        final List<List<Object>> data = new ArrayList<>();
        data.add(Arrays.asList("test0", 0.4, 234, "this0"));
        data.add(Arrays.asList("test1", 0.5, 25634, "this1"));
        data.add(Arrays.asList("test2", 0.6, 27, "this2"));
        data.add(Arrays.asList("test3", 0.7, 344, "this3"));
        data.add(Arrays.asList("test4", 0.2, 8984, "this4"));
        data.add(Arrays.asList("test5", 0.33, 3244, "this5"));
        data.add(Arrays.asList("test6", 34.66, 44, "this6"));
        data.add(Arrays.asList("test7", 2.33, 74, "this7"));

        return new FlatResult("vis-1234", structure, data, 200L, "visResultError");
    }

//    private VisResult getVisResult2() {
//        Field[][] structure = new Field[]{new Field("key1", Type.GENERAL, new Field("key2", Type.GENERAL) , new Field("val1", Type.GENERAL), new Field("val2", Type.NUMBER), new Field("val3", Type.NUMBER), new Field("val4", Type.GENERAL)};
//
//        final NodeBuilder nodeBuilder = new NodeBuilder(4);
//        nodeBuilder.addValue(new Object[]{"test0", 0.4, 234, "this0"});
//        nodeBuilder.addValue(new Object[]{"test1", 0.5, 25634, "this1"});
//        nodeBuilder.addValue(new Object[]{"test2", 0.6, 27, "this2"});
//        nodeBuilder.addValue(new Object[]{"test3", 0.7, 344, "this3"});
//        nodeBuilder.addValue(new Object[]{"test4", 0.2, 8984, "this4"});
//        nodeBuilder.addValue(new Object[]{"test5", 0.33, 3244, "this5"});
//        nodeBuilder.addValue(new Object[]{"test6", 34.66, 44, "this6"});
//        nodeBuilder.addValue(new Object[]{"test7", 2.33, 74, "this7"});
//
//        Key parentKey1 = new Key("key1");
//        Key parentKey2 = new Key("key2");
//
//        NodeBuilder innerNode1 = nodeBuilder.copy().setKey(new Key(parentKey1, "innerKey1"));
//        NodeBuilder innerNode2 = nodeBuilder.copy().setKey(new Key(parentKey1, "innerKey2"));
//
//        Node[] nodes = new Node[2];
//        nodes[0] = new NodeBuilder(4).setKey(parentKey1).addNode(innerNode1).addNode(innerNode2).build();
//        nodes[1] = nodeBuilder.setKey(parentKey2).build();
//
//        VisResult visResult = new VisResult("vis-5555", structure, nodes, null, null, null, 200L, "visResultError");
//
//        return visResult;
//    }

    private ObjectMapper createMapper(final boolean indent) {
//        final SimpleModule module = new SimpleModule();
//        module.addSerializer(Double.class, new MyDoubleSerialiser());

        final ObjectMapper mapper = new ObjectMapper();
//        mapper.registerModule(module);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // Enabling default typing adds type information where it would otherwise be ambiguous, i.e. for abstract classes
//        mapper.enableDefaultTyping();

        return mapper;
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type"
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Sub1.class, name = "sub1"),
            @JsonSubTypes.Type(value = Sub2.class, name = "sub2")
    })
    public static abstract class Base {
        @XmlElement
        private int num;

        public Base() {
        }

        public Base(final int num) {
            this.num = num;
        }

        public int getNum() {
            return num;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof Base)) return false;

            final Base base = (Base) o;

            return num == base.num;
        }

        @Override
        public int hashCode() {
            return num;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "sub1", propOrder = {"num2"})
    public static class Sub1 extends Base {
        @XmlElement
        private int num2;

        public Sub1() {
        }

        public Sub1(final int num, final int num2) {
            super(num);
            this.num2 = num2;
        }

        public int getNum2() {
            return num2;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof Sub1)) return false;
            if (!super.equals(o)) return false;

            final Sub1 sub1 = (Sub1) o;

            return num2 == sub1.num2;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + num2;
            return result;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "sub2", propOrder = {"str"})
    public static class Sub2 extends Base {
        @XmlElement
        private String str;

        public Sub2() {
        }

        public Sub2(final int num, final String str) {
            super(num);
            this.str = str;
        }

        public String getStr() {
            return str;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof Sub2)) return false;
            if (!super.equals(o)) return false;

            final Sub2 sub2 = (Sub2) o;

            return str != null ? str.equals(sub2.str) : sub2.str == null;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (str != null ? str.hashCode() : 0);
            return result;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "lst")
    public static class Lst {
        @XmlElementWrapper(name = "list")
        @XmlElements({@XmlElement(name = "sub1", type = Sub1.class),
                @XmlElement(name = "sub2", type = Sub2.class)})
        private List<Base> list;

        public Lst() {
        }

        public Lst(final List<Base> list) {
            this.list = list;
        }

        public List<Base> getList() {
            return list;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof Lst)) return false;

            final Lst lst = (Lst) o;

            return list != null ? list.equals(lst.list) : lst.list == null;
        }

        @Override
        public int hashCode() {
            return list != null ? list.hashCode() : 0;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "multi")
    public static class Multi {
        @XmlElementWrapper(name = "list")
        @XmlElements({@XmlElement(name = "double", type = Double.class),
                @XmlElement(name = "int", type = Integer.class),
                @XmlElement(name = "string", type = String.class)})
        private List<Object> list;

        public Multi() {
        }

        public Multi(final List<Object> list) {
            this.list = list;
        }

        public List<Object> getList() {
            return list;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof Multi)) return false;

            final Multi multi = (Multi) o;

            return list != null ? list.equals(multi.list) : multi.list == null;
        }

        @Override
        public int hashCode() {
            return list != null ? list.hashCode() : 0;
        }
    }
}