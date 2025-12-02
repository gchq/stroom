package stroom.planb.impl.serde;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.SimpleByteBufferFactory;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.serde.trace.MockLookupSerde;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.impl.serde.trace.SpanValueSerde;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSpanValueSerde {

    @Test
    void test() {
        final ByteBufferFactory byteBufferFactory = new SimpleByteBufferFactory();
        final SpanValueSerde serde = new SpanValueSerde(byteBufferFactory, new MockLookupSerde());
        SpanDataLoaderTestUtil.load(span -> {

            final NanoTime insertTime = NanoTimeUtil.now();
            final SpanValue in = SpanValue
                    .builder()
                    .insertTime(insertTime)
                    .traceState(span.getTraceState() == null
                            ? ""
                            : span.getTraceState())
                    .flags(span.getFlags())
                    .name(span.getName())
                    .kind(span.getKind())
                    .startTimeUnixNano(span.getStartTimeUnixNano())
                    .endTimeUnixNano(span.getEndTimeUnixNano())
                    .attributes(span.getAttributes())
                    .droppedAttributesCount(span.getDroppedAttributesCount())
                    .events(span.getEvents())
                    .droppedEventsCount(span.getDroppedEventsCount())
                    .links(span.getLinks())
                    .droppedLinksCount(span.getDroppedLinksCount())
                    .status(span.getStatus().copy().message("").build())
                    .build();
//                                    final String js = JsonUtil.writeValueAsString(in);
//                                    final byte[] bytes = js.getBytes(StandardCharsets.UTF_8);
            serde.write(null, in, byteBuffer -> {
                assertThat(serde.usesLookup(byteBuffer.duplicate())).isFalse();
                final SpanValue out = serde.read(null, byteBuffer.duplicate());
                assertThat(out).isEqualTo(in);
            });
        });
    }
}
