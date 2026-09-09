/*
 * Copyright 2026 Crown Copyright
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

package stroom.planb.impl.serde.trace;

import stroom.bytebuffer.impl6.SimpleByteBufferFactory;
import stroom.pathways.shared.otel.trace.AnyValue;
import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.SpanKind;
import stroom.pathways.shared.otel.trace.SpanLink;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * A span value is written into a buffer that grows as it needs to, and read back from a buffer whose
 * own contents decide how much memory the read reserves. Both sides have to hold for values this node
 * did not write — a shared file store carries them between nodes.
 */
class TestSpanValueSerdeBounds {

    // An attribute value carrying nothing at all. It is written as a single marker byte, which is the
    // one place a write reaches the buffer without having asked for room first.
    private static final AnyValue EMPTY_VALUE =
            new AnyValue(null, null, null, null, null, null, null);

    private static SpanValue.Builder span(final String name) {
        return SpanValue.builder()
                .insertTime(NanoTime.ZERO)
                .name(name)
                .kind(SpanKind.SPAN_KIND_INTERNAL)
                .startTimeUnixNano(NanoTime.ZERO)
                .endTimeUnixNano(NanoTime.ofSeconds(1))
                .traceState("")
                .flags(0)
                .droppedAttributesCount(0)
                .droppedEventsCount(0)
                .droppedLinksCount(0);
    }

    // ---------------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------------

    // Enough padding attributes to carry a record past the initial buffer size, so the write being
    // tested lands on the boundary for one of them. Each contributes a fixed few bytes, so the name
    // length is swept alongside to shift the whole record by one byte at a time and cover the offsets
    // in between.
    private static final int MAX_PAD = 400;
    private static final int MAX_NAME = 4;

    private static List<KeyValue> emptyValuedAttributes(final int count) {
        final List<KeyValue> attributes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            attributes.add(new KeyValue("k", EMPTY_VALUE));
        }
        return attributes;
    }

    /**
     * Walks the empty attribute's marker byte across the end of the buffer. Where the write does not
     * ask for room first, the one record length that lands the marker exactly on the boundary fails —
     * and which length that is depends on the buffer size, so a single case would not find it.
     */
    @Test
    void writesAnAttributeWithNoValueAtEveryOffset() {
        for (int nameLength = 0; nameLength < MAX_NAME; nameLength++) {
            for (int pad = 1; pad <= MAX_PAD; pad++) {
                final SpanValue in = span("x".repeat(nameLength))
                        .attributes(emptyValuedAttributes(pad))
                        .build();
                final SpanValue out = writeAndRead(
                        in, pad + " attributes with no value, name length " + nameLength);
                // The attributes survive; each value comes back as absent rather than as an AnyValue
                // holding nothing, which is how the reader has always treated the marker.
                assertThat(out.getAttributes()).hasSize(pad);
                assertThat(out.getAttributes().getFirst().getKey()).isEqualTo("k");
            }
        }
    }

    /**
     * The same sweep for the count a link carries after its attributes, which is the other write that
     * did not ask for room. It needs four bytes rather than one, so it overflows on any of the last
     * few offsets rather than just the boundary itself.
     */
    @Test
    void writesALinksTrailingCountAtEveryOffset() {
        for (int nameLength = 0; nameLength < MAX_NAME; nameLength++) {
            for (int pad = 1; pad <= MAX_PAD; pad++) {
                final SpanValue in = span("x".repeat(nameLength))
                        .attributes(emptyValuedAttributes(pad))
                        .links(List.of(new SpanLink("", "", "", null, 7)))
                        .build();
                final SpanValue out = writeAndRead(
                        in, "link after " + pad + " attributes, name length " + nameLength);
                // The count written after the link's attributes is the one at risk, so it is the one
                // worth reading back.
                assertThat(out.getLinks()).hasSize(1);
                assertThat(out.getLinks().getFirst().getDroppedAttributesCount()).isEqualTo(7);
            }
        }
    }

    private SpanValue writeAndRead(final SpanValue in, final String description) {
        // A serde remembers the largest buffer it has needed, so each case gets its own — otherwise an
        // earlier case's growth would move the boundary this one is trying to land on.
        final SpanValueSerde serde = new SpanValueSerde(new SimpleByteBufferFactory(), new MockLookupSerde());
        final SpanValue[] holder = new SpanValue[1];
        try {
            serde.write(null, in, byteBuffer -> holder[0] = serde.read(null, byteBuffer.duplicate()));
        } catch (final RuntimeException e) {
            fail("Failed to write " + description + ": " + e, e);
        }
        assertThat(holder[0]).as(description).isNotNull();
        return holder[0];
    }

    // ---------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------

    @Test
    void acceptsACountTheBufferCouldSupply() {
        final ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + 3);
        buffer.putInt(3).put(new byte[3]).flip();
        assertThat(SpanValueSerde.readCount(buffer, "attributes")).isEqualTo(3);
    }

    @Test
    void acceptsAnEmptyList() {
        final ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(0).flip();
        assertThat(SpanValueSerde.readCount(buffer, "attributes")).isZero();
    }

    /**
     * Sizing a list from a negative count throws {@link NegativeArraySizeException}, which says
     * nothing about the buffer being at fault.
     */
    @Test
    void rejectsANegativeCount() {
        final ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + 10);
        buffer.putInt(-1).flip();
        assertThatThrownBy(() -> SpanValueSerde.readCount(buffer, "attributes"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("-1");
    }

    /**
     * The one that matters most: a record naming more elements than the bytes left could ever supply
     * would otherwise reserve that much memory before reading a single one of them.
     */
    @Test
    void rejectsACountLargerThanTheBufferCouldSupply() {
        final ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + 2);
        buffer.putInt(Integer.MAX_VALUE).flip();
        assertThatThrownBy(() -> SpanValueSerde.readCount(buffer, "events"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("events");
    }

    /**
     * Where the attribute count sits in a record whose name and trace state are both empty:
     * insert time (8) + name (2) + kind (1) + start (8) + end (8) + trace state (2) + flags (4).
     * {@link #serialise} checks the count really is there before a case corrupts it, so a change to
     * the layout fails loudly rather than quietly testing nothing.
     */
    private static final int ATTRIBUTE_COUNT_OFFSET = 33;

    private byte[] serialise(final SpanValue in, final int expectedAttributeCount) {
        final SpanValueSerde serde = new SpanValueSerde(new SimpleByteBufferFactory(), new MockLookupSerde());
        final byte[][] holder = {new byte[0]};
        serde.write(null, in, byteBuffer -> {
            final ByteBuffer duplicate = byteBuffer.duplicate();
            final byte[] bytes = new byte[duplicate.remaining()];
            duplicate.get(bytes);
            holder[0] = bytes;
        });
        final byte[] bytes = holder[0];
        assertThat(ByteBuffer.wrap(bytes).getInt(ATTRIBUTE_COUNT_OFFSET))
                .as("the attribute count is no longer at offset " + ATTRIBUTE_COUNT_OFFSET
                    + ", so these cases would corrupt some other field")
                .isEqualTo(expectedAttributeCount);
        return bytes;
    }

    private static SpanValue readCorrupted(final byte[] record, final int attributeCount) {
        final ByteBuffer buffer = ByteBuffer.wrap(record.clone());
        buffer.putInt(ATTRIBUTE_COUNT_OFFSET, attributeCount);
        final SpanValueSerde serde = new SpanValueSerde(new SimpleByteBufferFactory(), new MockLookupSerde());
        return serde.read(null, buffer);
    }

    /**
     * The guard has to be reached by the read path, not merely to exist. A record claiming far more
     * attributes than its remaining bytes could hold would otherwise reserve room for all of them
     * before reading one.
     */
    @Test
    void rejectsAnOversizedCountWhenReadingARecord() {
        final byte[] record = serialise(
                span("").attributes(List.of(new KeyValue("a", AnyValue.stringValue("1")))).build(), 1);

        assertThatThrownBy(() -> readCorrupted(record, 1_000_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("attributes");
    }

    @Test
    void rejectsANegativeCountWhenReadingARecord() {
        final byte[] record = serialise(
                span("").attributes(List.of(new KeyValue("a", AnyValue.stringValue("1")))).build(), 1);

        assertThatThrownBy(() -> readCorrupted(record, -1))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Reaches the same guard through the real read path. Every prefix of a valid record has to fail
     * in a way that reports the buffer, rather than by reserving memory for a list that cannot be
     * there.
     */
    @Test
    void everyTruncationOfARecordFailsWithoutReservingMemory() {
        final SpanValue in = span("truncate-me")
                .attributes(List.of(
                        new KeyValue("a", AnyValue.stringValue("1")),
                        new KeyValue("b", EMPTY_VALUE)))
                .links(List.of(new SpanLink("", "", "", List.of(), 1)))
                .build();

        final SpanValueSerde serde = new SpanValueSerde(new SimpleByteBufferFactory(), new MockLookupSerde());
        final byte[][] holder = {new byte[0]};
        serde.write(null, in, byteBuffer -> {
            final ByteBuffer duplicate = byteBuffer.duplicate();
            final byte[] bytes = new byte[duplicate.remaining()];
            duplicate.get(bytes);
            holder[0] = bytes;
        });

        final byte[] full = holder[0];
        assertThat(full).isNotEmpty();

        for (int length = 0; length < full.length; length++) {
            final ByteBuffer truncated = ByteBuffer.allocate(length);
            truncated.put(full, 0, length).flip();
            try {
                serde.read(null, truncated);
                // Some prefixes parse, because the reader treats a missing trailing field as absent
                // rather than as an error. That is fine. Reserving memory the buffer cannot fill is not.
            } catch (final NegativeArraySizeException | OutOfMemoryError e) {
                fail("Truncating to " + length + " of " + full.length
                     + " bytes sized a list from an unchecked count: " + e);
            } catch (final RuntimeException e) {
                // A controlled failure is the expected outcome for an incomplete record.
                assertThat(e).isNotNull();
            }
        }
    }
}
