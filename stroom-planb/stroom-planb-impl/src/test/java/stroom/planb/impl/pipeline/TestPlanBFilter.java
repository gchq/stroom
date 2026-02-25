package stroom.planb.impl.pipeline;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.meta.shared.Meta;
import stroom.pathways.shared.TraceWriter;
import stroom.pathways.shared.otel.trace.AnyValue;
import stroom.pathways.shared.otel.trace.ExportTraceServiceRequest;
import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.ResourceSpans;
import stroom.pathways.shared.otel.trace.ScopeSpans;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.SpanEvent;
import stroom.pathways.shared.otel.trace.SpanLink;
import stroom.pathways.shared.otel.trace.SpanStatus;
import stroom.pathways.shared.otel.trace.StatusCode;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.filter.TestFilter;
import stroom.pipeline.filter.TestSAXEventFilter;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.util.ProcessorUtil;
import stroom.planb.impl.data.SpanKV;
import stroom.planb.impl.db.ShardWriters;
import stroom.planb.impl.db.ShardWriters.ShardWriter;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class TestPlanBFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestPlanBFilter.class);
    private static final ObjectMapper MAPPER = createMapper(true);

    @Mock
    ShardWriters shardWriters;
    @Mock
    ShardWriter shardWriter;

    @Test
    void test() throws Exception {
        final List<Span> spans = new ArrayList<>();

        Mockito.when(shardWriters.createWriter(Mockito.any()))
                .thenReturn(shardWriter);
        Mockito.when(shardWriter.getDoc(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.of(PlanBDoc.builder()
                        .uuid(UUID.randomUUID().toString())
                        .stateType(StateType.TRACE)
                        .build()));
        final Answer<?> answer = invocation -> {
            final SpanKV spanKV = invocation.getArgument(1);
            LOGGER.info(spanKV.toString());

            assertThat(spans).isNotEmpty();
            final Span expectedSpan = spans.removeFirst();
            final SpanKey expectedKey = SpanKey.create(expectedSpan);
            final SpanValue expectedValue = SpanValue.create(expectedSpan);
            assertThat(spanKV.key()).isEqualTo(expectedKey);
            assertThat(spanKV.val()).isEqualTo(expectedValue);

            return null;
        };
        Mockito.doAnswer(answer)
                .when(shardWriter)
                .addSpanValue(Mockito.any(), Mockito.any());

        // Write data as XML
        final XmlWriter xmlWriter = new XmlWriter();
        xmlWriter.element("plan-b",
                List.of(
                        new Attribute("xmlns", "plan-b:2"),
                        new Attribute("version", "2.0")),
                root -> {

                    final TraceWriter writer = new TraceWriter() {
                        @Override
                        public void addSpan(final Span span) {
                            spans.add(span);

                            root.element("trace", trace -> {
                                trace.data("map", "test");

                                trace.element("span", spn -> {
                                    spn.data("traceId", span.getTraceId());
                                    spn.data("spanId", span.getSpanId());
                                    spn.data("parentSpanId", span.getParentSpanId());
                                    spn.data("traceState", span.getTraceState());
                                    spn.data("flags", span.getFlags());
                                    spn.data("name", span.getName());
                                    spn.data("kind", span.getKind().getDisplayValue());
                                    spn.data("startTimeUnixNano", span.getStartTimeUnixNano());
                                    spn.data("endTimeUnixNano", span.getEndTimeUnixNano());

                                    appendAttributes(spn, span.getAttributes());

                                    spn.data("droppedAttributesCount", span.getDroppedAttributesCount());

                                    if (span.getEvents() != null) {
                                        spn.element("events", events -> {
                                            for (final SpanEvent event : span.getEvents()) {
                                                appendEvent(events, event);
                                            }
                                        });
                                    }
                                    spn.data("droppedEventsCount", span.getDroppedEventsCount());


                                    if (span.getLinks() != null) {
                                        spn.element("links", links -> {
                                            for (final SpanLink link : span.getLinks()) {
                                                appendLink(links, link);
                                            }
                                        });
                                    }

                                    spn.data("droppedLinksCount", span.getDroppedLinksCount());

                                    if (span.getStatus() != null) {
                                        spn.element("status", s -> {
                                            s.data("message", span.getStatus().getMessage());
                                            s.data("code", NullSafe
                                                    .get(span,
                                                            Span::getStatus,
                                                            SpanStatus::getCode,
                                                            StatusCode::getDisplayValue));
                                        });
                                    }
                                });
                            });
                        }

                        @Override
                        public void close() {

                        }
                    };

                    for (int i = 1; i <= 17; i++) {
                        final Path p = Paths.get("src/test/resources/TestSpanValueSerde/TEST_TRACES~" + i + ".in");
                        loadData(p, writer);
                    }
                });

        assertThat(spans.size()).isEqualTo(166);

        // Output XML
        final String xml = xmlWriter.toString();
        LOGGER.info(xml);

        // Validate XML against Plan B schema.
        final Path path = Paths.get("src/test/resources/TestPlanBFilter/plan_b_v2_0.xsd");
        final Schema schema = loadSchema(Files.newInputStream(path));
        schema.newValidator().validate(new StreamSource(new StringReader(xml)));

        // Read spans back into PlanBFilter
        testFilter(xml);
    }

    private void appendEvent(final XmlWriter sb, final SpanEvent spanEvent) {
        sb.element("event", event -> {
            event.data("timeUnixNano", spanEvent.getTimeUnixNano());
            event.data("name", spanEvent.getName());
            appendAttributes(event, spanEvent.getAttributes());
            event.data("droppedAttributesCount", spanEvent.getDroppedAttributesCount());
        });
    }

    private void appendLink(final XmlWriter xmlWriter, final SpanLink spanLink) {
        xmlWriter.element("link", link -> {
            link.data("traceId", spanLink.getTraceId());
            link.data("spanId", spanLink.getSpanId());
            link.data("traceState", spanLink.getTraceState());
            appendAttributes(link, spanLink.getAttributes());
            link.data("droppedAttributesCount", spanLink.getDroppedAttributesCount());
        });
    }

    private void appendAttributes(final XmlWriter xmlWriter, final List<KeyValue> attributes) {
        if (attributes != null) {
            xmlWriter.element("attributes", writer -> {
                for (final KeyValue keyValue : attributes) {
                    appendKeyValue(writer, keyValue);
                }
            });
        }
    }

    private void appendKeyValue(final XmlWriter xmlWriter, final KeyValue keyValue) {
        xmlWriter.element("keyValue", writer -> {
            writer.data("key", keyValue.getKey());
            appendAnyValue(writer, keyValue.getValue());
        });
    }

    private void appendAnyValue(final XmlWriter xmlWriter, final AnyValue anyValue) {
        if (anyValue.getStringValue() != null) {
            xmlWriter.data("stringValue", anyValue.getStringValue());
        }
        if (anyValue.getBoolValue() != null) {
            xmlWriter.data("boolValue", anyValue.getBoolValue());
        }
        if (anyValue.getIntValue() != null) {
            xmlWriter.data("intValue", anyValue.getIntValue());
        }
        if (anyValue.getDoubleValue() != null) {
            xmlWriter.data("doubleValue", anyValue.getDoubleValue());
        }
        if (anyValue.getArrayValue() != null && anyValue.getArrayValue().getValues() != null) {
            xmlWriter.element("arrayValue", arrayValue -> {
                for (final AnyValue value : anyValue.getArrayValue().getValues()) {
                    appendAnyValue(arrayValue, value);
                }
            });
        }
        if (anyValue.getKvlistValue() != null && anyValue.getKvlistValue().getValues() != null) {
            xmlWriter.element("kvlistValue", kvlistValue -> {
                for (final KeyValue value : anyValue.getKvlistValue().getValues()) {
                    appendKeyValue(kvlistValue, value);
                }
            });
        }
        if (anyValue.getBytesValue() != null) {
            xmlWriter.data("bytesValue", anyValue.getBytesValue());
        }
    }

    private void loadData(final Path path,
                          final TraceWriter writer) {
        try (final BufferedReader lineReader = Files.newBufferedReader(path)) {
            final String line = lineReader.readLine();
            final ExportTraceServiceRequest exportRequest =
                    MAPPER.readValue(line, ExportTraceServiceRequest.class);
            for (final ResourceSpans resourceSpans : NullSafe.list(exportRequest.getResourceSpans())) {
                for (final ScopeSpans scopeSpans : NullSafe.list(resourceSpans.getScopeSpans())) {
                    for (final Span span : NullSafe.list(scopeSpans.getSpans())) {
                        writer.addSpan(span);
                    }
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ObjectMapper createMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);
        return mapper;
    }

    private Schema loadSchema(final InputStream inputStream) throws Exception {
        final SchemaFactory schemaFactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
//        schemaFactory.setErrorHandler(new ErrorHandlerAdaptor());
//        schemaFactory.setResourceResolver(new LSResourceResolverImpl(xmlSchemaCache, findXMLSchemaCriteria));

        return schemaFactory.newSchema(new StreamSource(inputStream));
    }

    private void testFilter(final String xml) {
        final ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        final MetaHolder metaHolder = new MetaHolder();
        metaHolder.setMeta(new Meta());
        final PlanBFilter splitter = new PlanBFilter(
                new ErrorReceiverProxy(),
                new LocationFactoryProxy(),
                metaHolder,
                new ByteBufferFactoryImpl(),
                shardWriters);

        final TestFilter testFilter = new TestFilter(null, null);

        final TestSAXEventFilter testSAXEventFilter = new TestSAXEventFilter();

        splitter.setTarget(testFilter);
        testFilter.setTarget(testSAXEventFilter);

        ProcessorUtil.processXml(input, new ErrorReceiverProxy(new FatalErrorReceiver()), splitter,
                new LocationFactoryProxy());

//        final List<String> actualXmlList = testFilter.getOutputs()
//                .stream()
//                .map(String::trim)
//                .map(s -> s.replaceAll("\r", ""))
//                .collect(Collectors.toList());
//        final String actualSax = testSAXEventFilter.getOutput().trim();
//
//        // Test to see if the output SAX is the same as the expected SAX.
//        ComparisonHelper.compareStrings(expectedSax, actualSax, "Expected and actual SAX do not match at index: ");
//
//        // Test to see if the output XML is the same as the expected XML.
//        LOGGER.info(String.format("Expected List %d", expectedXmlList.size()));
//        expectedXmlList.forEach(LOGGER::info);
//        LOGGER.info(String.format("Actual List %d", actualXmlList.size()));
//        actualXmlList.forEach(LOGGER::info);
//
//        assertThat(actualXmlList).hasSize(expectedXmlList.size()); // first just check the size
//        final Iterator<String> actualXmlIter = actualXmlList.iterator();
//        for (final String expectedXml : expectedXmlList) {
//            final String actualXml = actualXmlIter.next();
//            ComparisonHelper.compareStrings(expectedXml, actualXml,
//            "Expected and actual XML do not match at index: ");
//        }
    }

    private static class XmlWriter {

        private final StringBuilder sb;
        private final int depth;

        public XmlWriter() {
            this(new StringBuilder(), 0);
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
        }

        private XmlWriter(final StringBuilder sb,
                          final int depth) {
            this.sb = sb;
            this.depth = depth;
        }

        void data(final String name, final Object object) {
            if (object != null) {
                pad();
                sb.append("<").append(name).append(">");
                sb.append(object);
                sb.append("</").append(name).append(">\n");
            }
        }


        void element(final String name, final Consumer<XmlWriter> consumer) {
            element(name, Collections.emptyList(), consumer);
        }

        void element(final String name,
                     final List<Attribute> attributes,
                     final Consumer<XmlWriter> consumer) {
            pad();
            sb.append("<").append(name);
            for (final Attribute attribute : attributes) {
                sb.append(" ");
                sb.append(attribute.key);
                sb.append("=\"");
                sb.append(attribute.value);
                sb.append("\"");
            }
            sb.append(">\n");
            consumer.accept(new XmlWriter(sb, depth + 1));
            pad();
            sb.append("</").append(name).append(">\n");
        }

        private void pad() {
            for (int i = 0; i < depth; i++) {
                sb.append("   ");
            }
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    private record Attribute(String key, String value) {

    }
}
