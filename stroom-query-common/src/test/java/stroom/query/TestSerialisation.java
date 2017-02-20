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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.Assert;
import org.junit.Test;
import stroom.datasource.api.DataSource;
import stroom.datasource.api.DataSourceField;
import stroom.datasource.api.DataSourceField.DataSourceFieldType;
import stroom.query.api.DateTimeFormat;
import stroom.query.api.DocRef;
import stroom.query.api.ExpressionBuilder;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Field;
import stroom.query.api.Filter;
import stroom.query.api.FlatResult;
import stroom.query.api.Format;
import stroom.query.api.Format.Type;
import stroom.query.api.NumberFormat;
import stroom.query.api.OffsetRange;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.Sort;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;
import stroom.query.api.TimeZone;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
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
import java.util.List;

public class TestSerialisation {
    private static DataSource getDataSource() {
        final DataSourceField field1 = new DataSourceField();
        field1.setType(DataSourceFieldType.FIELD);
        field1.setName("field1");
        field1.setConditions(Arrays.asList(Condition.EQUALS, Condition.CONTAINS));
        field1.setQueryable(true);

        final DataSourceField field2 = new DataSourceField();
        field2.setType(DataSourceFieldType.NUMERIC_FIELD);
        field2.setName("field2");
        field2.setConditions(Arrays.asList(Condition.EQUALS));
        field2.setQueryable(true);

        final List<DataSourceField> fields = Arrays.asList(field1, field2);
        final DataSource dataSource = new DataSource(fields);

        return dataSource;
    }

    private static SearchRequest getSearchRequest() {
        DocRef docRef = new DocRef("docRefType", "docRefUuid", "docRefName");

        ExpressionBuilder expressionBuilder = new ExpressionBuilder();
        expressionBuilder.addTerm("field1", Condition.EQUALS, "value1");
        expressionBuilder.addOperator(Op.AND);
        expressionBuilder.addTerm("field2", Condition.BETWEEN, "value2");

        TableSettings tableSettings = new TableSettings();
        tableSettings.setQueryId("someQueryId");
        tableSettings.setFields(new Field[]{
                new Field("name1", "expression1",
                        new Sort(1, Sort.SortDirection.ASCENDING),
                        new Filter("include1", "exclude1"),
                        new Format(new NumberFormat(1, false)), 1),
                new Field("name2", "expression2",
                        new Sort(2, Sort.SortDirection.DESCENDING),
                        new Filter("include2", "exclude2"),
                        new Format(createDateTimeFormat()), 2)
        });
        tableSettings.setExtractValues(false);
        tableSettings.setExtractionPipeline(new DocRef("docRefType2", "docRefUuid2", "docRefName2"));
        tableSettings.setMaxResults(new Integer[]{1, 2});
        tableSettings.setShowDetail(false);

//        Map<String, TableSettings> componentSettingsMap = new HashMap<>();
//        componentSettingsMap.put("componentSettingsMapKey", tableSettings);

        final Param[] params = new Param[]{new Param("param1", "val1"), new Param("param2", "val2")};
        final Query query = new Query(docRef, expressionBuilder.build(), params);

        final ResultRequest[] resultRequests = new ResultRequest[]{new ResultRequest("componentX", tableSettings, new OffsetRange(1, 100))};

        SearchRequest searchRequest = new SearchRequest(new QueryKey("1234"), query, resultRequests, "en-gb");

        return searchRequest;
    }

    private static DateTimeFormat createDateTimeFormat() {
        final TimeZone timeZone = TimeZone.fromOffset(2, 30);

        final DateTimeFormat dateTimeFormat = new DateTimeFormat();
        dateTimeFormat.setPattern("yyyy-MM-dd'T'HH:mm:ss");
        dateTimeFormat.setTimeZone(timeZone);

        return dateTimeFormat;
    }

    @Test
    public void testPolymorphic() throws IOException, JAXBException {
        final List<Base> list = new ArrayList<>();
        list.add(new Sub1(2, 5));
        list.add(new Sub2(8, "test"));
        final Lst lst = new Lst(list);

        test(lst, Lst.class, "testPolymorphic");
    }

    @Test
    public void testPolymorphic2() throws IOException, JAXBException {
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
    public void testDataSourceSerialisation() throws IOException, JAXBException {
        test(getDataSource(), DataSource.class, "testDataSourceSerialisation");
    }

    @Test
    public void testSearchRequestSerialisation() throws IOException, JAXBException {
        test(getSearchRequest(), SearchRequest.class, "testSearchRequestSerialisation");
    }

    @Test
    public void testSearchResponseSerialisation() throws IOException, JAXBException {
        test(getSearchResponse(), SearchResponse.class, "testSearchResponseSerialisation");
    }

    private <T> void test(final T objIn, final Class<T> type, final String testName) throws IOException, JAXBException {
        testJSON(objIn, type, testName);
//        testXML(objIn, type, testName);
    }

    private <T> void testJSON(final T objIn, final Class<T> type, final String testName) throws IOException, JAXBException {
        ObjectMapper mapper = createMapper(true);

        final Path dir = TestFileUtil.getTestResourcesDir().resolve("SerialisationTest");
        Path expectedFile = dir.resolve(testName + "-JSON.expected.json");
        Path actualFile = dir.resolve(testName + "-JSON.actual.json");

        String serialisedIn = mapper.writeValueAsString(objIn);
        System.out.println(serialisedIn);

        if (!Files.isRegularFile(expectedFile)) {
            StreamUtil.stringToFile(serialisedIn, expectedFile);
        }
        StreamUtil.stringToFile(serialisedIn, actualFile);

        final String expected = StreamUtil.fileToString(expectedFile);
        Assert.assertEquals(expected, serialisedIn);

        T objOut = mapper.readValue(serialisedIn, type);
        String serialisedOut = mapper.writeValueAsString(objOut);
        System.out.println(serialisedOut);

        Assert.assertEquals(serialisedIn, serialisedOut);
        Assert.assertEquals(objIn, objOut);
    }

//    private <T> void testXML(final T objIn, final Class<T> type, final String testName) throws IOException, JAXBException {
//        final JAXBContext context = JAXBContext.newInstance(type);
//
//        final Path dir = TestFileUtil.getTestResourcesDir().resolve("SerialisationTest");
//        Path expectedFile = dir.resolve(testName + "-XML.expected.xml");
//        Path actualFile = dir.resolve(testName + "-XML.actual.xml");
//
//        final StringWriter stringWriterIn = new StringWriter();
//        getMarshaller(context).marshal(objIn, stringWriterIn);
//
//        String serialisedIn = stringWriterIn.toString();
//        System.out.println(serialisedIn);
//
//        if (!Files.isRegularFile(expectedFile)) {
//            StreamUtil.stringToFile(serialisedIn, expectedFile);
//        }
//        StreamUtil.stringToFile(serialisedIn, actualFile);
//
//        final String expected = StreamUtil.fileToString(expectedFile);
//        Assert.assertEquals(expected, serialisedIn);
//
//        Object objOut = context.createUnmarshaller().unmarshal(new StringReader(serialisedIn));
//
//        final StringWriter stringWriterOut = new StringWriter();
//        getMarshaller(context).marshal(objOut, stringWriterOut);
//
//        String serialisedOut = stringWriterOut.toString();
//        System.out.println(serialisedOut);
//
//        Assert.assertEquals(serialisedIn, serialisedOut);
//        Assert.assertEquals(objIn, objOut);
//    }

    private Marshaller getMarshaller(final JAXBContext context) {
        try {
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            return marshaller;
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private SearchResponse getSearchResponse() {
        SearchResponse searchResponse = new SearchResponse();
        searchResponse.setHighlights(new String[]{"highlight1", "highlight2"});
        searchResponse.setErrors(new String[]{"some error"});
        searchResponse.setComplete(false);

        TableResult tableResult = new TableResult("table-1234");
        tableResult.setError("tableResultError");
        tableResult.setTotalResults(1);
        tableResult.setResultRange(new OffsetRange(1, 2));
        List<Row> rows = new ArrayList<>();
        String[] values = new String[1];
        values[0] = "test";
        rows.add(new Row("groupKey", values, 5));
        Row[] arr = new Row[rows.size()];
        arr = rows.toArray(arr);
        tableResult.setRows(arr);
        searchResponse.setResults(new Result[]{tableResult, getVisResult1()});

        return searchResponse;
    }

    private FlatResult getVisResult1() {
        List<Field> structure = new ArrayList<>();
        structure.add(new Field("val1", Type.GENERAL));
        structure.add(new Field("val2", Type.NUMBER));
        structure.add(new Field("val3", Type.NUMBER));
        structure.add(new Field("val4", Type.GENERAL));

        List<List<Object>> data = new ArrayList<>();
        data.add(Arrays.asList("test0", 0.4, 234, "this0"));
        data.add(Arrays.asList("test1", 0.5, 25634, "this1"));
        data.add(Arrays.asList("test2", 0.6, 27, "this2"));
        data.add(Arrays.asList("test3", 0.7, 344, "this3"));
        data.add(Arrays.asList("test4", 0.2, 8984, "this4"));
        data.add(Arrays.asList("test5", 0.33, 3244, "this5"));
        data.add(Arrays.asList("test6", 34.66, 44, "this6"));
        data.add(Arrays.asList("test7", 2.33, 74, "this7"));
        FlatResult visResult = new FlatResult("vis-1234", structure, data, 200L, "visResultError");

        return visResult;
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
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
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