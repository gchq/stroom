package stroom.query.common.v2;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.expression.api.ExpressionContext;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.TimeFilter;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.FlatGroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.FlatTimeGroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.FlatTimeUngroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.FlatUngroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.NestedGroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.NestedTimeGroupedLmdbRowKeyFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestLmdbRowKeyFactoryFactory {

    @Test
    void testFlatGroupedLmdbRowKeyFactory() {
        final FlatGroupedLmdbRowKeyFactory keyFactory = new FlatGroupedLmdbRowKeyFactory();
        testUnique(keyFactory);
        testNonTimeGroupedChildKeyRange(keyFactory);
    }

    @Test
    void testFlatUngroupedLmdbRowKeyFactory() {
        final FlatUngroupedLmdbRowKeyFactory keyFactory = new FlatUngroupedLmdbRowKeyFactory(getUniqueIdProvider());
        testUnique(keyFactory);
        testNonTimeGroupedChildKeyRange(keyFactory);
    }

    @Test
    void testFlatTimeGroupedLmdbRowKeyFactory() {
        final FlatTimeGroupedLmdbRowKeyFactory keyFactory = new FlatTimeGroupedLmdbRowKeyFactory();
        testUnique(keyFactory);
        testTimeGroupedChildKeyRange(keyFactory);
    }

    @Test
    void testFlatTimeUngroupedLmdbRowKeyFactory() {
        final FlatTimeUngroupedLmdbRowKeyFactory keyFactory =
                new FlatTimeUngroupedLmdbRowKeyFactory(getUniqueIdProvider());
        testUnique(keyFactory);
        testTimeGroupedChildKeyRange(keyFactory);
    }

    @Test
    void testNestedGroupedLmdbRowKeyFactory() {
        final LmdbRowKeyFactory keyFactory = new NestedGroupedLmdbRowKeyFactory(
                getUniqueIdProvider(),
                getCompiledDepths(),
                new ValHasher(new OutputFactoryImpl(new SearchResultStoreConfig()), new ErrorConsumerImpl()));
        testUnique(keyFactory);
        testNonTimeGroupedChildKeyRange(keyFactory);

        final ByteBuffer parentKey = keyFactory
                .create(0, null, 100L, 100L);
        final ByteBuffer key = keyFactory
                .create(1, parentKey, 100L, 100L);
        final LmdbKV lmdbKV = new LmdbKV(null, key, ByteBuffer.allocateDirect(0));
        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.getRowKey()));
        keyFactory.makeUnique(lmdbKV);
        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.getRowKey()));
    }

    @Test
    void testNestedTimeGroupedLmdbRowKeyFactory() {
        final LmdbRowKeyFactory keyFactory = new NestedTimeGroupedLmdbRowKeyFactory(
                getUniqueIdProvider(),
                getCompiledDepths(),
                new ValHasher(new OutputFactoryImpl(new SearchResultStoreConfig()), new ErrorConsumerImpl()));
        testUnique(keyFactory);
        testTimeGroupedChildKeyRange(keyFactory);

        final ByteBuffer parentKey = keyFactory
                .create(0, null, 100L, 100L);
        final ByteBuffer key = keyFactory
                .create(1, parentKey, 100L, 100L);
        final LmdbKV lmdbKV = new LmdbKV(null, key, ByteBuffer.allocateDirect(0));
        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.getRowKey()));
        keyFactory.makeUnique(lmdbKV);
        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.getRowKey()));
    }


    private void testUnique(final LmdbRowKeyFactory keyFactory) {
        final ByteBuffer key = keyFactory
                .create(0, null, 100L, 100L);
        final LmdbKV lmdbKV = new LmdbKV(null, key, ByteBuffer.allocateDirect(0));
        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.getRowKey()));
        keyFactory.makeUnique(lmdbKV);
        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.getRowKey()));
    }

    private void testNonTimeGroupedChildKeyRange(final LmdbRowKeyFactory keyFactory) {
        keyFactory.createChildKeyRange(Key.ROOT_KEY);
        assertThatThrownBy(() ->
                keyFactory.createChildKeyRange(Key.ROOT_KEY, new TimeFilter(0, 10)))
                .isInstanceOf(RuntimeException.class);

        final Key key = new Key(10, List.of(new GroupKeyPart(Val.of("one", "two"))));
        keyFactory.createChildKeyRange(key);
        assertThatThrownBy(() ->
                keyFactory.createChildKeyRange(key, new TimeFilter(0, 10)))
                .isInstanceOf(RuntimeException.class);
    }

    private void testTimeGroupedChildKeyRange(final LmdbRowKeyFactory keyFactory) {
        keyFactory.createChildKeyRange(Key.ROOT_KEY);
        keyFactory.createChildKeyRange(Key.ROOT_KEY, new TimeFilter(0, 10));

        final Key key = new Key(10, List.of(new GroupKeyPart(Val.of("one", "two"))));
        keyFactory.createChildKeyRange(key);
        keyFactory.createChildKeyRange(key, new TimeFilter(0, 10));
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
        final CompiledFields compiledFields = CompiledFields.create(new ExpressionContext(),
                fields, fieldIndex, Collections.emptyMap());
        final CompiledField[] compiledFieldArray = compiledFields.getCompiledFields();
        return new CompiledDepths(compiledFieldArray, false);
    }
}
