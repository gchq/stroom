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

package stroom.planb.impl.serde.trace;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.pathways.shared.otel.trace.AnyValue;
import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.SpanEvent;
import stroom.pathways.shared.otel.trace.SpanKind;
import stroom.pathways.shared.otel.trace.SpanLink;
import stroom.pathways.shared.otel.trace.SpanStatus;
import stroom.pathways.shared.otel.trace.StatusCode;
import stroom.pathways.shared.otel.trace.ValueType;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.db.trace.NanoTimeUtil;
import stroom.planb.impl.serde.Serde;
import stroom.planb.impl.serde.time.NanoTimeSerde;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.util.shared.NullSafe;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class SpanValueSerde implements Serde<SpanValue> {

    private final TimeSerde timeSerde;
    private final ByteBufferFactory byteBufferFactory;
    private final LookupSerde lookupSerde;

    private int bufferSize = 1024;

    public SpanValueSerde(final ByteBufferFactory byteBufferFactory,
                          final LookupSerde lookupSerde) {
        this.byteBufferFactory = byteBufferFactory;
        this.lookupSerde = lookupSerde;
        timeSerde = new NanoTimeSerde();
    }

    private ByteBuffer ensure(final ByteBuffer byteBuffer,
                              final int require) {
        if (byteBuffer.remaining() < require) {
            if (byteBuffer.capacity() > require) {
                final ByteBuffer buffer = byteBufferFactory.acquire(byteBuffer.capacity() * 2);
                buffer.put(byteBuffer.flip());
                byteBufferFactory.release(byteBuffer);
                return buffer;
            } else {
                final ByteBuffer buffer = byteBufferFactory.acquire(byteBuffer.capacity() + require);
                buffer.put(byteBuffer.flip());
                byteBufferFactory.release(byteBuffer);
                return buffer;
            }
        }
        return byteBuffer;
    }

    private ByteBuffer writeNanoTime(final NanoTime nanoTime, final ByteBuffer byteBuffer) {
        final NanoTime time = Objects.requireNonNullElse(nanoTime, NanoTime.ZERO);
        final ByteBuffer result = ensure(byteBuffer, timeSerde.getSize());
        timeSerde.write(result, NanoTimeUtil.toInstant(time));
        return result;
    }

    private NanoTime readNanoTime(final ByteBuffer byteBuffer) {
        final Instant instant = timeSerde.read(byteBuffer);
        return NanoTimeUtil.fromInstant(instant);
    }

    private ByteBuffer writeKvList(final Txn<ByteBuffer> txn, final List<KeyValue> list, final ByteBuffer byteBuffer) {
        final List<KeyValue> values = NullSafe.list(list);
        ByteBuffer result = ensure(byteBuffer, Integer.BYTES);
        result.putInt(values.size());
        for (final KeyValue keyValue : values) {
            result = writeString(txn, keyValue.getKey(), result);
            result = writeAnyValue(txn, keyValue.getValue(), result);
        }
        return result;
    }

    private List<KeyValue> readKvList(final Txn<ByteBuffer> txn,
                                      final ByteBuffer input) {
        final int size = input.getInt();
        if (size == 0) {
            return null;
        }

        final List<KeyValue> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            final String name = readString(txn, input);
            values.add(new KeyValue(name, readAnyValue(txn, input)));
        }
        return values;
    }

    private ByteBuffer writeValueList(final Txn<ByteBuffer> txn,
                                      final List<AnyValue> list,
                                      final ByteBuffer byteBuffer) {
        final List<AnyValue> values = NullSafe.list(list);
        ByteBuffer result = ensure(byteBuffer, Integer.BYTES);
        result.putInt(values.size());
        for (final AnyValue value : values) {
            result = writeAnyValue(txn, value, result);
        }
        return result;
    }

    private List<AnyValue> readValueList(final Txn<ByteBuffer> txn,
                                         final ByteBuffer byteBuffer) {
        final int size = byteBuffer.getInt();
        if (size == 0) {
            return null;
        }

        final List<AnyValue> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(readAnyValue(txn, byteBuffer));
        }
        return values;
    }

    private ByteBuffer writeString(final Txn<ByteBuffer> txn,
                                   final String string,
                                   final ByteBuffer byteBuffer) {
        final byte[] bytes = string == null
                ? new byte[0]
                : string.getBytes(StandardCharsets.UTF_8);
        final int len = lookupSerde.getStorageLength(bytes);
        final ByteBuffer result = ensure(byteBuffer, len);
        lookupSerde.write(txn, bytes, result);
        return result;
    }

    private String readString(final Txn<ByteBuffer> txn,
                              final ByteBuffer input) {
        final byte[] bytes = lookupSerde.read(txn, input);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private ByteBuffer writeByte(final byte b,
                                 final ByteBuffer byteBuffer) {
        final ByteBuffer result = ensure(byteBuffer, 1);
        result.put(b);
        return result;
    }

    private ByteBuffer writeInt(final int i,
                                final ByteBuffer byteBuffer) {
        final ByteBuffer result = ensure(byteBuffer, Integer.BYTES);
        result.putInt(i);
        return result;
    }

    private ByteBuffer writeAnyValue(final Txn<ByteBuffer> txn,
                                     final AnyValue anyValue,
                                     final ByteBuffer output) {
        ByteBuffer result = output;
        if (anyValue == null) {
            result.put((byte) 128);

        } else {
            if (anyValue.getStringValue() != null) {
                result = ensure(output, 1);
                result.put(ValueType.STRING.getPrimitiveValue());
                result = writeString(txn, anyValue.getStringValue(), result);

            } else if (anyValue.getBoolValue() != null) {
                result = ensure(output, 1 + 1);
                result.put(ValueType.BOOLEAN.getPrimitiveValue());
                result.put((byte) (anyValue.getBoolValue()
                        ? 1
                        : 0));

            } else if (anyValue.getIntValue() != null) {
                result = ensure(output, 1 + Long.BYTES);
                result.put(ValueType.LONG.getPrimitiveValue());
                result.putLong(anyValue.getIntValue());

            } else if (anyValue.getDoubleValue() != null) {
                result = ensure(output, 1 + Double.BYTES);
                result.put(ValueType.DOUBLE.getPrimitiveValue());
                result.putDouble(anyValue.getDoubleValue());

            } else if (anyValue.getArrayValue() != null) {
                result = ensure(output, 1);
                result.put(ValueType.ARRAY_VALUE.getPrimitiveValue());
                result = writeValueList(txn, anyValue.getArrayValue().getValues(), result);

            } else if (anyValue.getKvlistValue() != null) {
                result = ensure(output, 1);
                result.put(ValueType.KEY_VALUE_LIST.getPrimitiveValue());
                result = writeKvList(txn, anyValue.getKvlistValue().getValues(), result);

            } else if (anyValue.getBytesValue() != null) {
                result = ensure(output, 1);
                result.put(ValueType.BYTES.getPrimitiveValue());
                result = writeHexString(txn, anyValue.getBytesValue(), result);

            } else {
                result.put((byte) 128);
            }
        }
        return result;
    }

    private ByteBuffer writeHexString(final Txn<ByteBuffer> txn,
                                      final String string,
                                      final ByteBuffer output) {
        ByteBuffer result = output;
        final byte[] bytes = string == null
                ? new byte[0]
                : HexStringUtil.decode(string);
        final int len = lookupSerde.getStorageLength(bytes);
        result = ensure(result, len);
        lookupSerde.write(txn, bytes, result);
        return result;
    }

    private String readHexString(final Txn<ByteBuffer> txn,
                                 final ByteBuffer input) {
        final byte[] bytes = lookupSerde.read(txn, input);
        return HexStringUtil.encode(bytes);
    }

    private AnyValue readAnyValue(final Txn<ByteBuffer> txn,
                                  final ByteBuffer input) {
        final byte type = input.get();
        if (type == ValueType.STRING.getPrimitiveValue()) {
            final String string = readString(txn, input);
            return AnyValue.stringValue(string);

        } else if (type == ValueType.BOOLEAN.getPrimitiveValue()) {
            return AnyValue.boolValue(input.get() == 1);

        } else if (type == ValueType.LONG.getPrimitiveValue()) {
            return AnyValue.intValue(input.getLong());

        } else if (type == ValueType.DOUBLE.getPrimitiveValue()) {
            return AnyValue.doubleValue(input.getDouble());

        } else if (type == ValueType.ARRAY_VALUE.getPrimitiveValue()) {
            final List<AnyValue> list = readValueList(txn, input);
            return AnyValue.arrayValue(list);

        } else if (type == ValueType.KEY_VALUE_LIST.getPrimitiveValue()) {
            final List<KeyValue> list = readKvList(txn, input);
            return AnyValue.kvlistValue(list);

        } else if (type == ValueType.BYTES.getPrimitiveValue()) {
            return AnyValue.bytesValue(readHexString(txn, input));
        }
        return null;
    }

    private ByteBuffer writeEvents(final Txn<ByteBuffer> txn,
                                   final List<SpanEvent> list,
                                   final ByteBuffer output) {
        ByteBuffer result = ensure(output, Integer.BYTES);
        final List<SpanEvent> values = NullSafe.list(list);
        result.putInt(values.size());
        for (final SpanEvent value : values) {
            result = writeEvent(txn, value, result);
        }
        return result;
    }

    private List<SpanEvent> readEvents(final Txn<ByteBuffer> txn,
                                       final ByteBuffer input) {
        final int size = input.getInt();
        if (size == 0) {
            return null;
        }

        final List<SpanEvent> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(readEvent(txn, input));
        }
        return values;
    }

    private ByteBuffer writeEvent(final Txn<ByteBuffer> txn,
                                  final SpanEvent event,
                                  final ByteBuffer output) {
//                @JsonProperty("timeUnixNano")
//                private final String timeUnixNano;
//
//                @JsonProperty("name")
//                private final String name;
//
//                @JsonProperty("attributes")
//                private final List<KeyValue> attributes;
//
//                @JsonProperty("droppedAttributesCount")
//                private final int droppedAttributesCount;

        ByteBuffer result = output;
        result = writeNanoTime(NanoTime.fromString(event.getTimeUnixNano()), result);
        result = writeString(txn, event.getName(), result);
        result = writeKvList(txn, event.getAttributes(), result);
        result = ensure(result, Integer.BYTES);
        result = result.putInt(event.getDroppedAttributesCount());
        return result;
    }

    private SpanEvent readEvent(final Txn<ByteBuffer> txn,
                                final ByteBuffer input) {
        return new SpanEvent(
                readNanoTime(input).toNanoEpochString(),
                readString(txn, input),
                readKvList(txn, input),
                input.getInt());
    }

    private ByteBuffer writeLinks(final Txn<ByteBuffer> txn,
                                  final List<SpanLink> list,
                                  final ByteBuffer output) {
        ByteBuffer result = ensure(output, Integer.BYTES);
        final List<SpanLink> values = NullSafe.list(list);
        result.putInt(values.size());
        for (final SpanLink value : values) {
            result = writeLink(txn, value, result);
        }
        return result;
    }

    private List<SpanLink> readLinks(final Txn<ByteBuffer> txn,
                                     final ByteBuffer input) {
        final int size = input.getInt();
        if (size == 0) {
            return null;
        }

        final List<SpanLink> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(readLink(txn, input));
        }
        return values;
    }


    private ByteBuffer writeLink(final Txn<ByteBuffer> txn,
                                 final SpanLink link,
                                 final ByteBuffer output) {
//        @JsonProperty("traceId")
//        private final String traceId;
//
//        @JsonProperty("spanId")
//        private final String spanId;
//
//        @JsonProperty("traceState")
//        private final String traceState;
//
//        @JsonProperty("attributes")
//        private final List<KeyValue> attributes;
//
//        @JsonProperty("droppedAttributesCount")
//        private final int droppedAttributesCount;

        ByteBuffer result = output;
        result = writeHexString(txn, link.getTraceId(), result);
        result = writeHexString(txn, link.getSpanId(), result);
        result = writeString(txn, link.getTraceState(), result);
        result = writeKvList(txn, link.getAttributes(), result);
        result.putInt(link.getDroppedAttributesCount());
        return result;
    }

    private SpanLink readLink(final Txn<ByteBuffer> txn,
                              final ByteBuffer input) {
        return new SpanLink(
                readHexString(txn, input),
                readHexString(txn, input),
                readString(txn, input),
                readKvList(txn, input),
                input.getInt());
    }

    private ByteBuffer writeStatus(final Txn<ByteBuffer> txn,
                                   final SpanStatus status,
                                   final ByteBuffer output) {
        ByteBuffer result = output;
        if (status != null) {
            result = writeString(txn, status.getMessage(), result);
            if (status.getCode() != null) {
                result = ensure(result, 1);
                result.put(status.getCode().getPrimitiveValue());
            }
        }
        return result;
    }

    private SpanStatus readStatus(final Txn<ByteBuffer> txn,
                                  final ByteBuffer input) {
        if (input.remaining() == 0) {
            return null;
        }

        final String message = readString(txn, input);
        final StatusCode statusCode;
        if (input.remaining() == 0) {
            statusCode = null;
        } else {
            statusCode = StatusCode.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(input.get());
        }

        return new SpanStatus(message, statusCode);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn,
                      final SpanValue value,
                      final Consumer<ByteBuffer> consumer) {
        ByteBuffer byteBuffer = byteBufferFactory.acquire(bufferSize);
        try {
            // insert time
            byteBuffer = writeNanoTime(value.getInsertTime(), byteBuffer);
            // name
            byteBuffer = writeString(txn, value.getName(), byteBuffer);
            // kind
            byteBuffer = writeByte(Objects.requireNonNullElse(value.getKind(),
                    SpanKind.SPAN_KIND_UNSPECIFIED).getPrimitiveValue(), byteBuffer);
            // startTimeUnixNano
            byteBuffer = writeNanoTime(NanoTime.fromString(value.getStartTimeUnixNano()), byteBuffer);
            // endTimeUnixNano
            byteBuffer = writeNanoTime(NanoTime.fromString(value.getEndTimeUnixNano()), byteBuffer);
            // traceState
            byteBuffer = writeString(txn, value.getTraceState(), byteBuffer);
            // flags
            byteBuffer = writeInt(value.getFlags(), byteBuffer);
            // attributes
            byteBuffer = writeKvList(txn, value.getAttributes(), byteBuffer);
            // droppedAttributesCount
            byteBuffer = writeInt(value.getDroppedAttributesCount(), byteBuffer);
            // events
            byteBuffer = writeEvents(txn, value.getEvents(), byteBuffer);
            // droppedEventsCount
            byteBuffer = writeInt(value.getDroppedEventsCount(), byteBuffer);
            // links
            byteBuffer = writeLinks(txn, value.getLinks(), byteBuffer);
            // droppedLinksCount
            byteBuffer = writeInt(value.getDroppedLinksCount(), byteBuffer);
            // status
            byteBuffer = writeStatus(txn, value.getStatus(), byteBuffer);

            byteBuffer.flip();
            bufferSize = Math.max(bufferSize, byteBuffer.capacity());
            consumer.accept(byteBuffer);

        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    @Override
    public SpanValue read(final Txn<ByteBuffer> txn, final ByteBuffer input) {
        // insert time
        final NanoTime insertTime = readNanoTime(input);
        // name
        final String name = readString(txn, input);
        // kind
        final SpanKind kind = SpanKind.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(input.get());
        // startTimeUnixNano
        final NanoTime start = readNanoTime(input);
        // endTimeUnixNano
        final NanoTime end = readNanoTime(input);
        // traceState
        final String traceState = readString(txn, input);
        // flags
        final int flags = input.getInt();
        // attributes
        final List<KeyValue> attributes = readKvList(txn, input);
        // droppedAttributesCount
        final int droppedAttributesCount = input.getInt();
        // events
        final List<SpanEvent> events = readEvents(txn, input);
        // droppedEventsCount
        final int droppedEventsCount = input.getInt();
        // links
        final List<SpanLink> links = readLinks(txn, input);
        // droppedLinksCount
        final int droppedLinksCount = input.getInt();
        // status
        final SpanStatus status = readStatus(txn, input);

        return SpanValue.builder()
                .insertTime(insertTime)
                .traceState(traceState)
                .flags(flags)
                .name(name)
                .kind(kind)
                .startTimeUnixNano(start)
                .endTimeUnixNano(end)
                .attributes(attributes)
                .droppedAttributesCount(droppedAttributesCount)
                .events(events)
                .droppedEventsCount(droppedEventsCount)
                .links(links)
                .droppedLinksCount(droppedLinksCount)
                .status(status)
                .build();
    }


    @Override
    public UsedLookupsRecorder getUsedLookupsRecorder(final PlanBEnv env) {
        final UsedLookupsRecorder delegate = lookupSerde.getUsedLookupsRecorder(env);
        return new UsedLookupsImpl(timeSerde, delegate);
    }

    private void skipNanoTime(final ByteBuffer byteBuffer) {
        ByteBufferUtils.skip(byteBuffer, timeSerde.getSize());
    }

    private boolean usesLookupKvList(final ByteBuffer input) {
        final int size = input.getInt();
        if (size > 0) {
            for (int i = 0; i < size; i++) {
                if (usesLookupString(input)) {
                    return true;
                }
                if (usesLookupAnyValue(input)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean usesLookupValueList(final ByteBuffer byteBuffer) {
        final int size = byteBuffer.getInt();
        if (size > 0) {
            for (int i = 0; i < size; i++) {
                if (usesLookupAnyValue(byteBuffer)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean usesLookupString(final ByteBuffer input) {
        return lookupSerde.usesLookup(input);
    }

    private boolean usesLookupBase64(final ByteBuffer input) {
        return lookupSerde.usesLookup(input);
    }

    private boolean usesLookupAnyValue(final ByteBuffer input) {
        final byte type = input.get();
        if (type == ValueType.STRING.getPrimitiveValue()) {
            return usesLookupString(input);

        } else if (type == ValueType.BOOLEAN.getPrimitiveValue()) {
            ByteBufferUtils.skip(input, 1);

        } else if (type == ValueType.INTEGER.getPrimitiveValue()) {
            ByteBufferUtils.skip(input, Integer.BYTES);

        } else if (type == ValueType.LONG.getPrimitiveValue()) {
            ByteBufferUtils.skip(input, Long.BYTES);

        } else if (type == ValueType.DOUBLE.getPrimitiveValue()) {
            ByteBufferUtils.skip(input, Double.BYTES);

        } else if (type == ValueType.ARRAY_VALUE.getPrimitiveValue()) {
            return usesLookupValueList(input);

        } else if (type == ValueType.KEY_VALUE_LIST.getPrimitiveValue()) {
            return usesLookupKvList(input);

        } else if (type == ValueType.BYTES.getPrimitiveValue()) {
            return usesLookupBase64(input);
        }
        return false;
    }

    private boolean usesLookupEvents(final ByteBuffer input) {
        final int size = input.getInt();
        if (size > 0) {
            for (int i = 0; i < size; i++) {
                if (usesLookupEvent(input)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean usesLookupEvent(final ByteBuffer input) {
        skipNanoTime(input);
        if (usesLookupString(input)) {
            return true;
        }
        if (usesLookupKvList(input)) {
            return true;
        }
        ByteBufferUtils.skip(input, Integer.BYTES);
        return false;
    }

    private boolean usesLookupLinks(final ByteBuffer input) {
        final int size = input.getInt();
        if (size > 0) {
            for (int i = 0; i < size; i++) {
                if (usesLookupLink(input)) {
                    return true;
                }
            }
        }
        return false;
    }


    private boolean usesLookupLink(final ByteBuffer input) {
        if (usesLookupBase64(input)) {
            return true;
        }
        if (usesLookupBase64(input)) {
            return true;
        }
        if (usesLookupString(input)) {
            return true;
        }
        if (usesLookupKvList(input)) {
            return true;
        }
        ByteBufferUtils.skip(input, Integer.BYTES);
        return false;
    }

    private boolean usesLookupStatus(final ByteBuffer input) {
        if (input.remaining() == 0) {
            return false;
        }
        return usesLookupString(input);
    }

    @Override
    public boolean usesLookup(final ByteBuffer input) {
        return internalUsesLookup(input.duplicate());
    }

    private boolean internalUsesLookup(final ByteBuffer input) {
        // insert time
        skipNanoTime(input);
        // name
        if (usesLookupString(input)) {
            return true;
        }
        // kind
        ByteBufferUtils.skip(input, 1);
        // startTimeUnixNano
        skipNanoTime(input);
        // endTimeUnixNano
        skipNanoTime(input);
        // traceState
        if (usesLookupString(input)) {
            return true;
        }
        // flags
        ByteBufferUtils.skip(input, Integer.BYTES);
        // attributes
        if (usesLookupKvList(input)) {
            return true;
        }
        // droppedAttributesCount
        ByteBufferUtils.skip(input, Integer.BYTES);
        // events
        if (usesLookupEvents(input)) {
            return true;
        }
        // droppedEventsCount
        ByteBufferUtils.skip(input, Integer.BYTES);
        // links
        if (usesLookupLinks(input)) {
            return true;
        }
        // droppedLinksCount
        ByteBufferUtils.skip(input, Integer.BYTES);
        // status
        return usesLookupStatus(input);
    }

    private static class UsedLookupsImpl implements UsedLookupsRecorder {

        private final TimeSerde timeSerde;
        private final UsedLookupsRecorder usedLookupsRecorder;

        public UsedLookupsImpl(final TimeSerde timeSerde,
                               final UsedLookupsRecorder usedLookupsRecorder) {
            this.timeSerde = timeSerde;
            this.usedLookupsRecorder = usedLookupsRecorder;
        }

        @Override
        public void recordUsed(final LmdbWriter writer, final ByteBuffer byteBuffer) {
            record(writer, byteBuffer);
        }

        @Override
        public void deleteUnused(final Txn<ByteBuffer> readTxn, final LmdbWriter writer) {
            usedLookupsRecorder.deleteUnused(readTxn, writer);
        }


        private void recordNanoTime(final ByteBuffer byteBuffer) {
            ByteBufferUtils.skip(byteBuffer, timeSerde.getSize());
        }

        private void recordKvList(final LmdbWriter writer,
                                  final ByteBuffer input) {
            final int size = input.getInt();
            if (size > 0) {
                for (int i = 0; i < size; i++) {
                    recordString(writer, input);
                    recordAnyValue(writer, input);
                }
            }
        }

        private void recordValueList(final LmdbWriter writer,
                                     final ByteBuffer byteBuffer) {
            final int size = byteBuffer.getInt();
            if (size > 0) {
                for (int i = 0; i < size; i++) {
                    recordAnyValue(writer, byteBuffer);
                }
            }
        }

        private void recordString(final LmdbWriter writer,
                                  final ByteBuffer input) {
            usedLookupsRecorder.recordUsed(writer, input);
        }

        private void recordHexString(final LmdbWriter writer,
                                     final ByteBuffer input) {
            usedLookupsRecorder.recordUsed(writer, input);
        }

        private void recordAnyValue(final LmdbWriter writer,
                                    final ByteBuffer input) {
            final byte type = input.get();
            if (type == ValueType.STRING.getPrimitiveValue()) {
                recordString(writer, input);

            } else if (type == ValueType.BOOLEAN.getPrimitiveValue()) {
                ByteBufferUtils.skip(input, 1);

            } else if (type == ValueType.INTEGER.getPrimitiveValue()) {
                ByteBufferUtils.skip(input, Integer.BYTES);

            } else if (type == ValueType.LONG.getPrimitiveValue()) {
                ByteBufferUtils.skip(input, Integer.BYTES);

            } else if (type == ValueType.DOUBLE.getPrimitiveValue()) {
                ByteBufferUtils.skip(input, Double.BYTES);

            } else if (type == ValueType.ARRAY_VALUE.getPrimitiveValue()) {
                recordValueList(writer, input);

            } else if (type == ValueType.KEY_VALUE_LIST.getPrimitiveValue()) {
                recordKvList(writer, input);

            } else if (type == ValueType.BYTES.getPrimitiveValue()) {
                recordHexString(writer, input);
            }
        }

        private void recordEvents(final LmdbWriter writer,
                                  final ByteBuffer input) {
            final int size = input.getInt();
            if (size > 0) {
                for (int i = 0; i < size; i++) {
                    recordEvent(writer, input);
                }
            }
        }

        private void recordEvent(final LmdbWriter writer,
                                 final ByteBuffer input) {
            recordNanoTime(input);
            recordString(writer, input);
            recordKvList(writer, input);
            ByteBufferUtils.skip(input, Integer.BYTES);
        }

        private void recordLinks(final LmdbWriter writer,
                                 final ByteBuffer input) {
            final int size = input.getInt();
            if (size > 0) {
                for (int i = 0; i < size; i++) {
                    recordLink(writer, input);
                }
            }
        }

        private void recordLink(final LmdbWriter writer,
                                final ByteBuffer input) {
            recordHexString(writer, input);
            recordHexString(writer, input);
            recordString(writer, input);
            recordKvList(writer, input);
            ByteBufferUtils.skip(input, Integer.BYTES);
        }

        private void recordStatus(final LmdbWriter writer,
                                  final ByteBuffer input) {
            if (input.remaining() > 0) {
                recordString(writer, input);
            }
        }

        public void record(final LmdbWriter writer, final ByteBuffer input) {
            // insert time
            recordNanoTime(input);
            // name
            recordString(writer, input);
            // kind
            ByteBufferUtils.skip(input, 1);
            // startTimeUnixNano
            recordNanoTime(input);
            // endTimeUnixNano
            recordNanoTime(input);
            // traceState
            recordString(writer, input);
            // flags
            ByteBufferUtils.skip(input, Integer.BYTES);
            // attributes
            recordKvList(writer, input);
            // droppedAttributesCount
            ByteBufferUtils.skip(input, Integer.BYTES);
            // events
            recordEvents(writer, input);
            // droppedEventsCount
            ByteBufferUtils.skip(input, Integer.BYTES);
            // links
            recordLinks(writer, input);
            // droppedLinksCount
            ByteBufferUtils.skip(input, Integer.BYTES);
            // status
            recordStatus(writer, input);
        }
    }
}
