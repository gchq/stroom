package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.IndexFieldCache;

import java.util.Set;

public interface HighlightProvider {

    Set<String> getHighlights(DocRef indexDocRef,
                              IndexFieldCache indexFieldCache,
                              ExpressionOperator expression,
                              DateTimeSettings dateTimeSettings);
}
