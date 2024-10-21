package stroom.query.common.v2;

import stroom.query.common.v2.ExpressionPredicateBuilder.QueryFieldPosition;
import stroom.query.common.v2.ExpressionPredicateBuilder.Values;
import stroom.query.language.functions.DateUtil;

import java.math.BigDecimal;

public class StringValues implements Values {

    private final String[] values;

    public StringValues(final String[] values) {
        this.values = values;
    }

    @Override
    public String getString(final QueryFieldPosition queryFieldPosition) {
        return values[queryFieldPosition.index()];
    }

    @Override
    public BigDecimal getNumber(final QueryFieldPosition queryFieldPosition) {
        final String val = values[queryFieldPosition.index()];
        try {
            return new BigDecimal(val);
        } catch (final NumberFormatException e) {
            return null;

//            throw new MatchException(
//                    "Expected a numeric value but was given string \"" + val.toString() + "\"");
        }
    }

    @Override
    public Long getDate(final QueryFieldPosition queryFieldPosition) {
        final String val = values[queryFieldPosition.index()];
        if (val != null) {
            try {
                return DateUtil.parseNormalDateTimeString(val);
            } catch (final NumberFormatException e) {
                return null;
//                        throw new MatchException(
//                                "Unable to parse a date/time from value \"" + string + "\"");
            }
        }
        return null;
    }

    @Override
    public boolean isNull(final QueryFieldPosition queryFieldPosition) {
        final String val = values[queryFieldPosition.index()];
        return val == null;
    }
}
