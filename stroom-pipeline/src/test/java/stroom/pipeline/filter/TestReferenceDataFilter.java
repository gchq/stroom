/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.pipeline.filter;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.sun.xml.fastinfoset.sax.SAXDocumentParser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.fastinfoset.FastInfosetException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import stroom.entity.shared.Range;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.util.ProcessorUtil;
import stroom.refdata.RefDataLoaderHolder;
import stroom.refdata.offheapstore.ByteBufferPool;
import stroom.refdata.offheapstore.FastInfosetValue;
import stroom.refdata.offheapstore.PooledByteBufferOutputStream;
import stroom.refdata.offheapstore.RefDataLoader;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.refdata.offheapstore.StringValue;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestReferenceDataFilter extends StroomUnitTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestReferenceDataFilter.class);

    private static final String BASE_PATH = "TestReferenceDataFilter/";
    private static final String INPUT_STRING_VALUE_1 = BASE_PATH + "input_StringValue_1.xml";
    private static final String INPUT_STRING_VALUE_2 = BASE_PATH + "input_StringValue_2.xml";
    private static final String INPUT_FAST_INFOSET_VALUE_1 = BASE_PATH + "input_FastInfosetValue_1.xml";
    private static final String INPUT_FAST_INFOSET_VALUE_2 = BASE_PATH + "input_FastInfosetValue_2.xml";
    private static final String INPUT_FAST_INFOSET_VALUE_3 = BASE_PATH + "input_FastInfosetValue_3.xml";

    private static final int BUF_SIZE = 4096;

    @Mock
    private RefDataLoader refDataLoader;
    @Captor
    private ArgumentCaptor<RefDataValue> keyValueValueCaptor;
    @Captor
    private ArgumentCaptor<RefDataValue> rangeValueValueCaptor;
    @Captor
    private ArgumentCaptor<String> keyValueKeyCaptor;
    @Captor
    private ArgumentCaptor<Range<Long>> rangeValueKeyCaptor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testStringKeyValues() {

        LoadedRefDataValues loadedRefDataValues = doTest(INPUT_STRING_VALUE_1, null);

        assertThat(loadedRefDataValues.keyValueValues).hasOnlyElementsOfType(StringValue.class);
        assertThat(loadedRefDataValues.rangeValueValues).isEmpty();

        assertThat(loadedRefDataValues.keyValueKeys)
                .containsExactly("key11", "key12", "key13", "key21", "key22", "key23");

        assertThat(
                loadedRefDataValues.keyValueValues.stream()
                        .map(refDataValue -> (StringValue) refDataValue)
                        .map(stringValue -> stringValue.getValue()))
                .containsExactly("value11", "value12", "value13", "value21", "value22", "value23");
    }

    @Test
    public void testStringRangeValues() {

        LoadedRefDataValues loadedRefDataValues = doTest(INPUT_STRING_VALUE_2, null);

        assertThat(loadedRefDataValues.keyValueValues).isEmpty();
        assertThat(loadedRefDataValues.rangeValueValues).hasOnlyElementsOfType(StringValue.class);

        assertThat(
                loadedRefDataValues.rangeValueKeys)
                .containsExactly(
                        new Range<>(1L, 11L),
                        new Range<>(11L, 21L),
                        new Range<>(21L, 31L),
                        new Range<>(1L, 11L),
                        new Range<>(11L, 21L),
                        new Range<>(21L, 31L));
        assertThat(
                loadedRefDataValues.rangeValueValues.stream()
                        .map(refDataValue -> (StringValue) refDataValue)
                        .map(stringValue -> stringValue.getValue()))
                .containsExactly("value11", "value12", "value13", "value21", "value22", "value23");
    }

    @Test
    public void testFastInfosetKeyValues() {

        LoadedRefDataValues loadedRefDataValues = doTest(INPUT_FAST_INFOSET_VALUE_1, null);

        assertThat(loadedRefDataValues.keyValueValues).hasOnlyElementsOfType(FastInfosetValue.class);
        assertThat(loadedRefDataValues.keyValueValues).hasSize(6);
        assertThat(loadedRefDataValues.rangeValueValues).isEmpty();

        loadedRefDataValues.keyValueValues.stream()
                .map(refDataValue -> (FastInfosetValue) refDataValue)
                .map(this::deserialise)
                .forEach(str -> {
                    LOGGER.info("Dumping deserialised output");
                    System.out.println(str);
                });
        Pattern pattern = Pattern.compile("room[0-9]+");

        List<String> roomList = loadedRefDataValues.keyValueValues.stream()
                .map(refDataValue -> (FastInfosetValue) refDataValue)
                .map(this::deserialise)
                .map(str -> {
                    Matcher matcher = pattern.matcher(str);
                    assertThat(matcher.find()).isTrue();
                    return matcher.group();
                })
                .collect(Collectors.toList());

        assertThat(roomList)
                .containsExactly("room11", "room12", "room13", "room21", "room22", "room23");
    }

    @Test
    public void testFastInfosetKeyValues_localPrefixes() {

        LoadedRefDataValues loadedRefDataValues = doTest(INPUT_FAST_INFOSET_VALUE_2, null);

        assertThat(loadedRefDataValues.keyValueValues).hasOnlyElementsOfType(FastInfosetValue.class);
        assertThat(loadedRefDataValues.keyValueValues).hasSize(6);
        assertThat(loadedRefDataValues.rangeValueValues).isEmpty();

        loadedRefDataValues.keyValueValues.stream()
                .map(refDataValue -> (FastInfosetValue) refDataValue)
                .map(this::deserialise)
                .forEach(str -> {
                    LOGGER.info("Dumping deserialised output");
                    System.out.println(str);
                });
        Pattern pattern = Pattern.compile("room[0-9]+");

        List<String> roomList = loadedRefDataValues.keyValueValues.stream()
                .map(refDataValue -> (FastInfosetValue) refDataValue)
                .map(this::deserialise)
                .map(str -> {
                    Matcher matcher = pattern.matcher(str);
                    assertThat(matcher.find()).isTrue();
                    return matcher.group();
                })
                .collect(Collectors.toList());

        assertThat(roomList)
                .containsExactly("room11", "room12", "room13", "room21", "room22", "room23");
    }

    @Test
    public void testFastInfosetRangeValues() {

        LoadedRefDataValues loadedRefDataValues = doTest(INPUT_FAST_INFOSET_VALUE_3, null);

        assertThat(loadedRefDataValues.keyValueValues).isEmpty();
        assertThat(loadedRefDataValues.rangeValueValues).hasOnlyElementsOfType(FastInfosetValue.class);
        assertThat(loadedRefDataValues.rangeValueValues).hasSize(6);

        loadedRefDataValues.rangeValueValues.stream()
                .map(refDataValue -> (FastInfosetValue) refDataValue)
                .map(this::deserialise)
                .forEach(str -> {
                    LOGGER.info("Dumping deserialised output");
                    System.out.println(str);
                });

        assertThat(
                loadedRefDataValues.rangeValueKeys)
                .containsExactly(
                        new Range<>(1L, 11L),
                        new Range<>(11L, 21L),
                        new Range<>(21L, 31L),
                        new Range<>(1L, 11L),
                        new Range<>(11L, 21L),
                        new Range<>(21L, 31L));

        Pattern pattern = Pattern.compile("room[0-9]+");

        List<String> roomList = loadedRefDataValues.rangeValueValues.stream()
                .map(refDataValue -> (FastInfosetValue) refDataValue)
                .map(this::deserialise)
                .map(str -> {
                    Matcher matcher = pattern.matcher(str);
                    assertThat(matcher.find()).isTrue();
                    return matcher.group();
                })
                .collect(Collectors.toList());

        assertThat(roomList)
                .containsExactly("room11", "room12", "room13", "room21", "room22", "room23");
    }

    private LoadedRefDataValues doTest(String inputPath, String expectedOutputPath) {

        Mockito.when(refDataLoader.getRefStreamDefinition())
                .thenReturn(buildUniqueRefStreamDefinition());
        Mockito.when(refDataLoader.initialise(Mockito.anyBoolean()))
                .thenReturn(true);
        Mockito.when(refDataLoader.put(Mockito.any(), keyValueKeyCaptor.capture(), keyValueValueCaptor.capture()))
                .thenReturn(true);
        Mockito.when(refDataLoader.put(Mockito.any(), rangeValueKeyCaptor.capture(), rangeValueValueCaptor.capture()))
                .thenReturn(true);

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());
        RefDataLoaderHolder refDataLoaderHolder = new RefDataLoaderHolder();
        refDataLoaderHolder.setRefDataLoader(refDataLoader);

        final ReferenceDataFilter referenceDataFilter = new ReferenceDataFilter(
                errorReceiverProxy, refDataLoaderHolder, cap -> new PooledByteBufferOutputStream(new ByteBufferPool(), cap));

        final TestFilter testFilter = new TestFilter(null, null);

        final TestSAXEventFilter testSAXEventFilter = new TestSAXEventFilter();

        referenceDataFilter.setTarget(testFilter);
        testFilter.setTarget(testSAXEventFilter);

        ProcessorUtil.processXml(
                input,
                new ErrorReceiverProxy(new FatalErrorReceiver()),
                referenceDataFilter,
                new LocationFactoryProxy());

        final List<String> actualXmlList = testFilter.getOutputs()
                .stream()
                .map(String::trim)
                .map(s -> s.replaceAll("\r", ""))
                .collect(Collectors.toList());

        actualXmlList.forEach(System.out::println);

        List<RefDataValue> refDataValues = keyValueValueCaptor.getAllValues();

        LoadedRefDataValues loadedRefDataValues = new LoadedRefDataValues(
                keyValueKeyCaptor.getAllValues(),
                keyValueValueCaptor.getAllValues(),
                rangeValueValueCaptor.getAllValues(),
                rangeValueKeyCaptor.getAllValues());


        final String actualSax = testSAXEventFilter.getOutput().trim();

        LOGGER.info("Actual SAX: \n {}", actualSax);

        return loadedRefDataValues;
    }

    private String getString(final String resourceName) {
        try {
            final InputStream is = StroomPipelineTestFileUtil.getInputStream(resourceName);

            final byte[] buffer = new byte[BUF_SIZE];
            int len;
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }

            String str = baos.toString();
            str = str.replaceAll("\r", "");
            return str.trim();
        } catch (final IOException e) {
            e.printStackTrace();
        }

        return null;
    }


    private String deserialise(final FastInfosetValue fastInfosetValue) {
        TestSAXEventFilter testSAXEventFilter = new TestSAXEventFilter();
        TestFilter testFilter = new TestFilter(new ErrorReceiverProxy(), new LocationFactoryProxy());
        testFilter.setContentHandler(new MyContentHandler());

        SAXDocumentParser saxDocumentParser = new SAXDocumentParser();
        // it may be possible to only deal with fragments but can't seem to serialise without calling startDocument
//        saxDocumentParser.setParseFragments(true);
        saxDocumentParser.setContentHandler(testSAXEventFilter);
        try {
            saxDocumentParser.parse(new ByteBufferInputStream(fastInfosetValue.getByteBuffer()));
        } catch (IOException | FastInfosetException | SAXException e) {
            throw new RuntimeException(e);
        }

        return testSAXEventFilter.getOutput();
    }

    private RefStreamDefinition buildUniqueRefStreamDefinition() {
        return new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123456L);
    }


    private static class MyContentHandler implements ContentHandler {

        @Override
        public void setDocumentLocator(final Locator locator) {

        }

        @Override
        public void startDocument() throws SAXException {

        }

        @Override
        public void endDocument() throws SAXException {

        }

        @Override
        public void startPrefixMapping(final String prefix, final String uri) throws SAXException {

        }

        @Override
        public void endPrefixMapping(final String prefix) throws SAXException {

        }

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes atts) throws SAXException {

        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) throws SAXException {

        }

        @Override
        public void characters(final char[] ch, final int start, final int length) throws SAXException {

        }

        @Override
        public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {

        }

        @Override
        public void processingInstruction(final String target, final String data) throws SAXException {

        }

        @Override
        public void skippedEntity(final String name) throws SAXException {

        }
    }

    private static class LoadedRefDataValues {
        List<String> keyValueKeys;
        List<RefDataValue> keyValueValues;
        List<Range<Long>> rangeValueKeys;
        List<RefDataValue> rangeValueValues;

        LoadedRefDataValues(final List<String> keyValueKeys,
                            final List<RefDataValue> keyValueValues,
                            final List<RefDataValue> rangeValueValues,
                            final List<Range<Long>> rangeValueKeys) {
            this.keyValueKeys = keyValueKeys == null ? Collections.emptyList() : keyValueKeys;
            this.keyValueValues = keyValueValues == null ? Collections.emptyList() : keyValueValues;
            this.rangeValueValues = rangeValueValues == null ? Collections.emptyList() : rangeValueValues;
            this.rangeValueKeys = rangeValueKeys == null ? Collections.emptyList() : rangeValueKeys;

            assertThat(this.keyValueKeys.size()).isEqualTo(this.keyValueValues.size());
            assertThat(this.rangeValueKeys.size()).isEqualTo(this.rangeValueValues.size());
        }
    }
}