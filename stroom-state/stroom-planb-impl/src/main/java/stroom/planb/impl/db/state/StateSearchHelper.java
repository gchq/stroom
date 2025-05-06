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
import stroom.query.language.functions.ValuesConsumer;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class StateSearchHelper {

    public static void search(final ExpressionCriteria criteria,
                              final FieldIndex fieldIndex,
                              final DateTimeSettings dateTimeSettings,
                              final ExpressionPredicateFactory expressionPredicateFactory,
                              final ValuesConsumer consumer,
                              final ValExtractor[] valExtractors,
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
        env.read(readTxn -> {
            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(readTxn)) {
                for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                    final Val[] vals = new Val[valExtractors.length];
                    for (int i = 0; i < vals.length; i++) {
                        final ValExtractor valExtractor = valExtractors[i];
                        vals[i] = valExtractor.apply(readTxn, keyVal);
                    }
                    if (predicate.test(vals)) {
                        consumer.accept(vals);
                    }
                }
            }
            return null;
        });
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
}
