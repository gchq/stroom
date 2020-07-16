/*
 * Copyright 2020 Crown Copyright
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
package stroom.alert.impl;

import stroom.docref.DocRef;
import stroom.query.api.v2.TableSettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AlertQueryHits {
    private final Map<DocRef, Set<RuleConfig>> pipelineRulesMap = new HashMap<>();
    private final Map<String, Set<Long>> queryHitsMap = new HashMap<>();

    public AlertQueryHits() {
    }

    public final Collection<DocRef> getExtractionPipelines (){
        return pipelineRulesMap.keySet();
    }

    public final Collection <RuleConfig> getRulesForPipeline (final DocRef pipeline) {
        return pipelineRulesMap.get(pipeline);
    }

    public long [] getSortedQueryHitsForRule(final RuleConfig ruleConfig){
        Set<Long> hitSet = queryHitsMap.get(ruleConfig.getQueryId());
        return hitSet.stream().mapToLong(i -> i).toArray();
    }

    public synchronized void addQueryHitForRule (final RuleConfig rule, final Long eventId) {
        addRuleForPipeline (rule.getPipeline(), rule);
        final String queryId = rule.getQueryId();
        if (!queryHitsMap.containsKey(queryId)){
            queryHitsMap.put(queryId,new HashSet<>());
        }
        queryHitsMap.get(queryId).add(eventId);
    }

    public synchronized void clearHits(){
        queryHitsMap.clear();
    }

    private void addRuleForPipeline (final DocRef pipeline, final RuleConfig ruleConfig){
        if (!pipelineRulesMap.containsKey(pipeline)){
            pipelineRulesMap.put(pipeline, new HashSet<>());
        }
        pipelineRulesMap.get(pipeline).add(ruleConfig);
    }

}
