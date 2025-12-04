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

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.KeySerde;
import stroom.util.shared.NullSafe;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class SpanKeySerde implements KeySerde<SpanKey> {

    private static final int TRACE_ID_LENGTH = 16;
    private static final int PARENT_SPAN_ID_LENGTH = 8;
    private static final int SPAN_ID_LENGTH = 8;
    private static final int LENGTH = TRACE_ID_LENGTH + PARENT_SPAN_ID_LENGTH + SPAN_ID_LENGTH;
    private static final byte[] NO_PARENT_SPAN_ID = new byte[PARENT_SPAN_ID_LENGTH];

    private final ByteBuffers byteBuffers;

    public SpanKeySerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final SpanKey value,
                                final Function<Optional<ByteBuffer>, R> function) {
        return byteBuffers.use(LENGTH, byteBuffer -> {
            write(value, byteBuffer);
            return function.apply(Optional.of(byteBuffer));
        });
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final SpanKey value, final Consumer<ByteBuffer> consumer) {
        byteBuffers.use(LENGTH, byteBuffer -> {
            write(value, byteBuffer);
            consumer.accept(byteBuffer);
        });
    }

    private void write(final SpanKey spanKey, final ByteBuffer byteBuffer) {
        final byte[] traceId = HexStringUtil.decode(spanKey.getTraceId());
        final byte[] parentSpanId = spanIdStringToBytes(spanKey.getParentSpanId());
        final byte[] spanId = HexStringUtil.decode(spanKey.getSpanId());

        if (traceId.length != TRACE_ID_LENGTH) {
            throw new IllegalArgumentException("Trace id not " + TRACE_ID_LENGTH + " bytes long");
        }
        if (parentSpanId.length != PARENT_SPAN_ID_LENGTH) {
            throw new IllegalArgumentException("Parent span id not " + PARENT_SPAN_ID_LENGTH + " bytes long");
        }
        if (spanId.length != SPAN_ID_LENGTH) {
            throw new IllegalArgumentException("Span id not " + SPAN_ID_LENGTH + " bytes long");
        }
        byteBuffer.put(traceId);
        byteBuffer.put(parentSpanId);
        byteBuffer.put(spanId);
        byteBuffer.flip();
    }

    @Override
    public SpanKey read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final byte[] traceId = new byte[TRACE_ID_LENGTH];
        final byte[] parentSpanId = new byte[PARENT_SPAN_ID_LENGTH];
        final byte[] spanId = new byte[SPAN_ID_LENGTH];
        byteBuffer.get(traceId);
        byteBuffer.get(parentSpanId);
        byteBuffer.get(spanId);
        return new SpanKey(HexStringUtil.encode(traceId),
                HexStringUtil.encode(spanId),
                spanIdBytesToString(parentSpanId));
    }

    private byte[] spanIdStringToBytes(final String spanId) {
        return NullSafe.isEmptyString(spanId)
                ? NO_PARENT_SPAN_ID
                : HexStringUtil.decode(spanId);
    }

    private String spanIdBytesToString(final byte[] bytes) {
        if (Arrays.equals(bytes, NO_PARENT_SPAN_ID)) {
            return "";
        }
        return HexStringUtil.encode(bytes);
    }
}
