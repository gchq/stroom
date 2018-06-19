/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.data.store;

import stroom.dictionary.DictionaryStore;
import stroom.ruleset.shared.DataRetentionRule;
import stroom.data.meta.api.Stream;
import stroom.data.meta.api.StreamDataSource;
import stroom.util.date.DateUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public class StreamAttributeMapRetentionRuleDecorator {
    public static final String RETENTION_AGE = "Age";
    public static final String RETENTION_UNTIL = "Until";
    public static final String RETENTION_RULE = "Rule";

    private final List<DataRetentionRule> rules;
    private final ExpressionMatcher expressionMatcher;

    public StreamAttributeMapRetentionRuleDecorator(final DictionaryStore dictionaryStore, final List<DataRetentionRule> rules) {
        this.rules = rules;
        expressionMatcher = new ExpressionMatcher(StreamDataSource.getFieldMap(), dictionaryStore);
    }

    public void addMatchingRetentionRuleInfo(final Stream stream, final Map<String, String> metaMap) {
        int index = -1;

        // If there are no active rules then we aren't going to process anything.
        if (rules.size() > 0) {
            // Create an attribute map we can match on.
            final Map<String, Object> attributeMap = StreamAttributeMapUtil.createAttributeMap(stream, metaMap);
            index = findMatchingRuleIndex(attributeMap);
        }

        if (index != -1) {
            final DataRetentionRule rule = rules.get(index);
            metaMap.put(RETENTION_AGE, rule.getAgeString());

            String keepUntil = DataRetentionRule.FOREVER;
            if (stream != null) {
                final long millis = stream.getCreateMs();
                final LocalDateTime createTime = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDateTime();
                final Long ms = DataRetentionAgeUtil.plus(createTime, rule);
                if (ms != null) {
                    keepUntil = DateUtil.createNormalDateTimeString(ms);
                }
            }

            metaMap.put(RETENTION_UNTIL, keepUntil);
            metaMap.put(RETENTION_RULE, rule.toString());
        } else {
            metaMap.put(RETENTION_AGE, DataRetentionRule.FOREVER);
            metaMap.put(RETENTION_UNTIL, DataRetentionRule.FOREVER);
            metaMap.put(RETENTION_RULE, "None");
        }
    }

    private int findMatchingRuleIndex(final Map<String, Object> attributeMap) {
        for (int i = 0; i < rules.size(); i++) {
            final DataRetentionRule rule = rules.get(i);
            // We will ignore rules that are not enabled or have no enabled expression.
            if (rule.isEnabled() && rule.getExpression() != null && rule.getExpression().enabled()) {
                if (expressionMatcher.match(attributeMap, rule.getExpression())) {
                    return i;
                }
            }
        }

        return -1;
    }
}
