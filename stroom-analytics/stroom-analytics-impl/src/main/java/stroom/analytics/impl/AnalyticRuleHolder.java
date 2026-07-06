/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.analytics.impl;

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.util.pipeline.scope.PipelineScoped;

/**
 * Pipeline-scoped holder for the analytic rule document currently being executed.
 * <p>
 * This allows pipeline-scoped components (such as {@link AnalyticErrorWriter}) to
 * access the rule identity without requiring it to be threaded through method signatures.
 * </p>
 * <p>
 * When multiple rules are being processed against the same stream, callers should
 * switch the active rule in and out as each rule is processed, so that errors are
 * attributed to the correct rule.
 * </p>
 * <p>
 * Follows the same holder pattern as {@link stroom.pipeline.state.MetaDataHolder}.
 * </p>
 */
@PipelineScoped
public class AnalyticRuleHolder {

    private String ruleIdentity;

    public String getRuleIdentity() {
        return ruleIdentity;
    }

    /**
     * Set the active rule identity from a rule doc.
     */
    public void setAnalyticRuleDoc(final AbstractAnalyticRuleDoc analyticRuleDoc) {
        if (analyticRuleDoc != null) {
            this.ruleIdentity = RuleUtil.getRuleIdentity(analyticRuleDoc);
        } else {
            this.ruleIdentity = null;
        }
    }

    /**
     * Clear the active rule identity, e.g. when switching between rules
     * or when processing shared/general work.
     */
    public void clear() {
        this.ruleIdentity = null;
    }
}
