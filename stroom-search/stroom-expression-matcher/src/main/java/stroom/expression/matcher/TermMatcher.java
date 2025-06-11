package stroom.expression.matcher;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;

public interface TermMatcher {
    boolean match(QueryField queryField, Condition condition, String termValue, DocRef docRef);

}
