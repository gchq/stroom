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
    private final Map<DocRef, List<RuleConfig>> pipelineRulesMap = new HashMap<>();
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

    public void addQueryHitForRule (final RuleConfig rule, final Long eventId) {
        addRuleForPipeline (rule.getPipeline(), rule);
        final String queryId = rule.getQueryId();
        if (!queryHitsMap.containsKey(queryId)){
            queryHitsMap.put(queryId,new HashSet<>());
        }
        queryHitsMap.get(queryId).add(eventId);
    }

    private void addRuleForPipeline (final DocRef pipeline, final RuleConfig ruleConfig){
        if (!pipelineRulesMap.containsKey(pipeline)){
            pipelineRulesMap.put(pipeline, new ArrayList<>());
        }
        pipelineRulesMap.get(pipeline).add(ruleConfig);
    }

}
