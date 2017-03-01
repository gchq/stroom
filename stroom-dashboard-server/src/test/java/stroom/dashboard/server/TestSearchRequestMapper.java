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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.Assert;
import org.junit.Test;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.shared.DateTimeFormatSettings;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.Filter;
import stroom.dashboard.shared.Format;
import stroom.dashboard.shared.Format.Type;
import stroom.dashboard.shared.NumberFormatSettings;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.SearchRequest;
import stroom.dashboard.shared.Sort;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TableResultRequest;
import stroom.dashboard.shared.TimeZone;
import stroom.entity.shared.SharedDocRef;
import stroom.query.api.DocRef;
import stroom.query.api.ExpressionBuilder;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class TestSearchRequestMapper {
    private static SearchRequest getSearchRequest() {
        DocRef docRef = new DocRef("docRefType", "docRefUuid", "docRefName");

        ExpressionBuilder expressionOperator = new ExpressionBuilder(Op.AND);
        expressionOperator.addTerm("field1", Condition.EQUALS, "value1");
        expressionOperator.addOperator(Op.AND);
        expressionOperator.addTerm("field2", Condition.BETWEEN, "value2");

        TableComponentSettings tableSettings = new TableComponentSettings();
        tableSettings.setQueryId("someQueryId");
        tableSettings.addField(new Field("name1", "expression1",
                new Sort(1, Sort.SortDirection.ASCENDING),
                new Filter("include1", "exclude1"),
                new Format(Type.NUMBER, new NumberFormatSettings(1, false)), 1, 200, true));
        tableSettings.addField(new Field("name2", "expression2",
                new Sort(2, Sort.SortDirection.DESCENDING),
                new Filter("include2", "exclude2"),
                new Format(Type.DATE_TIME, createDateTimeFormat()), 2, 200, true));
        tableSettings.setExtractValues(false);
        tableSettings.setExtractionPipeline(new SharedDocRef("docRefType2", "docRefUuid2", "docRefName2"));
        tableSettings.setMaxResults(new int[]{1, 2});
        tableSettings.setShowDetail(false);

        Map<String, ComponentSettings> componentSettingsMap = new HashMap<>();
        componentSettingsMap.put("componentSettingsMapKey", tableSettings);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("param1", "val1");
        paramMap.put("param2", "val2");

        final Search search = new Search(docRef, expressionOperator.build(), componentSettingsMap, paramMap, true);

        final Map<String, ComponentResultRequest> componentResultRequestMap = new HashMap<>();
        for (final Entry<String, ComponentSettings> entry : componentSettingsMap.entrySet()) {
            TableResultRequest tableResultRequest = new TableResultRequest();
            tableResultRequest.setTableSettings((TableComponentSettings) entry.getValue());
            tableResultRequest.setWantsData(true);
            componentResultRequestMap.put(entry.getKey(), tableResultRequest);
        }

        SearchRequest searchRequest = new SearchRequest(search, componentResultRequestMap, "en-gb");

        return searchRequest;
    }

    private static DateTimeFormatSettings createDateTimeFormat() {
        final TimeZone timeZone = TimeZone.fromOffset(2, 30);

        final DateTimeFormatSettings dateTimeFormat = new DateTimeFormatSettings();
        dateTimeFormat.setPattern("yyyy-MM-dd'T'HH:mm:ss");
        dateTimeFormat.setTimeZone(timeZone);

        return dateTimeFormat;
    }

    @Test
    public void testRequest() throws Exception {
        final SearchRequestMapper mapper = new SearchRequestMapper(new MockVisualisationService());
        final stroom.query.api.SearchRequest result = mapper.mapRequest(DashboardQueryKey.create("test", 1), getSearchRequest());

        test(result, stroom.query.api.SearchRequest.class, "testRequest");
    }

    private <T> void test(final T objIn, final Class<T> type, final String testName) throws IOException, JAXBException {
        testJSON(objIn, type, testName);
        testXML(objIn, type, testName);
    }

    private <T> void testJSON(final T objIn, final Class<T> type, final String testName) throws IOException, JAXBException {
        ObjectMapper mapper = createMapper(true);

        final Path dir = TestFileUtil.getTestResourcesDir().resolve("TestSearchRequestMapper");
        Path expectedFile = dir.resolve(testName + "-JSON.expected.json");
        Path actualFile = dir.resolve(testName + "-JSON.actual.json");

        String serialisedIn = mapper.writeValueAsString(objIn);
        System.out.println(serialisedIn);

        if (!Files.isRegularFile(expectedFile)) {
            StreamUtil.stringToFile(serialisedIn, expectedFile);
        }
        StreamUtil.stringToFile(serialisedIn, actualFile);

        final String expected = StreamUtil.fileToString(expectedFile);
        assertEqualsIgnoreWhitespace(expected, serialisedIn);

        T objOut = mapper.readValue(serialisedIn, type);
        String serialisedOut = mapper.writeValueAsString(objOut);
        System.out.println(serialisedOut);

        assertEqualsIgnoreWhitespace(serialisedIn, serialisedOut);
        Assert.assertEquals(objIn, objOut);
    }

    private <T> void testXML(final T objIn, final Class<T> type, final String testName) throws IOException, JAXBException {
        final JAXBContext context = JAXBContext.newInstance(type);

        final Path dir = TestFileUtil.getTestResourcesDir().resolve("TestSearchRequestMapper");
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

    private void assertEqualsIgnoreWhitespace(final String expected, final String actual) {
        final String str1 = removeWhitespace(expected);
        final String str2 = removeWhitespace(actual);
        Assert.assertEquals(str1, str2);
    }

    private String removeWhitespace(final String in) {
        return in.replaceAll("\\s", "");
    }
}
