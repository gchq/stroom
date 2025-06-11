package stroom.query.api;

import stroom.query.api.ExpressionTerm.Condition;

public class StringExpressionUtil {

    public static String equalsCaseSensitive(final String string) {
        return Condition.EQUALS_CASE_SENSITIVE.getOperator() + "\"" + string + "\"";
    }

}
