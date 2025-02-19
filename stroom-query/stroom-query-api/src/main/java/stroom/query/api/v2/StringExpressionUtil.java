package stroom.query.api.v2;

import stroom.query.api.v2.ExpressionTerm.Condition;

public class StringExpressionUtil {

    public static String equalsCaseSensitive(final String string) {
        return Condition.EQUALS_CASE_SENSITIVE.getOperator() + "\"" + string + "\"";
    }

}
