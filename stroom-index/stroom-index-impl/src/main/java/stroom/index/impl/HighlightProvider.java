package stroom.index.impl;

import stroom.expression.api.DateTimeSettings;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.v2.ExpressionOperator;

import java.util.Set;

public interface HighlightProvider {

    Set<String> getHighlights(LuceneIndexDoc index,
                              ExpressionOperator expression,
                              DateTimeSettings dateTimeSettings);
}
