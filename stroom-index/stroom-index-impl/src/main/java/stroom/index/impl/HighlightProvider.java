package stroom.index.impl;

import stroom.expression.api.DateTimeSettings;
import stroom.index.shared.IndexDoc;
import stroom.query.api.v2.ExpressionOperator;

import java.util.Set;

public interface HighlightProvider {

    Set<String> getHighlights(IndexDoc index,
                              ExpressionOperator expression,
                              DateTimeSettings dateTimeSettings);
}
