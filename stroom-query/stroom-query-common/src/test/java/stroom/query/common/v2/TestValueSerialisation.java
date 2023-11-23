package stroom.query.common.v2;

import stroom.expression.api.ExpressionContext;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

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
        KeyFactoryConfigImpl keyFactoryConfig =
                new KeyFactoryConfigImpl(SourceType.DASHBOARD_UI, compiledColumnArray, compiledDepths);
        final Serialisers serialisers = new Serialisers(new SearchResultStoreConfig());
        final KeyFactory keyFactory = KeyFactoryFactory.create(keyFactoryConfig, compiledDepths);
        final LmdbRowValueFactory lmdbRowValueFactory =
                new LmdbRowValueFactory(valueReferenceIndex, serialisers.getOutputFactory(), errorConsumer);
        final long timeMs = System.currentTimeMillis();
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
        compiledColumnArray[0].getGenerator().set(Val.of(ValLong.create(1L)), storedValues);
        // This item will not be grouped.
        final long uniqueId = keyFactory.getUniqueId();
        final Key parentKey = Key.ROOT_KEY;
        final Key key = parentKey.resolve(timeMs, uniqueId);
        final ByteBuffer rowValue = lmdbRowValueFactory.create(storedValues);

        final StoredValues readStoredValues = valueReferenceIndex.read(rowValue);
        Assertions.assertThat(readStoredValues).isEqualTo(storedValues);
    }
}
