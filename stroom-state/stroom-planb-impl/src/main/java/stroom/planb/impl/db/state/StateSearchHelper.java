package stroom.planb.impl.db.state;

import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Format;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ValArrayFunctionFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class StateSearchHelper {

    public static void search(final Txn<ByteBuffer> readTxn,
                              final ExpressionCriteria criteria,
                              final FieldIndex fieldIndex,
                              final DateTimeSettings dateTimeSettings,
                              final ExpressionPredicateFactory expressionPredicateFactory,
                              final ValuesConsumer consumer,
                              final ValuesExtractor valuesExtractor,
                              final PlanBEnv env,
                              final Dbi<ByteBuffer> dbi) {
        // Ensure we have fields for all expression criteria.
        final List<String> fields = ExpressionUtil.fields(criteria.getExpression());
        fields.forEach(fieldIndex::create);

        final ValueFunctionFactories<Val[]> valueFunctionFactories = createValueFunctionFactories(fieldIndex);
        final Optional<Predicate<Val[]>> optionalPredicate = expressionPredicateFactory
                .createOptional(criteria.getExpression(), valueFunctionFactories, dateTimeSettings);
        final Predicate<Val[]> predicate = optionalPredicate.orElse(vals -> true);

        // TODO : It would be faster if we limit the iteration to keys based on the criteria.
        try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(readTxn)) {
            for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                final Val[] vals = valuesExtractor.apply(readTxn, keyVal);
                if (predicate.test(vals)) {
                    consumer.accept(vals);
                }
            }
        }
    }

    private static ValueFunctionFactories<Val[]> createValueFunctionFactories(final FieldIndex fieldIndex) {
        return fieldName -> {
            final Integer index = fieldIndex.getPos(fieldName);
            if (index == null) {
                throw new RuntimeException("Unexpected field: " + fieldName);
            }
            return new ValArrayFunctionFactory(Column.builder().format(Format.TEXT).build(), index);
        };
    }

    public static ValuesExtractor createValuesExtractor(final FieldIndex fieldIndex,
                                                        final Function<Context, Val> keyValueConverter,
                                                        final Function<Context, Val> valValueFunction) {
        final String[] fields = fieldIndex.getFields();
        final Converter[] converters = new Converter[fields.length];
        final boolean extractValue = fieldIndex.getPos(StateFields.VALUE_TYPE) != null ||
                                     fieldIndex.getPos(StateFields.VALUE) != null;
        final Function<Context, Val> svf;
        if (extractValue) {
            svf = valValueFunction;
        } else {
            svf = context -> null;
        }

        for (int i = 0; i < fields.length; i++) {
            converters[i] = switch (fields[i]) {
                case StateFields.KEY -> (context, val) -> keyValueConverter.apply(context);
                case StateFields.VALUE_TYPE -> (context, val) -> ValString.create(val.type().toString());
                case StateFields.VALUE -> (context, val) -> val;
                default -> (context, stateValue) -> ValNull.INSTANCE;
            };
        }
        return (readTxn, kv) -> {
            final Context context = new Context(readTxn, kv);
            final Val[] values = new Val[fields.length];
            final Val val = svf.apply(context);
            for (int i = 0; i < fields.length; i++) {
                values[i] = converters[i].convert(context, val);
            }
            return values;
        };
    }

    public record Context(Txn<ByteBuffer> readTxn, KeyVal<ByteBuffer> kv) {

    }

    public interface Converter {

        Val convert(Context context, Val val);
    }
}
