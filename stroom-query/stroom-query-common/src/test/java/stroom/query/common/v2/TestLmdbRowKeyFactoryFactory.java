package stroom.query.common.v2;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.query.api.v2.Field;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.FlatGroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.FlatTimeGroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.FlatTimeUngroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.FlatUngroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.NestedGroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.NestedTimeGroupedLmdbRowKeyFactory;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class TestLmdbRowKeyFactoryFactory {

    @Test
    void testUniqueFlatGroupedLmdbRowKeyFactory() {
        testUnique(new FlatGroupedLmdbRowKeyFactory());
    }

    @Test
    void testUniqueFlatUngroupedLmdbRowKeyFactory() {
        testUnique(new FlatUngroupedLmdbRowKeyFactory(getUniqueIdProvider()));
    }

    @Test
    void testUniqueFlatTimeGroupedLmdbRowKeyFactory() {
        testUnique(new FlatTimeGroupedLmdbRowKeyFactory());
    }

    @Test
    void testUniqueFlatTimeUngroupedLmdbRowKeyFactory() {
        testUnique(new FlatTimeUngroupedLmdbRowKeyFactory(getUniqueIdProvider()));
    }

    @Test
    void testUniqueNestedGroupedLmdbRowKeyFactory() {
        final LmdbRowKeyFactory keyFactory = new NestedGroupedLmdbRowKeyFactory(
                getUniqueIdProvider(),
                getCompiledDepths(),
                new ValHasher(new OutputFactoryImpl(new SearchResultStoreConfig()), new ErrorConsumerImpl()));
        testUnique(keyFactory);

        final ByteBuffer key = keyFactory
                .create(1, 0, 100L, 100L);
        final LmdbKV lmdbKV = new LmdbKV(null, key, ByteBuffer.allocateDirect(0));
        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.getRowKey()));
        keyFactory.makeUnique(lmdbKV);
        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.getRowKey()));
    }

    @Test
    void testUniqueNestedTimeGroupedLmdbRowKeyFactory() {
        final LmdbRowKeyFactory keyFactory = new NestedTimeGroupedLmdbRowKeyFactory(
                getUniqueIdProvider(),
                getCompiledDepths(),
                new ValHasher(new OutputFactoryImpl(new SearchResultStoreConfig()), new ErrorConsumerImpl()));
        testUnique(keyFactory);

        final ByteBuffer key = keyFactory
                .create(1, 0, 100L, 100L);
        final LmdbKV lmdbKV = new LmdbKV(null, key, ByteBuffer.allocateDirect(0));
        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.getRowKey()));
        keyFactory.makeUnique(lmdbKV);
        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.getRowKey()));
    }

    private void testUnique(final LmdbRowKeyFactory keyFactory) {
        final ByteBuffer key = keyFactory
                .create(0, 0, 100L, 100L);
        final LmdbKV lmdbKV = new LmdbKV(null, key, ByteBuffer.allocateDirect(0));
        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.getRowKey()));
        keyFactory.makeUnique(lmdbKV);
        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.getRowKey()));
    }

    private UniqueIdProvider getUniqueIdProvider() {
        final AtomicLong uniqueId = new AtomicLong();
        return uniqueId::incrementAndGet;
    }

    private CompiledDepths getCompiledDepths() {
        final Field field = Field
                .builder()
                .id("test")
                .name("test")
                .expression("${Number}")
                .group(0)
                .build();
        final List<Field> fields = List.of(field);
        final FieldIndex fieldIndex = new FieldIndex();
        final CompiledFields compiledFields = CompiledFields.create(fields, fieldIndex, Collections.emptyMap());
        final CompiledField[] compiledFieldArray = compiledFields.getCompiledFields();
        final CompiledDepths compiledDepths = new CompiledDepths(compiledFieldArray, false);
        return compiledDepths;
    }
}
