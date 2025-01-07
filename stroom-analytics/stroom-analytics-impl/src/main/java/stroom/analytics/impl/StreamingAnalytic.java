package stroom.analytics.impl;

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.query.api.v2.SearchRequest;
import stroom.view.shared.ViewDoc;

public record StreamingAnalytic(String ruleIdentity,
                                AbstractAnalyticRuleDoc analyticRuleDoc,
                                SearchRequest searchRequest,
                                ViewDoc viewDoc) {

}
