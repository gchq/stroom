package stroom.expression.matcher;

import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionTerm.Condition;

public interface TermMatcher {
    boolean match(QueryField queryField, Condition condition, String termValue, DocRef docRef);

}
