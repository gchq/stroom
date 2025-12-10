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
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ref.DataReader;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.query.language.functions.ref.KryoDataReader;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

public class TestValueSerialisation {

    @Test
    void test() {
        final Column column = Column.builder().id("test").name("test").expression("${Number}").build();
        final List<Column> columns = List.of(column);
        final FieldIndex fieldIndex = new FieldIndex();
        final ErrorConsumer errorConsumer = new ErrorConsumerImpl();
        final CompiledColumns compiledColumns = CompiledColumns
                .create(new ExpressionContext(), columns, fieldIndex, Collections.emptyMap());
        final CompiledColumn[] compiledColumnArray = compiledColumns.getCompiledColumns();
        final ValueReferenceIndex valueReferenceIndex = compiledColumns.getValueReferenceIndex();
        final CompiledDepths compiledDepths = new CompiledDepths(compiledColumnArray, false);
        final KeyFactoryConfigImpl keyFactoryConfig =
                new KeyFactoryConfigImpl(SourceType.DASHBOARD_UI, compiledColumnArray, compiledDepths);
        final ByteBufferFactory byteBufferFactory = new SimpleByteBufferFactory();
        final DataWriterFactory writerFactory =
                new DataWriterFactory(errorConsumer, 1000);
        final KeyFactory keyFactory = KeyFactoryFactory.create(keyFactoryConfig, compiledDepths);
        final LmdbRowValueFactory lmdbRowValueFactory =
                new LmdbRowValueFactory(byteBufferFactory, valueReferenceIndex, writerFactory);
        final long timeMs = System.currentTimeMillis();
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
        compiledColumnArray[0].getGenerator().set(Val.of(ValLong.create(1L)), storedValues);
        // This item will not be grouped.
        final long uniqueId = keyFactory.getUniqueId();
        final Key parentKey = Key.ROOT_KEY;
        final Key key = parentKey.resolve(timeMs, uniqueId);
        final ByteBuffer rowValue = lmdbRowValueFactory.create(storedValues);

        final StoredValues readStoredValues;
        try (final DataReader reader =
                new KryoDataReader(new ByteBufferInput(rowValue))) {
            readStoredValues = valueReferenceIndex.read(reader);
        }
        Assertions.assertThat(readStoredValues).isEqualTo(storedValues);

        byteBufferFactory.release(rowValue);
    }
}
