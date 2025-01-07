package stroom.analytics.impl;

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.query.common.v2.CompiledColumns;

public interface DuplicateCheckFactory {

    DuplicateCheck create(AbstractAnalyticRuleDoc analyticRuleDoc, CompiledColumns compiledColumns);

}
