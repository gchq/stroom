package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.dashboard.expression.v1.ref.StoredValues;
import stroom.dashboard.expression.v1.ref.ValueReferenceIndex;
import stroom.query.api.v2.Field;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

public class TestValueSerialisation {

    @Test
    void test() {
        final Field field = Field.builder().id("test").name("test").expression("${Number}").build();
        final List<Field> fields = List.of(field);
        final FieldIndex fieldIndex = new FieldIndex();
        final ErrorConsumer errorConsumer = new ErrorConsumerImpl();
        final CompiledFields compiledFields = CompiledFields.create(fields, fieldIndex, Collections.emptyMap());
        final CompiledField[] compiledFieldArray = compiledFields.getCompiledFields();
        final ValueReferenceIndex valueReferenceIndex = compiledFields.getValueReferenceIndex();
        final CompiledDepths compiledDepths = new CompiledDepths(compiledFieldArray, false);
        KeyFactoryConfigImpl keyFactoryConfig =
                new KeyFactoryConfigImpl(compiledFieldArray, compiledDepths, DataStoreSettings.builder().build());
        final Serialisers serialisers = new Serialisers(new SearchResultStoreConfig());
        final KeyFactory keyFactory = KeyFactoryFactory.create(serialisers, keyFactoryConfig, compiledDepths);
        final LmdbRowKeyFactory lmdbRowKeyFactory =
                LmdbRowKeyFactoryFactory.create(keyFactory, keyFactoryConfig, compiledDepths);
        final LmdbRowValueFactory lmdbRowValueFactory =
                new LmdbRowValueFactory(keyFactory, valueReferenceIndex, serialisers.getOutputFactory(), errorConsumer);
        final long groupHash = ValHasher.hash(Val.empty());
        final long timeMs = System.currentTimeMillis();
        final LmdbRowKey rowKey = lmdbRowKeyFactory
                .create(0, 0, groupHash, timeMs);
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
        compiledFieldArray[0].getGenerator().set(Val.of(ValLong.create(1L)), storedValues);
        // This item will not be grouped.
        final long uniqueId = keyFactory.getUniqueId();
        final Key parentKey = Key.ROOT_KEY;
        final Key key = parentKey.resolve(timeMs, uniqueId);
        final LmdbRowValue rowValue = lmdbRowValueFactory.create(key, storedValues);

        final LmdbValueBytes lmdbValueBytes = LmdbValueBytes.create(serialisers.getInputFactory(),
                rowValue.getByteBuffer());
        final StoredValues storedValues2 =
                valueReferenceIndex.read(
                        serialisers.getInputFactory(),
                        lmdbValueBytes.valueBytes());
        Assertions.assertThat(storedValues2).isEqualTo(storedValues);
    }
}
