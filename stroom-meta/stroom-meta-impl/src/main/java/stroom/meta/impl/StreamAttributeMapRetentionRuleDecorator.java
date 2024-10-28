/*
 * Copyright 2024 Crown Copyright
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

package stroom.meta.impl;

import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.datasource.api.v2.QueryField;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class StreamAttributeMapRetentionRuleDecorator {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamAttributeMapRetentionRuleDecorator.class);

    private final List<DataRetentionRule> rules;
    private final ExpressionMatcher expressionMatcher;

    @Inject
    public StreamAttributeMapRetentionRuleDecorator(final ExpressionMatcherFactory expressionMatcherFactory,
                                                    final Provider<DataRetentionRules> dataRetentionRulesProvider) {
        expressionMatcher = expressionMatcherFactory.create(MetaFields.getFieldMap());

        rules = Optional.ofNullable(dataRetentionRulesProvider)
                .map(Provider::get)
                .map(DataRetentionRules::getRules)
                .orElse(Collections.emptyList());
    }

    void addMatchingRetentionRuleInfo(final Meta meta,
                                      final Map<String, String> attributeMap) {
        try {
            int index = -1;

            // If there are no active rules then we aren't going to process anything.
            if (NullSafe.hasItems(rules)) {
                // Create an attribute map we can match on.
                final Map<CIKey, Object> map = StreamAttributeMapUtil.createAttributeMap(meta, attributeMap);
                index = findMatchingRuleIndex(map);
            }

            if (index != -1) {
                final DataRetentionRule rule = rules.get(index);
                putAttr(attributeMap, DataRetentionFields.RETENTION_AGE_FIELD, rule.getAgeString());

                String keepUntil = DataRetentionRule.FOREVER;
                if (meta != null) {
                    final long millis = meta.getCreateMs();
                    final LocalDateTime createTime = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDateTime();
                    final Long ms = DataRetentionAgeUtil.plus(createTime, rule);
                    if (ms != null) {
                        keepUntil = DateUtil.createNormalDateTimeString(ms);
                    }
                }

                putAttr(attributeMap, DataRetentionFields.RETENTION_UNTIL_FIELD, keepUntil);
                putAttr(attributeMap, DataRetentionFields.RETENTION_RULE_FIELD, rule.toString());
            } else {
                putAttr(attributeMap, DataRetentionFields.RETENTION_AGE_FIELD, DataRetentionRule.FOREVER);
                putAttr(attributeMap, DataRetentionFields.RETENTION_UNTIL_FIELD, DataRetentionRule.FOREVER);
                putAttr(attributeMap, DataRetentionFields.RETENTION_RULE_FIELD, "None");
            }
        } catch (final RuntimeException e) {
            putAttr(attributeMap, DataRetentionFields.RETENTION_AGE_FIELD, DataRetentionRule.FOREVER);
            putAttr(attributeMap, DataRetentionFields.RETENTION_UNTIL_FIELD, DataRetentionRule.FOREVER);
            putAttr(attributeMap, DataRetentionFields.RETENTION_RULE_FIELD, "Error - " + e.getMessage());
        }
    }

    private void putAttr(final Map<String, String> attributeMap,
                         final QueryField queryField,
                         final String value) {
        attributeMap.put(queryField.getFldName(), value);
    }

    private int findMatchingRuleIndex(final Map<CIKey, Object> attributeMap) {
        RuntimeException lastException = null;

        for (int i = 0; i < rules.size(); i++) {
            try {
                final DataRetentionRule rule = rules.get(i);
                // We will ignore rules that are not enabled or have no enabled expression.
                if (rule.isEnabled() && rule.getExpression() != null && rule.getExpression().enabled()) {
                    if (expressionMatcher.match(attributeMap, rule.getExpression())) {
                        return i;
                    }
                }
            } catch (final RuntimeException e) {
                lastException = e;
                LOGGER.debug(e.getMessage(), e);
            }
        }

        if (lastException != null) {
            throw lastException;
        }

        return -1;
    }
}
