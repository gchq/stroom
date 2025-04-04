package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.query.api.Row;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.common.v2.LmdbKV;

import com.esotericsoftware.kryo.io.Output;
import net.openhft.hashing.LongHashFunction;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DuplicateKeyFactory {

    private final ByteBufferFactory byteBufferFactory;

    private final long ruleUUIDHash;
    private final boolean grouped;
    private final List<Integer> groupIndexes;

    public DuplicateKeyFactory(final ByteBufferFactory byteBufferFactory,
                               final AnalyticRuleDoc analyticRuleDoc,
                               final CompiledColumns compiledColumns) {
        this.byteBufferFactory = byteBufferFactory;
        ruleUUIDHash = hashRule(analyticRuleDoc);

        groupIndexes = new ArrayList<>(compiledColumns.getCompiledColumns().length);
        boolean grouped = false;
        for (int i = 0; i < compiledColumns.getCompiledColumns().length; i++) {
            if (compiledColumns.getCompiledColumns()[i].getGroupDepth() >= 0) {
                groupIndexes.add(i);
                grouped = true;
            }
        }
        this.grouped = grouped;
    }

    private long hashRule(final AnalyticRuleDoc analyticRuleDoc) {
        final byte[] bytes;

        try (final Output output = new Output(512, -1)) {
            output.writeString(analyticRuleDoc.getUuid());
            bytes = output.toBytes();
        }
//        bufferSize = Math.max(bufferSize, bytes.length);
        return LongHashFunction.xx3().hashBytes(bytes);
    }

    public LmdbKV createRow(final Row row) {
        final byte[] bytes = rowToBytes(row);

        // Hash the value.
        final long rowHash = LongHashFunction.xx3().hashBytes(bytes);

        final ByteBuffer keyByteBuffer = byteBufferFactory.acquire(Long.BYTES + Long.BYTES);
        keyByteBuffer.putLong(ruleUUIDHash);
        keyByteBuffer.putLong(rowHash);
        keyByteBuffer.flip();

        // TODO : Possibly trim bytes, although should have already happened.
        final ByteBuffer valueByteBuffer = byteBufferFactory.acquire(bytes.length);
        valueByteBuffer.put(bytes);
        valueByteBuffer.flip();

        return new LmdbKV(null, keyByteBuffer, valueByteBuffer);
    }

    private byte[] rowToBytes(final Row row) {
        final byte[] bytes;
        if (grouped) {
            try (final Output output = new Output(512, -1)) {
                for (final Integer index : groupIndexes) {
                    final String value = row.getValues().get(index);
                    if (value != null) {
                        output.writeString(value);
                    }
                }
                bytes = output.toBytes();
            }

        } else {
            try (final Output output = new Output(512, -1)) {
                for (final String value : row.getValues()) {
                    if (value != null) {
                        output.writeString(value);
                    }
                }
                bytes = output.toBytes();
            }
        }
        return bytes;
    }
}
