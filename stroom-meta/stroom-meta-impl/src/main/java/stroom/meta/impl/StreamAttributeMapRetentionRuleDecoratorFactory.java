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

package stroom.meta.impl;

import stroom.data.retention.api.DataRetentionRulesProvider;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.query.api.ExpressionOperator;
import stroom.security.api.SecurityContext;
import stroom.util.date.DateUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Singleton
class StreamAttributeMapRetentionRuleDecoratorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamAttributeMapRetentionRuleDecoratorFactory.class);

    private final ExpressionMatcher expressionMatcher;
    private final DataRetentionRulesProvider dataRetentionRulesProvider;
    private final StreamAttributeMapConverter streamAttributeMapConverter;
    private final SecurityContext securityContext;

    @Inject
    public StreamAttributeMapRetentionRuleDecoratorFactory(
            final ExpressionMatcherFactory expressionMatcherFactory,
            final DataRetentionRulesProvider dataRetentionRulesProvider,
            final StreamAttributeMapConverter streamAttributeMapConverter,
            final SecurityContext securityContext) {

        this.dataRetentionRulesProvider = dataRetentionRulesProvider;
        this.streamAttributeMapConverter = streamAttributeMapConverter;
        this.expressionMatcher = expressionMatcherFactory.create(MetaFields.getFieldMap());
        this.securityContext = securityContext;
    }

    private List<DataRetentionRule> getRules() {
        // The user will not likely hold the MANAGE_POLICIES_PERMISSION app perm,
        // so we need to run this as the processing user to be able to see which rule matches the stream
        return securityContext.asProcessingUserResult(() -> {
            final List<DataRetentionRule> rules = Optional.ofNullable(dataRetentionRulesProvider)
                    .flatMap(DataRetentionRulesProvider::get)
                    .map(DataRetentionRules::getRules)
                    .orElse(Collections.emptyList());
            LOGGER.debug("getRules() - rules: {}", rules);
            return rules;
        });
    }

    StreamAttributeMapRetentionRuleDecorator createDecorator() {
        final List<DataRetentionRule> rules = getRules();
        return new StreamAttributeMapRetentionRuleDecorator(rules);
    }


    // --------------------------------------------------------------------------------


    class StreamAttributeMapRetentionRuleDecorator {

        final List<DataRetentionRule> rules;

        public StreamAttributeMapRetentionRuleDecorator(final List<DataRetentionRule> rules) {
            this.rules = rules;
        }

        void addMatchingRetentionRuleInfo(final Meta meta, final Map<String, String> attributeMap) {
            LOGGER.debug("addMatchingRetentionRuleInfo() - meta: {}, attributeMap: {}", meta, attributeMap);
            try {
                int index = -1;

                final List<DataRetentionRule> rules = getRules();

                // If there are no active rules, then we aren't going to process anything.
                if (NullSafe.hasItems(rules)) {
                    // Create an attribute map we can match on.
                    final Map<String, Object> map = streamAttributeMapConverter.createAttributeMap(meta, attributeMap);
                    index = findMatchingRuleIndex(rules, map);
                }

                if (index > -1) {
                    final DataRetentionRule rule = rules.get(index);
                    LOGGER.debug("addMatchingRetentionRuleInfo() - meta: {}, rule: {}", meta, rule);
                    attributeMap.put(DataRetentionFields.RETENTION_AGE, rule.getAgeString());

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

                    attributeMap.put(DataRetentionFields.RETENTION_UNTIL, keepUntil);
                    attributeMap.put(DataRetentionFields.RETENTION_RULE, rule.toString());
                } else {
                    attributeMap.put(DataRetentionFields.RETENTION_AGE, DataRetentionRule.FOREVER);
                    attributeMap.put(DataRetentionFields.RETENTION_UNTIL, DataRetentionRule.FOREVER);
                    attributeMap.put(DataRetentionFields.RETENTION_RULE, "None");
                }
            } catch (final RuntimeException e) {
                final String msg = Objects.requireNonNullElseGet(e.getMessage(), () -> e.getClass().getSimpleName());
                attributeMap.put(DataRetentionFields.RETENTION_AGE, DataRetentionRule.FOREVER);
                attributeMap.put(DataRetentionFields.RETENTION_UNTIL, DataRetentionRule.FOREVER);
                attributeMap.put(DataRetentionFields.RETENTION_RULE, "Error - " + msg);
            }
            LOGGER.debug("addMatchingRetentionRuleInfo() - meta: {}, decorated attributeMap: {}", meta, attributeMap);
        }

        private int findMatchingRuleIndex(final List<DataRetentionRule> rules,
                                          final Map<String, Object> attributeMap) {
            RuntimeException lastException = null;

            for (int i = 0; i < rules.size(); i++) {
                try {
                    final DataRetentionRule rule = rules.get(i);
                    // We will ignore rules that are not enabled or have no enabled expression.
                    if (rule.isEnabled()) {
                        final ExpressionOperator expression = rule.getExpression();
                        if (NullSafe.test(expression, ExpressionOperator::enabled)) {
                            if (expressionMatcher.match(attributeMap, expression)) {
                                return i;
                            }
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
}
