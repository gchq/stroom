package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.query.common.v2.CompiledColumns;

public interface DuplicateCheckFactory {

    DuplicateCheck create(AnalyticRuleDoc analyticRuleDoc, CompiledColumns compiledColumns);

}
