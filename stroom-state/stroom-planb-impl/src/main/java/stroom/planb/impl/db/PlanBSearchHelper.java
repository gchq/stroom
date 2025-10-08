package stroom.planb.impl.db;

import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb.stream.LmdbIterable;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Format;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ValuesFunctionFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.Values;
import stroom.query.language.functions.ValuesConsumer;

import org.lmdbjava.Dbi;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class PlanBSearchHelper {

    public static void search(final Txn<ByteBuffer> readTxn,
                              final ExpressionCriteria criteria,
                              final FieldIndex fieldIndex,
                              final DateTimeSettings dateTimeSettings,
                              final ExpressionPredicateFactory expressionPredicateFactory,
                              final ValuesConsumer consumer,
                              final ValuesExtractor valuesExtractor,
                              final Dbi<ByteBuffer> dbi) {
        // Ensure we have fields for all expression criteria.
        final List<String> fields = ExpressionUtil.fields(criteria.getExpression());
        fields.forEach(fieldIndex::create);

        final ValueFunctionFactories<Values> valueFunctionFactories = createValueFunctionFactories(fieldIndex);
        final Optional<Predicate<Values>> optionalPredicate = expressionPredicateFactory
                .createOptional(criteria.getExpression(), valueFunctionFactories, dateTimeSettings);
        final Predicate<Values> predicate = optionalPredicate.orElse(vals -> true);

        // TODO : It would be faster if we limit the iteration to keys based on the criteria.
        LmdbIterable.iterate(readTxn, dbi, (key, val) -> {
            final Values vals = valuesExtractor.apply(readTxn, key, val);
            if (predicate.test(vals)) {
                consumer.accept(vals.toArray());
            }
        });
    }

    public static ValueFunctionFactories<Values> createValueFunctionFactories(final FieldIndex fieldIndex) {
        return fieldName -> {
            final Integer index = fieldIndex.getPos(fieldName);
            if (index == null) {
                throw new RuntimeException("Unexpected field: " + fieldName);
            }
            return new ValuesFunctionFactory(Column.builder().format(Format.TEXT).build(), index);
        };
    }

    public record Context(Txn<ByteBuffer> readTxn, ByteBuffer key, ByteBuffer val) {

    }

    public static class LazyKV<K, V> {

        private final Context context;
        private final Function<Context, K> keyFunction;
        private final Function<Context, V> valFunction;
        private K key;
        private V val;

        public LazyKV(final Context context,
                      final Function<Context, K> keyFunction,
                      final Function<Context, V> valFunction) {
            this.context = context;
            this.keyFunction = keyFunction;
            this.valFunction = valFunction;
        }

        public K getKey() {
            if (key == null) {
                key = keyFunction.apply(context);
            }
            return key;
        }

        public V getValue() {
            if (val == null) {
                val = valFunction.apply(context);
            }
            return val;
        }
    }

    public interface Converter<K, V> {

        Val convert(LazyKV<K, V> lazyKV);
    }

    public interface ValuesExtractor {

        Values apply(Txn<ByteBuffer> readTxn, ByteBuffer key, ByteBuffer value);
    }
}
