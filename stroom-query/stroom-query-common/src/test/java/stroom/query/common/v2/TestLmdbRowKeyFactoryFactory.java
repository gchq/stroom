package stroom.query.common.v2;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.expression.api.ExpressionContext;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.TimeFilter;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.FlatGroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.FlatTimeGroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.FlatTimeUngroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.FlatUngroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.NestedGroupedLmdbRowKeyFactory;
import stroom.query.common.v2.LmdbRowKeyFactoryFactory.NestedTimeGroupedLmdbRowKeyFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ref.StoredValues;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestLmdbRowKeyFactoryFactory {

    @Test
    void testFlatGroupedLmdbRowKeyFactory() {
        final FlatGroupedLmdbRowKeyFactory keyFactory =
                new FlatGroupedLmdbRowKeyFactory(getStoredValueKeyFactory());
        testUnique(keyFactory);
        testNonTimeGroupedChildKeyRange(keyFactory);
    }

    @Test
    void testFlatUngroupedLmdbRowKeyFactory() {
        final FlatUngroupedLmdbRowKeyFactory keyFactory =
                new FlatUngroupedLmdbRowKeyFactory(getUniqueIdProvider());
        testUnique(keyFactory);
        testNonTimeGroupedChildKeyRange(keyFactory);
    }

    @Test
    void testFlatTimeGroupedLmdbRowKeyFactory() {
        final FlatTimeGroupedLmdbRowKeyFactory keyFactory =
                new FlatTimeGroupedLmdbRowKeyFactory(getStoredValueKeyFactory());
        testUnique(keyFactory);
        testTimeGroupedChildKeyRange(keyFactory);
    }

    @Test
    void testFlatTimeUngroupedLmdbRowKeyFactory() {
        final FlatTimeUngroupedLmdbRowKeyFactory keyFactory =
                new FlatTimeUngroupedLmdbRowKeyFactory(getUniqueIdProvider(), getStoredValueKeyFactory());
        testUnique(keyFactory);
        testTimeGroupedChildKeyRange(keyFactory);
    }

    @Test
    void testNestedGroupedLmdbRowKeyFactory() {
        final LmdbRowKeyFactory keyFactory = new NestedGroupedLmdbRowKeyFactory(
                getUniqueIdProvider(),
                getCompiledDepths(),
                getStoredValueKeyFactory());
        testUnique(keyFactory);
        testNonTimeGroupedChildKeyRange(keyFactory);

        final StoredValues storedValues = new StoredValues(new Object[]{ValLong.create(100L), ValLong.create(100L)});
        final ByteBuffer parentKey = keyFactory.create(0, null, storedValues);
        final ByteBuffer key = keyFactory.create(1, parentKey, storedValues);
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
                getStoredValueKeyFactory());
        testUnique(keyFactory);
        testTimeGroupedChildKeyRange(keyFactory);

        final StoredValues storedValues = new StoredValues(new Object[]{ValLong.create(100L), ValLong.create(100L)});
        final ByteBuffer parentKey = keyFactory.create(0, null, storedValues);
        final ByteBuffer key = keyFactory.create(1, parentKey, storedValues);
        final LmdbKV lmdbKV = new LmdbKV(null, key, ByteBuffer.allocateDirect(0));
        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.getRowKey()));
        keyFactory.makeUnique(lmdbKV);
        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.getRowKey()));
    }


    private void testUnique(final LmdbRowKeyFactory keyFactory) {
        final StoredValues storedValues = new StoredValues(new Object[]{ValLong.create(100L), ValLong.create(100L)});
        final ByteBuffer key = keyFactory.create(0, null, storedValues);
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
        final Column column = Column
                .builder()
                .id("test")
                .name("test")
                .expression("${Number}")
                .group(0)
                .build();
        final List<Column> columns = List.of(column);
        final FieldIndex fieldIndex = new FieldIndex();
        final CompiledColumns compiledColumns = CompiledColumns.create(new ExpressionContext(),
                columns, fieldIndex, Collections.emptyMap());
        final CompiledColumn[] compiledColumnArray = compiledColumns.getCompiledColumns();
        return new CompiledDepths(compiledColumnArray, false);
    }

    private ValHasher getValHasher() {
        return new ValHasher(new OutputFactoryImpl(new SearchResultStoreConfig()), new ErrorConsumerImpl());
    }

    private StoredValueKeyFactory getStoredValueKeyFactory() {
        return new StoredValueKeyFactory() {
            @Override
            public Val[] getGroupValues(final int depth, final StoredValues storedValues) {
                return new Val[0];
            }

            @Override
            public long getGroupHash(final int depth, final StoredValues storedValues) {
                return 0;
            }

            @Override
            public long hash(final Val[] groupValues) {
                return 0;
            }

            @Override
            public long getTimeMs(final StoredValues storedValues) {
                return 0;
            }
        };
    }
}
