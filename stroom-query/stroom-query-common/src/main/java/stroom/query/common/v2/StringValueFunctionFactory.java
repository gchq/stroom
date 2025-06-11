package stroom.query.common.v2;

import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactory;
import stroom.util.date.DateUtil;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Function;

public class StringValueFunctionFactory implements ValueFunctionFactory<String> {

    private final QueryField field;

    public StringValueFunctionFactory(final QueryField field) {
        this.field = field;
    }

    public static ValueFunctionFactories<String> create(final QueryField field) {
        final StringValueFunctionFactory stringValueFunctionFactory = new StringValueFunctionFactory(field);
        return fieldName -> stringValueFunctionFactory;
    }

    @Override
    public Function<String, Boolean> createNullCheck() {
        return Objects::isNull;
    }

    @Override
    public Function<String, String> createStringExtractor() {
        return string -> string;
    }

    @Override
    public Function<String, Long> createDateExtractor() {
        return DateUtil::parseNormalDateTimeString;
    }

    @Override
    public Function<String, Double> createNumberExtractor() {
        return string -> {
            try {
                return new BigDecimal(string).doubleValue();
            } catch (final RuntimeException e) {
                return null;
            }
        };
    }

    @Override
    public FieldType getFieldType() {
        return field.getFldType();
    }
}
