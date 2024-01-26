package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.StreamingAnalyticProcessConfig;
import stroom.query.api.v2.SearchRequest;
import stroom.view.shared.ViewDoc;

public record StreamingAnalytic(String ruleIdentity,
                                AnalyticRuleDoc analyticRuleDoc,
                                StreamingAnalyticProcessConfig streamingAnalyticProcessConfig,
                                SearchRequest searchRequest,
                                ViewDoc viewDoc) {

}
