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

package stroom.query.common.v2;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.SimpleByteBufferFactory;
import stroom.query.api.Column;
import stroom.query.api.TimeFilter;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.StoredValues;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestLmdbRowKeyFactoryFactory {

//    @Test
//    void testFlatGroupedLmdbRowKeyFactory() {
//        final FlatGroupedLmdbRowKeyFactory keyFactory =
//                new FlatGroupedLmdbRowKeyFactory(getByteBufferFactory(), getStoredValueKeyFactory());
//        testUnique(keyFactory);
//        testNonTimeGroupedChildKeyRange(keyFactory);
//    }
//
//    @Test
//    void testFlatUngroupedLmdbRowKeyFactory() {
//        final FlatUngroupedLmdbRowKeyFactory keyFactory =
//                new FlatUngroupedLmdbRowKeyFactory(getByteBufferFactory(), getUniqueIdProvider());
//        testUnique(keyFactory);
//        testNonTimeGroupedChildKeyRange(keyFactory);
//    }

//    @Test
//    void testFlatTimeGroupedLmdbRowKeyFactory() {
//        final FlatTimeGroupedLmdbRowKeyFactory keyFactory =
//                new FlatTimeGroupedLmdbRowKeyFactory(getByteBufferFactory(), getStoredValueKeyFactory());
//        testUnique(keyFactory);
//        testTimeGroupedChildKeyRange(keyFactory);
//    }
//
//    @Test
//    void testFlatTimeUngroupedLmdbRowKeyFactory() {
//        final FlatTimeUngroupedLmdbRowKeyFactory keyFactory =
//                new FlatTimeUngroupedLmdbRowKeyFactory(
//                        getByteBufferFactory(), getUniqueIdProvider(), getStoredValueKeyFactory());
//        testUnique(keyFactory);
//        testTimeGroupedChildKeyRange(keyFactory);
//    }

//    @Test
//    void testNestedGroupedLmdbRowKeyFactory() {
//        final LmdbRowKeyFactory keyFactory = new NestedGroupedLmdbRowKeyFactory(
//                getByteBufferFactory(),
//                getUniqueIdProvider(),
//                getCompiledDepths(),
//                getStoredValueKeyFactory());
//        testUnique(keyFactory);
//        testNonTimeGroupedChildKeyRange(keyFactory);
//
//        final StoredValues storedValues = new StoredValues(new Object[]{ValLong.create(100L), ValLong.create(100L)});
//        final ByteBuffer parentKey = keyFactory.create(0, null, storedValues);
//        final ByteBuffer key = keyFactory.create(1, parentKey, storedValues);
//        final LmdbKV lmdbKV = new LmdbKV(null, key, ByteBuffer.allocateDirect(0));
//        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.key()));
//        keyFactory.makeUnique(lmdbKV);
//        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.key()));
//    }
//
//    @Test
//    void testNestedTimeGroupedLmdbRowKeyFactory() {
//        final LmdbRowKeyFactory keyFactory = new NestedTimeGroupedLmdbRowKeyFactory(
//                getByteBufferFactory(),
//                getUniqueIdProvider(),
//                getCompiledDepths(),
//                getStoredValueKeyFactory());
//        testUnique(keyFactory);
//        testTimeGroupedChildKeyRange(keyFactory);
//
//        final StoredValues storedValues = new StoredValues(new Object[]{ValLong.create(100L), ValLong.create(100L)});
//        final ByteBuffer parentKey = keyFactory.create(0, null, storedValues);
//        final ByteBuffer key = keyFactory.create(1, parentKey, storedValues);
//        final LmdbKV lmdbKV = new LmdbKV(null, key, ByteBuffer.allocateDirect(0));
//        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.key()));
//        keyFactory.makeUnique(lmdbKV);
//        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.key()));
//    }
//
//
//    private void testUnique(final LmdbRowKeyFactory keyFactory) {
//        final StoredValues storedValues = new StoredValues(new Object[]{ValLong.create(100L), ValLong.create(100L)});
//        final ByteBuffer key = keyFactory.create(0, null, storedValues);
//        final LmdbKV lmdbKV = new LmdbKV(null, key, ByteBuffer.allocateDirect(0));
//        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.key()));
//        keyFactory.makeUnique(lmdbKV);
//        System.out.println(ByteBufferUtils.byteBufferToString(lmdbKV.key()));
//    }

    private void testNonTimeGroupedChildKeyRange(final LmdbRowKeyFactory keyFactory) {
        keyFactory.createChildKeyRange(Key.ROOT_KEY, keyRange -> {
        });
        assertThatThrownBy(() ->
                keyFactory.createChildKeyRange(Key.ROOT_KEY, new TimeFilter(0, 10), keyRange -> {
                }))
                .isInstanceOf(RuntimeException.class);

        final Key key = new Key(10, List.of(new GroupKeyPart(Val.of("one", "two"))));
        keyFactory.createChildKeyRange(key, keyRange -> {
        });
        assertThatThrownBy(() ->
                keyFactory.createChildKeyRange(key, new TimeFilter(0, 10), keyRange -> {
                }))
                .isInstanceOf(RuntimeException.class);
    }

    private void testTimeGroupedChildKeyRange(final LmdbRowKeyFactory keyFactory) {
        keyFactory.createChildKeyRange(Key.ROOT_KEY, keyRange -> {
        });
        keyFactory.createChildKeyRange(Key.ROOT_KEY, new TimeFilter(0, 10), keyRange -> {
        });

        final Key key = new Key(10, List.of(new GroupKeyPart(Val.of("one", "two"))));
        keyFactory.createChildKeyRange(key, keyRange -> {
        });
        keyFactory.createChildKeyRange(key, new TimeFilter(0, 10), keyRange -> {
        });
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

    private ByteBufferFactory getByteBufferFactory() {
        return new SimpleByteBufferFactory() {
        };
    }

    private StoredValueKeyFactory getStoredValueKeyFactory() {
        return new StoredValueKeyFactory() {
            @Override
            public Val[] getGroupValues(final int depth, final StoredValues storedValues) {
                return Val.EMPTY_VALUES;
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
