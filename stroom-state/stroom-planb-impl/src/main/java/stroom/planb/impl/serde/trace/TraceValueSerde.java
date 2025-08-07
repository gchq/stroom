package stroom.planb.impl.serde.trace;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.data.TraceValue;
import stroom.planb.impl.serde.Serde;
import stroom.util.json.JsonUtil;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class TraceValueSerde implements Serde<TraceValue> {

    private final ByteBuffers byteBuffers;

    public TraceValueSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final TraceValue value, final Consumer<ByteBuffer> consumer) {
        final byte[] bytes = JsonUtil.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
        byteBuffers.useBytes(bytes, consumer);
    }

    @Override
    public TraceValue read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        return JsonUtil.readValue(ByteBufferUtils.toString(byteBuffer), TraceValue.class);
    }
}
