package stroom.planb.impl.pipeline;

import stroom.pathways.shared.otel.trace.AnyValue;
import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.SpanEvent;
import stroom.pathways.shared.otel.trace.SpanKind;
import stroom.pathways.shared.otel.trace.SpanLink;
import stroom.pathways.shared.otel.trace.SpanStatus;
import stroom.pathways.shared.otel.trace.StatusCode;
import stroom.util.CharBuffer;
import stroom.util.shared.NullSafe;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;

public class SpanHandler extends DefaultHandler {

    private final Span.Builder spanBuilder = Span.builder();
    private final CharBuffer contentBuffer = new CharBuffer(20);
    private SpanStatus.Builder spanStatusBuilder;
    private final Stack<KeyValue.Builder> keyValueBuilderStack = new Stack<>();
    private final Stack<Consumer<AnyValue>> currentValueConsumerStack = new Stack<>();
    private final Stack<Consumer<KeyValue>> currentKeyValueConsumerStack = new Stack<>();

    private List<SpanEvent> events;
    private SpanEvent.Builder spanEventBuilder;

    private List<SpanLink> links;
    private SpanLink.Builder spanLinkBuilder;

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts) {
        contentBuffer.clear();

        if (localName.equalsIgnoreCase("status")) {
            spanStatusBuilder = SpanStatus.builder();
        } else if (localName.equalsIgnoreCase("attributes")) {
            final List<KeyValue> attributes = new ArrayList<>();
            currentKeyValueConsumerStack.push(attributes::add);
            if (spanEventBuilder != null) {
                spanEventBuilder.attributes(attributes);
            } else if (spanLinkBuilder != null) {
                spanLinkBuilder.attributes(attributes);
            } else {
                spanBuilder.attributes(attributes);
            }
        } else if (localName.equalsIgnoreCase("keyValue")) {
            final KeyValue.Builder keyValueBuilder = KeyValue.builder();
            currentValueConsumerStack.push(keyValueBuilder::value);
            keyValueBuilderStack.push(keyValueBuilder);
        } else if (localName.equalsIgnoreCase("arrayValue")) {
            final List<AnyValue> values = new ArrayList<>();
            currentValueConsumerStack.peek().accept(AnyValue.arrayValue(values));
            currentValueConsumerStack.push(values::add);
        } else if (localName.equalsIgnoreCase("kvlistValue")) {
            final List<KeyValue> values = new ArrayList<>();
            currentValueConsumerStack.peek().accept(AnyValue.kvlistValue(values));
            currentKeyValueConsumerStack.push(values::add);
        } else if (localName.equalsIgnoreCase("events")) {
            events = new ArrayList<>();
        } else if (localName.equalsIgnoreCase("links")) {
            links = new ArrayList<>();
        } else if (localName.equalsIgnoreCase("spanEvent")) {
            spanEventBuilder = SpanEvent.builder();
        } else if (localName.equalsIgnoreCase("spanLink")) {
            spanLinkBuilder = SpanLink.builder();
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) {
        if (spanEventBuilder != null) {
            // Deal with span event.
            if (localName.equalsIgnoreCase("spanEvent")) {
                events.add(spanEventBuilder.build());
                spanEventBuilder = null;
            } else if (localName.equalsIgnoreCase("timeUnixNano")) {
                spanEventBuilder.timeUnixNano(contentBuffer.toString());
            } else if (localName.equalsIgnoreCase("name")) {
                spanEventBuilder.name(contentBuffer.toString());
            } else if (localName.equalsIgnoreCase("attributes")) {
                currentKeyValueConsumerStack.pop();
            } else if (localName.equalsIgnoreCase("droppedAttributesCount")) {
                spanEventBuilder.droppedAttributesCount(getInt(contentBuffer.toString()));
            }

        } else if (spanLinkBuilder != null) {
            // Deal with span link.
            if (localName.equalsIgnoreCase("spanLink")) {
                links.add(spanLinkBuilder.build());
                spanLinkBuilder = null;
            } else if (localName.equalsIgnoreCase("traceId")) {
                spanLinkBuilder.traceId(contentBuffer.toString());
            } else if (localName.equalsIgnoreCase("spanId")) {
                spanLinkBuilder.spanId(contentBuffer.toString());
            } else if (localName.equalsIgnoreCase("traceState")) {
                spanLinkBuilder.traceState(contentBuffer.toString());
            } else if (localName.equalsIgnoreCase("attributes")) {
                currentKeyValueConsumerStack.pop();
            } else if (localName.equalsIgnoreCase("droppedAttributesCount")) {
                spanLinkBuilder.droppedAttributesCount(getInt(contentBuffer.toString()));
            }

        } else if (spanStatusBuilder != null) {
            // Deal with span status.
            if (localName.equalsIgnoreCase("message")) {
                spanStatusBuilder.message(contentBuffer.toString());
            } else if (localName.equalsIgnoreCase("code")) {
                spanStatusBuilder.code(StatusCode.fromString(contentBuffer.toString()));
            } else if (localName.equalsIgnoreCase("status")) {
                spanBuilder.status(spanStatusBuilder.build());
                spanStatusBuilder = null;
            }

        } else if (!currentKeyValueConsumerStack.isEmpty()) {
            // Deal with key value pairs.
            if (!currentValueConsumerStack.empty()) {
                final Consumer<AnyValue> consumer = currentValueConsumerStack.peek();
                if (localName.equalsIgnoreCase("stringValue")) {
                    consumer.accept(AnyValue.stringValue(contentBuffer.toString()));
                } else if (localName.equalsIgnoreCase("boolValue")) {
                    consumer.accept(AnyValue.boolValue(getBoolean(contentBuffer.toString())));
                } else if (localName.equalsIgnoreCase("intValue")) {
                    consumer.accept(AnyValue.intValue(getInt(contentBuffer.toString())));
                } else if (localName.equalsIgnoreCase("doubleValue")) {
                    consumer.accept(AnyValue.doubleValue(getDouble(contentBuffer.toString())));
                } else if (localName.equalsIgnoreCase("arrayValue")) {
                    currentValueConsumerStack.pop();
                } else if (localName.equalsIgnoreCase("kvlistValue")) {
                    currentKeyValueConsumerStack.pop();
                } else if (localName.equalsIgnoreCase("bytesValue")) {
                    consumer.accept(AnyValue.bytesValue(contentBuffer.toString()));
                }
            }

            if (!keyValueBuilderStack.empty()) {
                if (localName.equalsIgnoreCase("key")) {
                    keyValueBuilderStack.peek().key(contentBuffer.toString());
                } else if (localName.equalsIgnoreCase("keyValue")) {
                    final KeyValue keyValue = keyValueBuilderStack.pop().build();
                    currentKeyValueConsumerStack.peek().accept(keyValue);
                }
            }

        } else {
            if (localName.equalsIgnoreCase("traceId")) {
                spanBuilder.traceId(contentBuffer.toString());
            } else if (localName.equalsIgnoreCase("spanId")) {
                spanBuilder.spanId(contentBuffer.toString());
            } else if (localName.equalsIgnoreCase("parentSpanId")) {
                spanBuilder.parentSpanId(contentBuffer.toString());
            } else if (localName.equalsIgnoreCase("traceState")) {
                spanBuilder.traceState(contentBuffer.toString());
            } else if (localName.equalsIgnoreCase("flags")) {
                spanBuilder.flags(getInt(contentBuffer.toString()));
            } else if (localName.equalsIgnoreCase("name")) {
                spanBuilder.name(contentBuffer.toString());
            } else if (localName.equalsIgnoreCase("kind")) {
                spanBuilder.kind(SpanKind.fromString(contentBuffer.toString()));
            } else if (localName.equalsIgnoreCase("startTimeUnixNano")) {
                spanBuilder.startTimeUnixNano(contentBuffer.toString());
            } else if (localName.equalsIgnoreCase("endTimeUnixNano")) {
                spanBuilder.endTimeUnixNano(contentBuffer.toString());
            } else if (localName.equalsIgnoreCase("attributes")) {
                currentKeyValueConsumerStack.pop();
            } else if (localName.equalsIgnoreCase("droppedAttributesCount")) {
                spanBuilder.droppedAttributesCount(getInt(contentBuffer.toString()));
            } else if (localName.equalsIgnoreCase("events")) {
                spanBuilder.events(events);
                events = null;
            } else if (localName.equalsIgnoreCase("droppedEventsCount")) {
                spanBuilder.droppedEventsCount(getInt(contentBuffer.toString()));
            } else if (localName.equalsIgnoreCase("links")) {
                spanBuilder.links(links);
                links = null;
            } else if (localName.equalsIgnoreCase("droppedLinksCount")) {
                spanBuilder.droppedLinksCount(getInt(contentBuffer.toString()));
            }
        }

        contentBuffer.clear();
    }

    private int getInt(final String string) {
        if (!NullSafe.isEmptyString(string)) {
            return Integer.parseInt(string);
        }
        return 0;
    }

    private double getDouble(final String string) {
        if (!NullSafe.isEmptyString(string)) {
            return Double.parseDouble(string);
        }
        return 0;
    }

    private boolean getBoolean(final String string) {
        if (!NullSafe.isEmptyString(string)) {
            return Boolean.parseBoolean(string);
        }
        return false;
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        // outside the value element so capture the chars, so we can get keys, map names, etc.
        contentBuffer.append(ch, start, length);
    }

    public Span build() {
        return spanBuilder.build();
    }
}
