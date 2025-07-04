package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.Format;
import stroom.query.api.datasource.FieldType;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactory;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.Values;
import stroom.util.date.DateUtil;

import java.util.function.Function;

public class ValuesFunctionFactory implements ValueFunctionFactory<Values> {

    private final Column column;
    private final int index;

    public ValuesFunctionFactory(final Column column, final int index) {
        this.column = column;
        this.index = index;
    }

    @Override
    public Function<Values, Boolean> createNullCheck() {
        return item -> stroom.query.language.functions.Type.NULL.equals(item.getValue(index).type());
    }

    @Override
    public Function<Values, String> createStringExtractor() {
        return item -> item.getValue(index).toString();
    }

    @Override
    public Function<Values, Long> createDateExtractor() {
        return item -> {
            final Val val = item.getValue(index);
            if (Type.LONG.equals(val.type()) || Type.DATE.equals(val.type())) {
                return val.toLong();
            } else {
                final String string = val.toString();
                if (string != null) {
                    try {
                        return DateUtil.parseNormalDateTimeString(string);
                    } catch (final NumberFormatException e) {
//                        throw new MatchException(
//                                "Unable to parse a date/time from value \"" + string + "\"");
                    }
                }
            }
            return null;
        };
    }

    @Override
    public Function<Values, Double> createNumberExtractor() {
        return item -> {
            try {
                final Val val = item.getValue(index);
                return val.toDouble();
            } catch (final RuntimeException e) {
                return null;
            }
        };
    }

    @Override
    public FieldType getFieldType() {
        FieldType fieldType = FieldType.TEXT;
        if (column.getFormat() != null) {
            if (Format.Type.NUMBER.equals(column.getFormat().getType())) {
                fieldType = FieldType.LONG;
            } else if (Format.Type.DATE_TIME.equals(column.getFormat().getType())) {
                fieldType = FieldType.DATE;
            }
        }
        return fieldType;
    }
}
