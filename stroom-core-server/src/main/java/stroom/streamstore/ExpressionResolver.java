package stroom.streamstore;

import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.entity.shared.EntityServiceException;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.common.v2.DateExpressionParser;
import stroom.util.date.DateUtil;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A class that resolves placeholders in an expression to return a static expression.
 */
public class ExpressionResolver {
    private static final String DELIMITER = ",";

    private final Map<String, DataSourceField> fieldMap;
    private final Context context;

    ExpressionResolver(final Map<String, DataSourceField> fieldMap,
                       final Context context) {
        this.fieldMap = fieldMap;
        this.context = context;
    }

    public ExpressionOperator resolve(final ExpressionOperator expressionOperator) {
        final ExpressionOperator.Builder builder = new Builder(expressionOperator.enabled(), expressionOperator.getOp());
        expressionOperator.getChildren().forEach(item -> appendItem(builder, item));
        return builder.build();
    }

    private void appendItem(final ExpressionOperator.Builder parent, final ExpressionItem item) {
        if (item instanceof ExpressionOperator) {
            final ExpressionOperator expressionOperator = (ExpressionOperator) item;
            final ExpressionOperator.Builder builder = new Builder(expressionOperator.enabled(), expressionOperator.getOp());
            expressionOperator.getChildren().forEach(child -> appendItem(builder, child));
            parent.addOperator(builder.build());

        } else if (item instanceof ExpressionTerm) {
            final ExpressionTerm term = (ExpressionTerm) item;
            final DataSourceField dataSourceField = fieldMap.get(term.getField());

            String value = term.getValue();
            if (DataSourceFieldType.DATE_FIELD.equals(dataSourceField.getType())) {
                final long[] dates = getDates(term.getField(), term.getValue());
                final String[] values = new String[dates.length];
                for (int i = 0; i < dates.length; i++) {
                    values[i] = DateUtil.createNormalDateTimeString(dates[i]);
                }
                value = Arrays.stream(values).collect(Collectors.joining(DELIMITER));
            }

            // TODO : Resolve dictionary values too????

            parent.addTerm(term.getField(), term.getCondition(), value);
        }
    }

    private long[] getDates(final String fieldName, final String value) {
        final String[] values = value.split(DELIMITER);
        final long[] dates = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            dates[i] = getDate(fieldName, values[i].trim());
        }

        return dates;
    }

    private long getDate(final String fieldName, final String value) {
        try {
            //empty optional will be caught below
            return DateExpressionParser.parse(value, context.timeZoneId, context.nowEpochMilli).get().toInstant().toEpochMilli();
        } catch (final RuntimeException e) {
            throw new EntityServiceException("Expected a standard date value for field \"" + fieldName
                    + "\" but was given string \"" + value + "\"");
        }
    }

    public static class Context {
        private final String timeZoneId;
        private final long nowEpochMilli;

        public Context(final String timeZoneId, final long nowEpochMilli) {
            this.timeZoneId = timeZoneId;
            this.nowEpochMilli = nowEpochMilli;
        }

        public static ExpressionToFindCriteria.Context now() {
            return new ExpressionToFindCriteria.Context(null, System.currentTimeMillis());
        }
    }
}
