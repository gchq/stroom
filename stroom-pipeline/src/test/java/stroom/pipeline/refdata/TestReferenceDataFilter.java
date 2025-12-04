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

package stroom.pipeline.refdata;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.filter.TestFilter;
import stroom.pipeline.filter.TestSAXEventFilter;
import stroom.pipeline.refdata.store.ByteBufferConsumerId;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.GenericRefDataValueProxyConsumer;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.MultiRefDataValueProxy;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefDataStore.StorageType;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefDataValueProxyConsumerFactory;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.StagingValueOutputStream;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.XxHashValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.offheapstore.FastInfosetByteBufferConsumer;
import stroom.pipeline.refdata.store.offheapstore.OffHeapRefDataValueProxyConsumer;
import stroom.pipeline.refdata.store.offheapstore.RefDataValueProxyConsumer;
import stroom.pipeline.refdata.store.offheapstore.TypedByteBuffer;
import stroom.pipeline.util.ProcessorUtil;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.sun.xml.fastinfoset.sax.SAXDocumentParser;
import jakarta.validation.constraints.NotNull;
import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.serialize.XMLEmitter;
import net.sf.saxon.trans.XPathException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.fastinfoset.FastInfosetException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestReferenceDataFilter extends StroomUnitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestReferenceDataFilter.class);

    private static final String BASE_PATH = "TestReferenceDataFilter/";
    private static final String INPUT_STRING_VALUE_1 = BASE_PATH + "input_StringValue_1.xml";
    private static final String INPUT_STRING_VALUE_2 = BASE_PATH + "input_StringValue_2.xml";
    private static final String INPUT_FAST_INFOSET_VALUE_1 = BASE_PATH + "input_FastInfosetValue_1.xml";
    private static final String INPUT_FAST_INFOSET_VALUE_2 = BASE_PATH + "input_FastInfosetValue_2.xml";
    private static final String INPUT_FAST_INFOSET_VALUE_3 = BASE_PATH + "input_FastInfosetValue_3.xml";
    private static final String INPUT_FAST_INFOSET_VALUE_4 = BASE_PATH + "input_FastInfosetValue_4.xml";
    private static final String INPUT_FAST_INFOSET_VALUE_5 = BASE_PATH + "input_FastInfosetValue_5.xml";
    private static final String INPUT_FAST_INFOSET_VALUE_6 = BASE_PATH + "input_FastInfosetValue_6.xml";
    private static final String INPUT_FAST_INFOSET_VALUE_7 = BASE_PATH + "input_FastInfosetValue_7.xml";
    private static final String INPUT_FAST_INFOSET_VALUE_8 = BASE_PATH + "input_FastInfosetValue_8.xml";

    private static final int BUF_SIZE = 4096;

    @Mock
    private RefDataLoader refDataLoader;

    private ByteBufferPool getByteBufferPool() {
        return new ByteBufferPoolFactory().getByteBufferPool();
    }

    @Test
    void testStringKeyValues() {

        final LoadedRefDataValues loadedRefDataValues = doTest(INPUT_STRING_VALUE_1, null);

        assertThat(loadedRefDataValues.keyValueValues)
                .extracting(StagingValue::getTypeId)
                .containsOnly(StringValue.TYPE_ID);
        assertThat(loadedRefDataValues.rangeValueValues).isEmpty();

        assertThat(loadedRefDataValues.keyValueKeys)
                .containsExactly("key11", "key12", "key13", "key21", "key22", "key23");

        assertThat(
                loadedRefDataValues.keyValueValues.stream()
                        .map(StringValue::new)
                        .map(StringValue::getValue))
                .containsExactly("value11", "value12", "value13", "value21", "value22", "value23");
    }

    @Test
    void testStringRangeValues() {

        final LoadedRefDataValues loadedRefDataValues = doTest(INPUT_STRING_VALUE_2, null);

        assertThat(loadedRefDataValues.keyValueValues).isEmpty();

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
                        .map(StringValue::new)
                        .map(StringValue::getValue))
                .containsExactly("value11", "value12", "value13", "value21", "value22", "value23");
    }

    @Test
    void testFastInfoset_1_KeyValues() {

        final LoadedRefDataValues loadedRefDataValues = doTest(INPUT_FAST_INFOSET_VALUE_1, null);

        assertThat(loadedRefDataValues.keyValueValues)
                .extracting(StagingValue::getTypeId)
                .containsOnly(FastInfosetValue.TYPE_ID);
        assertThat(loadedRefDataValues.keyValueValues).hasSize(6);
        assertThat(loadedRefDataValues.rangeValueValues).isEmpty();

        loadedRefDataValues.keyValueValues.stream()
                .map(FastInfosetValue::new)
                .peek(fastInfosetValue -> {
                    LOGGER.info("Dumping fastinfoset:\n{}", deserialise(fastInfosetValue));
                })
                .forEach(fastInfosetValue -> {

                    consumeFastInfoset(fastInfosetValue, "" +
                                                         "<\\?xml version=\"1\\.0\" encoding=\"UTF-8\"\\?>" +
                                                         "<evt:Location xmlns:evt=\"event-logging:3\">(.|\\n)*" +
                                                         "<evt:Room>room[0-9]+<\\/evt:Room>" +
                                                         "<evt:Desk>desk[0-9]+<\\/evt:Desk>" +
                                                         "<evt:Value>value[0-9]+<\\/evt:Value>" +
                                                         "<\\/evt:Location>");
                });
        final Pattern pattern = Pattern.compile("room[0-9]+");

        final List<String> roomList = loadedRefDataValues.keyValueValues.stream()
                .map(FastInfosetValue::new)
                .map(this::deserialise)
                .map(str -> {
                    final Matcher matcher = pattern.matcher(str);
                    assertThat(matcher.find()).isTrue();
                    return matcher.group();
                })
                .collect(Collectors.toList());

        assertThat(roomList)
                .containsExactly("room11", "room12", "room13", "room21", "room22", "room23");
    }


    @Test
    void testFastInfoset_2_KeyValues_localPrefixes() {

        final LoadedRefDataValues loadedRefDataValues = doTest(INPUT_FAST_INFOSET_VALUE_2, null);

        assertThat(loadedRefDataValues.keyValueValues)
                .extracting(StagingValue::getTypeId)
                .containsOnly(FastInfosetValue.TYPE_ID);
        assertThat(loadedRefDataValues.keyValueValues).hasSize(6);
        assertThat(loadedRefDataValues.rangeValueValues).isEmpty();

        loadedRefDataValues.keyValueValues.stream()
                .map(FastInfosetValue::new)
                .peek(fastInfosetValue -> {
                    LOGGER.info("Dumping fastinfoset:\n{}", deserialise(fastInfosetValue));
                })
                .forEach(fastInfosetValue -> {

                    consumeFastInfoset(fastInfosetValue, "" +
                                                         "<\\?xml version=\"1\\.0\" encoding=\"UTF-8\"\\?>" +
                                                         "<evt:Location xmlns:evt=\"event-logging:3\">(.|\\n)*" +
                                                         "<evt:Room>room[0-9]+<\\/evt:Room>" +
                                                         "<evt:Desk>desk[0-9]+<\\/evt:Desk>" +
                                                         "<evt:Value>value[0-9]+<\\/evt:Value>" +
                                                         "<\\/evt:Location>");
                });
        final Pattern pattern = Pattern.compile("room[0-9]+");

        final List<String> roomList = loadedRefDataValues.keyValueValues.stream()
                .map(FastInfosetValue::new)
                .map(this::deserialise)
                .map(str -> {
                    final Matcher matcher = pattern.matcher(str);
                    assertThat(matcher.find()).isTrue();
                    return matcher.group();
                })
                .collect(Collectors.toList());

        assertThat(roomList)
                .containsExactly("room11", "room12", "room13", "room21", "room22", "room23");
    }


    @Test
    void testFastInfoset_3_RangeValues() {

        final LoadedRefDataValues loadedRefDataValues = doTest(INPUT_FAST_INFOSET_VALUE_3, null);

        assertThat(loadedRefDataValues.keyValueValues).isEmpty();
        assertThat(loadedRefDataValues.rangeValueValues)
                .extracting(StagingValue::getTypeId)
                .containsOnly(FastInfosetValue.TYPE_ID);
        assertThat(loadedRefDataValues.rangeValueValues).hasSize(6);

        loadedRefDataValues.rangeValueValues.stream()
                .map(FastInfosetValue::new)
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

        final Pattern pattern = Pattern.compile("room[0-9]+");

        final List<String> roomList = loadedRefDataValues.rangeValueValues.stream()
                .map(FastInfosetValue::new)
                .map(this::deserialise)
                .map(str -> {
                    final Matcher matcher = pattern.matcher(str);
                    assertThat(matcher.find()).isTrue();
                    return matcher.group();
                })
                .collect(Collectors.toList());

        assertThat(roomList)
                .containsExactly("room11", "room12", "room13", "room21", "room22", "room23");
    }

    @Test
    void testFastInfoset_4_KeyValues_defaultNamespace() {

        final LoadedRefDataValues loadedRefDataValues = doTest(INPUT_FAST_INFOSET_VALUE_4, null);

        assertThat(loadedRefDataValues.keyValueValues)
                .extracting(StagingValue::getTypeId)
                .containsOnly(FastInfosetValue.TYPE_ID);
        assertThat(loadedRefDataValues.keyValueValues).hasSize(1);
        assertThat(loadedRefDataValues.rangeValueValues).isEmpty();

        loadedRefDataValues.keyValueValues.stream()
                .map(FastInfosetValue::new)
                .peek(fastInfosetValue -> {
                    LOGGER.info("Dumping fastinfoset:\n{}", deserialise(fastInfosetValue));
                })
                .forEach(fastInfosetValue -> {

                    consumeFastInfoset(fastInfosetValue, "" +
                                                         "<\\?xml version=\"1\\.0\" encoding=\"UTF-8\"\\?>" +
                                                         "<Location xmlns=\"stroom\">(.|\\n)*" +
                                                         "<Room>room[0-9]+<\\/Room>" +
                                                         "<Desk>desk[0-9]+<\\/Desk>" +
                                                         "<Value>value[0-9]+<\\/Value>" +
                                                         "<\\/Location>");
                });
        final Pattern pattern = Pattern.compile("room[0-9]+");

        final List<String> roomList = loadedRefDataValues.keyValueValues.stream()
                .map(FastInfosetValue::new)
                .map(this::deserialise)
                .map(str -> {
                    final Matcher matcher = pattern.matcher(str);
                    assertThat(matcher.find()).isTrue();
                    return matcher.group();
                })
                .collect(Collectors.toList());

        assertThat(roomList)
                .containsExactly("room11");
    }

    @Test
    void testFastInfoset_5_KeyValues_sameNamespaceAsOuter() {

        final LoadedRefDataValues loadedRefDataValues = doTest(INPUT_FAST_INFOSET_VALUE_5, null);

        assertThat(loadedRefDataValues.keyValueValues)
                .extracting(StagingValue::getTypeId)
                .containsOnly(FastInfosetValue.TYPE_ID);
        assertThat(loadedRefDataValues.keyValueValues).hasSize(1);
        assertThat(loadedRefDataValues.rangeValueValues).isEmpty();

        loadedRefDataValues.keyValueValues.stream()
                .map(FastInfosetValue::new)
                .peek(fastInfosetValue -> {
                    LOGGER.info("Dumping fastinfoset:\n{}", deserialise(fastInfosetValue));
                })
                .forEach(fastInfosetValue -> {

                    consumeFastInfoset(fastInfosetValue, "" +
                                                         "<\\?xml version=\"1\\.0\" encoding=\"UTF-8\"\\?>" +
                                                         "<Location xmlns=\"reference-data:2\">(.|\\n)*" +
                                                         "<Room>room[0-9]+<\\/Room>" +
                                                         "<Desk>desk[0-9]+<\\/Desk>" +
                                                         "<Value>value[0-9]+<\\/Value>" +
                                                         "<\\/Location>");
                });
        final Pattern pattern = Pattern.compile("room[0-9]+");

        final List<String> roomList = loadedRefDataValues.keyValueValues.stream()
                .map(FastInfosetValue::new)
                .map(this::deserialise)
                .map(str -> {
                    final Matcher matcher = pattern.matcher(str);
                    assertThat(matcher.find())
                            .isTrue();
                    return matcher.group();
                })
                .collect(Collectors.toList());

        assertThat(roomList)
                .containsExactly("room11");
    }

    @Test
    void testFastInfoset_6_KeyValues_sameNamespaceAsOuter() {

        final LoadedRefDataValues loadedRefDataValues = doTest(INPUT_FAST_INFOSET_VALUE_6, null);

        assertThat(loadedRefDataValues.keyValueValues)
                .extracting(StagingValue::getTypeId)
                .containsOnly(FastInfosetValue.TYPE_ID);
        assertThat(loadedRefDataValues.keyValueValues).hasSize(1);
        assertThat(loadedRefDataValues.rangeValueValues).isEmpty();

        loadedRefDataValues.keyValueValues.stream()
                .map(FastInfosetValue::new)
                .peek(fastInfosetValue -> {
                    LOGGER.info("Dumping fastinfoset:\n{}", deserialise(fastInfosetValue));
                })
                .forEach(fastInfosetValue -> {

                    consumeFastInfoset(fastInfosetValue, "" +
                                                         "<\\?xml version=\"1\\.0\" encoding=\"UTF-8\"\\?>" +
                                                         "<Location " +
                                                         "xmlns=\"stroom\" " +
                                                         "xmlns:s=\"stroom\" " +
                                                         "xmlns:xxx=\"extra-namespace\">(.|\\n)*" +
                                                         "<s:Room xmlns:yyy=\"another-namespace\" attr1=\"123\" " +
                                                         "xxx:attr2=\"456\" yyy:attr3=\"789\">room[0-9]+<\\/s:Room>" +
                                                         "<xxx:Desk>desk[0-9]+<\\/xxx:Desk>" +
                                                         "<xxx:Value>value[0-9]+<\\/xxx:Value><\\/Location>");
                });
        final Pattern pattern = Pattern.compile("room[0-9]+");

        final List<String> roomList = loadedRefDataValues.keyValueValues.stream()
                .map(FastInfosetValue::new)
                .map(this::deserialise)
                .map(str -> {
                    final Matcher matcher = pattern.matcher(str);
                    assertThat(matcher.find()).isTrue();
                    return matcher.group();
                })
                .collect(Collectors.toList());

        assertThat(roomList)
                .containsExactly("room11");
    }

    @Test
    void testFastInfoset_7_KeyValues_nestedValueElements() {

        final LoadedRefDataValues loadedRefDataValues = doTest(INPUT_FAST_INFOSET_VALUE_7, null);

        assertThat(loadedRefDataValues.keyValueValues)
                .extracting(StagingValue::getTypeId)
                .containsOnly(FastInfosetValue.TYPE_ID);
        assertThat(loadedRefDataValues.keyValueValues).hasSize(1);
        assertThat(loadedRefDataValues.rangeValueValues).isEmpty();

        loadedRefDataValues.keyValueValues.stream()
                .map(FastInfosetValue::new)
                .peek(fastInfosetValue -> {
                    LOGGER.info("Dumping fastinfoset:\n{}", deserialise(fastInfosetValue));
                })
                .forEach(fastInfosetValue -> {

                    consumeFastInfoset(fastInfosetValue, "" +
                                                         "<\\?xml version=\"1\\.0\" encoding=\"UTF-8\"\\?>" +
                                                         "<Location xmlns=\"reference-data:2\">(.|\\n)*" +
                                                         "<Room>room[0-9]+<\\/Room>" +
                                                         "<Desk>desk[0-9]+<\\/Desk>" +
                                                         "<Value>" +
                                                         "<value>" +
                                                         "<Value>nestedValue<\\/Value>" +
                                                         "<\\/value>" +
                                                         "<\\/Value>" +
                                                         "<\\/Location>");
                });
        final Pattern pattern = Pattern.compile("room[0-9]+");

        final List<String> roomList = loadedRefDataValues.keyValueValues.stream()
                .map(FastInfosetValue::new)
                .map(this::deserialise)
                .map(str -> {
                    final Matcher matcher = pattern.matcher(str);
                    assertThat(matcher.find())
                            .isTrue();
                    return matcher.group();
                })
                .collect(Collectors.toList());

        assertThat(roomList)
                .containsExactly("room11");
    }

    @Test
    void testFastInfoset_8_KeyValues_elmNameCaseInsensitivity() {

        final LoadedRefDataValues loadedRefDataValues = doTest(INPUT_FAST_INFOSET_VALUE_8, null);

        assertThat(loadedRefDataValues.keyValueValues)
                .extracting(StagingValue::getTypeId)
                .containsOnly(FastInfosetValue.TYPE_ID);
        assertThat(loadedRefDataValues.keyValueValues).hasSize(1);
        assertThat(loadedRefDataValues.rangeValueValues).isEmpty();

        loadedRefDataValues.keyValueValues.stream()
                .map(FastInfosetValue::new)
                .peek(fastInfosetValue -> {
                    LOGGER.info("Dumping fastinfoset:\n{}", deserialise(fastInfosetValue));
                })
                .forEach(fastInfosetValue -> {

                    consumeFastInfoset(fastInfosetValue, "" +
                                                         "<\\?xml version=\"1\\.0\" encoding=\"UTF-8\"\\?>" +
                                                         "<Location xmlns=\"reference-data:2\">(.|\\n)*" +
                                                         "<Room>room[0-9]+<\\/Room>" +
                                                         "<Desk>desk[0-9]+<\\/Desk>" +
                                                         "<value>value[0-9]+<\\/value>" +
                                                         "<\\/Location>");
                });
        final Pattern pattern = Pattern.compile("room[0-9]+");

        final List<String> roomList = loadedRefDataValues.keyValueValues.stream()
                .map(FastInfosetValue::new)
                .map(this::deserialise)
                .map(str -> {
                    final Matcher matcher = pattern.matcher(str);
                    assertThat(matcher.find())
                            .isTrue();
                    return matcher.group();
                })
                .collect(Collectors.toList());

        assertThat(roomList)
                .containsExactly("room11");
    }

    private LoadedRefDataValues doTest(final String inputPath, final String expectedOutputPath) {
        final LoadedRefDataValues loadedRefDataValues = new LoadedRefDataValues();

        Mockito.when(refDataLoader.getRefStreamDefinition())
                .thenReturn(buildUniqueRefStreamDefinition());

        Mockito.when(refDataLoader.initialise(Mockito.anyBoolean()))
                .thenReturn(PutOutcome.success());

        // capture the args passed to the two put methods. Have to use doAnswer
        // so we can copy the buffer that is reused and therefore mutates.
        Mockito.doAnswer(invocation -> {
            loadedRefDataValues.addKeyValue(
                    invocation.getArgument(1),
                    invocation.getArgument(2));
            return PutOutcome.newEntry();
        }).when(refDataLoader).put(
                Mockito.any(),
                Mockito.any(String.class),
                Mockito.any(StagingValue.class));

        Mockito.doAnswer(invocation -> {
            loadedRefDataValues.addRangeValue(
                    invocation.getArgument(1), // mockito can infer the type
                    invocation.getArgument(2));
            return PutOutcome.newEntry();
        }).when(refDataLoader).put(
                Mockito.any(),
                Mockito.<Range<Long>>any(),
                Mockito.any(StagingValue.class));

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());
        final RefDataLoaderHolder refDataLoaderHolder = new RefDataLoaderHolder();
        refDataLoaderHolder.setRefDataLoader(refDataLoader);

        final ReferenceDataFilter referenceDataFilter = new ReferenceDataFilter(
                errorReceiverProxy,
                refDataLoaderHolder,
                new StagingValueOutputStream(new XxHashValueStoreHashAlgorithm(),
                        capacity ->
                                new PooledByteBufferOutputStream(getByteBufferPool(), capacity)));

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


        final String actualSax = testSAXEventFilter.getOutput().trim();

        LOGGER.info("Actual SAX: \n {}", actualSax);

        return loadedRefDataValues;
    }

    /**
     * Consume the fastInfosetValue using the various classes used in ref data lookup
     */
    private void consumeFastInfoset(final FastInfosetValue fastInfosetValue, final String expectedXMLRegex) {
        LOGGER.info("Consuming fastInfosetValue");
        final Configuration configuration = new Configuration();
        final PipelineConfiguration pipelineConfiguration = new PipelineConfiguration(configuration);

        final StringWriter stringWriter = new StringWriter();
        final XMLEmitter xmlEmitter = new XMLEmitter();
        xmlEmitter.setPipelineConfiguration(pipelineConfiguration);
        try {
            xmlEmitter.setWriter(stringWriter);
        } catch (final XPathException e) {
            throw new RuntimeException(e);
        }
        final FastInfosetByteBufferConsumer fastInfosetByteBufferConsumer = new FastInfosetByteBufferConsumer(
                xmlEmitter, pipelineConfiguration);

        final RefDataValueByteBufferConsumer.Factory refDataValueByteBufferConsumerFactory =
                (receiver, pipelineConfiguration1)
                        -> fastInfosetByteBufferConsumer;

        final OffHeapRefDataValueProxyConsumer offHeapRefDataValueProxyConsumer = new OffHeapRefDataValueProxyConsumer(
                xmlEmitter,
                pipelineConfiguration,
                Map.of(new ByteBufferConsumerId(FastInfosetValue.TYPE_ID), refDataValueByteBufferConsumerFactory));

        final OffHeapRefDataValueProxyConsumer.Factory offHeapRefDataValueProxyConsumerFactory =
                (receiver, pipelineConfiguration12)
                        -> offHeapRefDataValueProxyConsumer;

        final RefDataValueProxyConsumerFactory refDataValueProxyConsumerFactory = new RefDataValueProxyConsumerFactory(
                xmlEmitter,
                pipelineConfiguration,
                null,
                offHeapRefDataValueProxyConsumerFactory);

        final GenericRefDataValueProxyConsumer genericRefDataValueProxyConsumer = new GenericRefDataValueProxyConsumer(
                xmlEmitter, pipelineConfiguration, refDataValueProxyConsumerFactory);

        final RefDataValueProxy refDataValueProxy = new RefDataValueProxy() {

            @Override
            public String getKey() {
                return null;
            }

            @Override
            public String getMapName() {
                return null;
            }

            @Override
            public List<MapDefinition> getMapDefinitions() {
                return null;
            }

            @Override
            public Optional<MapDefinition> getSuccessfulMapDefinition() {
                return Optional.empty();
            }

            @Override
            public Optional<RefDataValue> supplyValue() {
                return Optional.of(fastInfosetValue);
            }

            @Override
            public boolean consumeBytes(final Consumer<TypedByteBuffer> typedByteBufferConsumer) {
                final TypedByteBuffer typedByteBuffer = new TypedByteBuffer(FastInfosetValue.TYPE_ID,
                        fastInfosetValue.getByteBuffer());
                typedByteBufferConsumer.accept(typedByteBuffer);
                return true;
            }

            @Override
            public boolean consumeValue(final RefDataValueProxyConsumerFactory refDataValueProxyConsumerFactory) {
                final RefDataValueProxyConsumer refDataValueProxyConsumer = refDataValueProxyConsumerFactory
                        .getConsumer(StorageType.OFF_HEAP);
                try {
                    return refDataValueProxyConsumer.consume(this);
                } catch (final XPathException e) {
                    throw new RuntimeException(LogUtil.message(
                            "Error handling reference data value: {}", e.getMessage()), e);
                }
            }

            @Override
            public RefDataValueProxy merge(final RefDataValueProxy additionalProxy) {
                return MultiRefDataValueProxy.merge(this, additionalProxy);
            }
        };

        try {
            genericRefDataValueProxyConsumer.startDocument();
            genericRefDataValueProxyConsumer.consume(refDataValueProxy);
            genericRefDataValueProxyConsumer.endDocument();
        } catch (final XPathException e) {
            e.printStackTrace();
        }

        final String xml = stringWriter.toString();
        LOGGER.info("==================");
        LOGGER.info("Output:\n{}", xml);

        Assertions.assertThat(xml)
                .matches(expectedXMLRegex);

        // Flip the buffer for the next reader
        fastInfosetValue.getByteBuffer().flip();
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
        final TestSAXEventFilter testSAXEventFilter = new TestSAXEventFilter();
        final TestFilter testFilter = new TestFilter(new ErrorReceiverProxy(), new LocationFactoryProxy());
        testFilter.setContentHandler(new MyContentHandler());

        final SAXDocumentParser saxDocumentParser = new SAXDocumentParser();
        // it may be possible to only deal with fragments but can't seem to serialise without calling startDocument
//        saxDocumentParser.setParseFragments(true);
        saxDocumentParser.setContentHandler(testSAXEventFilter);
        try {
            saxDocumentParser.parse(new ByteBufferInputStream(fastInfosetValue.getByteBuffer()));
        } catch (final IOException | FastInfosetException | SAXException e) {
            throw new RuntimeException(e);
        }
        // flip the buffer now we have read it so it can be read again if required
        fastInfosetValue.getByteBuffer().flip();

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
        public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
                throws SAXException {

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
        List<StagingValue> keyValueValues;
        List<Range<Long>> rangeValueKeys;
        List<StagingValue> rangeValueValues;

        LoadedRefDataValues() {
            this.keyValueKeys = new ArrayList<>();
            this.keyValueValues = new ArrayList<>();
            this.rangeValueValues = new ArrayList<>();
            this.rangeValueKeys = new ArrayList<>();
        }

        void addKeyValue(final String key, final StagingValue value) {
            LOGGER.info("Adding keyValue {} {}", key, value);
            keyValueKeys.add(key);
            final StagingValue valueCopy = copyValue(value);
            keyValueValues.add(valueCopy);
        }

        void addRangeValue(final Range<Long> range, final StagingValue value) {
            LOGGER.info("Adding rangeValue {} {}", range, value);
            rangeValueKeys.add(range);
            final StagingValue valueCopy = copyValue(value);
            rangeValueValues.add(valueCopy);
        }

        @NotNull
        private static StagingValue copyValue(final StagingValue value) {
            assertThat(value.getFullByteBuffer().position()).isEqualTo(0);
            assertThat(value.getValueBuffer().position()).isEqualTo(0);
            final StagingValue valueCopy = value.copy(
                    () -> ByteBuffer.allocateDirect(value.size()));
            assertThat(valueCopy.getFullByteBuffer().position()).isEqualTo(0);
            assertThat(valueCopy.getValueBuffer().position()).isEqualTo(0);
            return valueCopy;
        }
    }

//    @Test
//    void buildXml() {
//        Configuration configuration = Configuration.newConfiguration();
//        final DocumentBuilder documentBuilder = new Processor(configuration).newDocumentBuilder();
//
//        final Builder builder = documentBuilder.getTreeModel().makeBuilder(new PipelineConfiguration(configuration));
//        final String uri = "event-logging:3";
//        final String prefix = "evt";
//
//        try {
//            builder.startDocument(0);
//            builder.startPrefixMapping(prefix, uri);
//            builder.startElement((uri, "Events", "Events", null);
//            builder.endElement(uri, "Events", "Events");
//            builder.endPrefixMapping(prefix);
//            builder.endDocument();
//        } catch (XPathException e) {
//            throw new RuntimeException(e);
//        }
//
//        documentBuilder.setTreeModel(TreeModel.TINY_TREE);
//        try {
//            final BuildingContentHandler buildingContentHandler = documentBuilder.newBuildingContentHandler();
//
//            buildingContentHandler.startDocument();
//            buildingContentHandler.startPrefixMapping(prefix, uri);
//            buildingContentHandler.startElement(uri, "Events", "Events", null);
//            buildingContentHandler.endElement(uri, "Events", "Events");
//            buildingContentHandler.endPrefixMapping(prefix);
//            buildingContentHandler.endDocument();
//            buildingContentHandler
//            final XdmNode documentNode = buildingContentHandler.getDocumentNode();
//
//        } catch (SaxonApiException e) {
//            throw new RuntimeException(e);
//        } catch (SAXException e) {
//            e.printStackTrace();
//        }
//
//    }
}
