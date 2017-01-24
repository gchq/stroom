package stroom.query;



import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.Assert;
import org.junit.Test;
import stroom.query.api.Param;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.DocRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Field;
import stroom.query.api.Filter;
import stroom.query.api.Format;
import stroom.query.api.Key;
import stroom.query.api.Node;
import stroom.query.api.NumberFormat;
import stroom.query.api.OffsetRange;
import stroom.query.api.Row;
import stroom.query.api.Query;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.Sort;
import stroom.query.api.TableResult;
import stroom.query.api.TableResultRequest;
import stroom.query.api.TableSettings;
import stroom.query.api.VisResult;

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
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestSerialisation {
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
    public void testSearchRequestSerialisation() throws IOException, JAXBException {
        test(getSearchRequest(), SearchRequest.class, "testSearchRequestSerialisation");
    }

    @Test
    public void testSearchResponseSerialisation() throws IOException, JAXBException {
        test(getSearchResponse(), SearchResponse.class, "testSearchResponseSerialisation");
    }


    private <T> void test(final T objIn, final Class<T> type, final String testName) throws IOException, JAXBException {
        testJSON(objIn, type, testName);
        testXML(objIn, type, testName);
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

    private <T> void testXML(final T objIn, final Class<T> type, final String testName) throws IOException, JAXBException {
        final JAXBContext context = JAXBContext.newInstance(type);

        final Path dir = TestFileUtil.getTestResourcesDir().resolve("SerialisationTest");
        Path expectedFile = dir.resolve(testName + "-XML.expected.xml");
        Path actualFile = dir.resolve(testName + "-XML.actual.xml");

        final StringWriter stringWriterIn = new StringWriter();
        getMarshaller(context).marshal(objIn, stringWriterIn);

        String serialisedIn = stringWriterIn.toString();
        System.out.println(serialisedIn);

        if (!Files.isRegularFile(expectedFile)) {
            StreamUtil.stringToFile(serialisedIn, expectedFile);
        }
        StreamUtil.stringToFile(serialisedIn, actualFile);

        final String expected = StreamUtil.fileToString(expectedFile);
        Assert.assertEquals(expected, serialisedIn);

        Object objOut = context.createUnmarshaller().unmarshal(new StringReader(serialisedIn));

        final StringWriter stringWriterOut = new StringWriter();
        getMarshaller(context).marshal(objOut, stringWriterOut);

        String serialisedOut = stringWriterOut.toString();
        System.out.println(serialisedOut);

        Assert.assertEquals(serialisedIn, serialisedOut);
        Assert.assertEquals(objIn, objOut);
    }

    private Marshaller getMarshaller(final JAXBContext context) {
        try {
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            return marshaller;
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static SearchRequest getSearchRequest() {
        DocRef docRef = new DocRef("docRefType", "docRefUuid", "docRefName");

        ExpressionOperator expressionOperator = new ExpressionOperator(Op.AND);
        expressionOperator.addTerm("field1", Condition.EQUALS, "value1");
        expressionOperator.addOperator(Op.AND);
        expressionOperator.addTerm("field2", Condition.BETWEEN, "value2");

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
                        new Format(new NumberFormat(2, true)), 2)
        });
        tableSettings.setExtractValues(false);
        tableSettings.setExtractionPipeline(new DocRef("docRefType2", "docRefUuid2", "docRefName2"));
        tableSettings.setMaxResults(new Integer[]{1, 2});
        tableSettings.setShowDetail(false);

//        Map<String, TableSettings> componentSettingsMap = new HashMap<>();
//        componentSettingsMap.put("componentSettingsMapKey", tableSettings);

        final Param[] params = new Param[] {new Param("param1", "val1"), new Param("param2", "val2")};
        final Query query = new Query(docRef, expressionOperator, params);

        final ResultRequest[] resultRequests = new ResultRequest[] {new TableResultRequest("componentX", tableSettings, 1, 100)};

        SearchRequest searchRequest = new SearchRequest(query, resultRequests, "en-gb");

        return searchRequest;
    }

    private SearchResponse getSearchResponse() {
        SearchResponse searchResponse = new SearchResponse();
        searchResponse.setHighlights(new String[] {"highlight1", "highlight2"});
        searchResponse.setErrors(new String[] {"some error"});
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
        searchResponse.setResults(new Result[] {tableResult, getVisResult1(), getVisResult2()});

        return searchResponse;
    }

    private VisResult getVisResult1() {
        Object[][] data = new Object[8][];
        data[0] = new Object[]{"test0", 0.4, 234, "this0"};
        data[1] = new Object[]{"test1", 0.5, 25634, "this1"};
        data[2] = new Object[]{"test2", 0.6, 27, "this2"};
        data[3] = new Object[]{"test3", 0.7, 344, "this3"};
        data[4] = new Object[]{"test4", 0.2, 8984, "this4"};
        data[5] = new Object[]{"test5", 0.33, 3244, "this5"};
        data[6] = new Object[]{"test6", 34.66, 44, "this6"};
        data[7] = new Object[]{"test7", 2.33, 74, "this7"};
        VisResult visResult = new VisResult("vis-1234", new String[]{"string", "double", "integer", "string"}, null, data, null, null, null, 200, "visResultError");

        return visResult;
    }

    private VisResult getVisResult2() {
        Object[][] data = new Object[8][];
        data[0] = new Object[]{"test0", 0.4, 234, "this0"};
        data[1] = new Object[]{"test1", 0.5, 25634, "this1"};
        data[2] = new Object[]{"test2", 0.6, 27, "this2"};
        data[3] = new Object[]{"test3", 0.7, 344, "this3"};
        data[4] = new Object[]{"test4", 0.2, 8984, "this4"};
        data[5] = new Object[]{"test5", 0.33, 3244, "this5"};
        data[6] = new Object[]{"test6", 34.66, 44, "this6"};
        data[7] = new Object[]{"test7", 2.33, 74, "this7"};

        Node[] innerNodes = new Node[2];
        innerNodes[0] = new Node(new Key("string", "innerKey1"), null, data, null, null, null);
        innerNodes[1] = new Node(new Key("string", "innerKey2"), null, data, null, null, null);

        Node[] nodes = new Node[2];
        nodes[0] = new Node(new Key("string", "key1"), innerNodes, null, null, null, null);
        nodes[1] = new Node(new Key("string", "key2"), null, data, null, null, null);

        VisResult visResult = new VisResult("vis-5555", new String[]{"string", "double", "integer", "string"}, nodes, null, null, null, null, 200, "visResultError");

        return visResult;
    }

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
        @XmlElements({ @XmlElement(name = "sub1", type = Sub1.class),
                @XmlElement(name = "sub2", type = Sub2.class) })
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
        @XmlElements({ @XmlElement(name = "double", type = Double.class),
                @XmlElement(name = "int", type = Integer.class),
                @XmlElement(name = "string", type = String.class) })
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