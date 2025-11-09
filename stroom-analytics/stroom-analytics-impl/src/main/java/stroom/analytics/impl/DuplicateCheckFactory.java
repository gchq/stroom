package stroom.analytics.impl;

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.query.common.v2.CompiledColumns;

import java.util.List;
import java.util.Optional;

public interface DuplicateCheckFactory {

    DuplicateCheck create(AbstractAnalyticRuleDoc analyticRuleDoc, CompiledColumns compiledColumns);

    /**
     * @return The list of column names or an empty {@link Optional} if the duplicate
     * store has not yet been initialised.
     */
    Optional<List<String>> fetchColumnNames(String analyticUuid);

}
