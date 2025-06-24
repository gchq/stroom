package stroom.meta.impl;

import stroom.meta.shared.MetaFields;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.SimpleStringExpressionParser;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MetaExpressionHelper {

    private static final FieldProvider FIELD_PROVIDER;

    private static final Map<String, QueryField> QUERY_FIELD_MAP;

    static {
        QUERY_FIELD_MAP = MetaFields.getAllFields()
                .stream()
                .collect(Collectors.toMap(field -> getSimpleName(field
                        .getFldName()), Function.identity()));
        FIELD_PROVIDER = new FieldProvider() {
            @Override
            public List<String> getDefaultFields() {
                return List.of(MetaFields.FEED_NAME.getFldName(), MetaFields.TYPE.getFldName());
            }

            @Override
            public Optional<String> getQualifiedField(final String string) {
                final String simpleName = getSimpleName(string);
                final QueryField queryField = QUERY_FIELD_MAP.get(simpleName);
                return Optional.ofNullable(queryField).map(QueryField::getFldName);
            }
        };
    }

    private static String getSimpleName(final String name) {
        return name
                .replaceAll(" ", "")
                .toLowerCase(Locale.ROOT);
    }

    public static ExpressionOperator parseFilterExpression(final String filter) {
        ExpressionOperator expressionOperator = SimpleStringExpressionParser
                .create(FIELD_PROVIDER, filter, Condition.EQUALS).orElse(ExpressionOperator.builder().build());
        final List<String> fields = ExpressionUtil.fields(expressionOperator);
        if (!fields.contains(MetaFields.STATUS.getFldName())) {
            final ExpressionTerm statusTerm = ExpressionTerm
                    .builder()
                    .field(MetaFields.STATUS)
                    .condition(Condition.EQUALS)
                    .value("Unlocked")
                    .build();
            if (Op.AND.equals(expressionOperator.op())) {
                expressionOperator = expressionOperator
                        .copy()
                        .addTerm(statusTerm)
                        .build();
            } else {
                expressionOperator = ExpressionOperator
                        .builder()
                        .addOperator(expressionOperator)
                        .addTerm(statusTerm)
                        .build();
            }
        }
        return expressionOperator;
    }
}
