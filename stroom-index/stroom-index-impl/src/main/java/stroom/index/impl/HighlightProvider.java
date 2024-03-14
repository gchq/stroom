package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.expression.api.DateTimeSettings;
import stroom.index.shared.IndexFieldCache;
import stroom.query.api.v2.ExpressionOperator;

import java.util.Set;

public interface HighlightProvider {

    Set<String> getHighlights(DocRef indexDocRef,
                              IndexFieldCache indexFieldCache,
                              ExpressionOperator expression,
                              DateTimeSettings dateTimeSettings);
}
