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

package stroom.ruleset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.datasource.api.v2.DataSourceField;
import stroom.dictionary.DictionaryStore;
import stroom.feed.MetaMap;
import stroom.docref.DocRef;
import stroom.ruleset.shared.DataReceiptAction;
import stroom.ruleset.shared.Rule;
import stroom.ruleset.shared.RuleSet;
import stroom.streamstore.store.ExpressionMatcher;
import stroom.data.meta.api.ExpressionUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

class DataReceiptPolicyChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataReceiptPolicyChecker.class);

    private static final int ONE_MINUTE = 60 * 1000;

    private final RuleSetService ruleSetService;
    private final DictionaryStore dictionaryStore;
    private final DocRef policyRef;
    private final AtomicLong lastRefresh = new AtomicLong();
    private volatile Checker checker = new ReceiveAllChecker();

    DataReceiptPolicyChecker(final RuleSetService ruleSetService,
                             final DictionaryStore dictionaryStore,
                             final DocRef policyRef) {
        this.ruleSetService = ruleSetService;
        this.dictionaryStore = dictionaryStore;
        this.policyRef = policyRef;
    }

    DataReceiptAction check(final MetaMap metaMap) {
        return getChecker().check(metaMap);
    }

    private Checker getChecker() {
        if (needsRefresh()) {
            refresh();
        }

        return checker;
    }

    private boolean needsRefresh() {
        return lastRefresh.get() < (System.currentTimeMillis() - ONE_MINUTE);
    }

    private synchronized void refresh() {
        if (needsRefresh()) {
            // We need to examine the meta map and ensure we aren't dropping or rejecting this data.
            final RuleSet dataReceiptPolicy = ruleSetService.readDocument(policyRef);
            if (dataReceiptPolicy != null && dataReceiptPolicy.getRules() != null && dataReceiptPolicy.getFields() != null) {
                // Create a map of fields.
                final Map<String, DataSourceField> fieldMap = dataReceiptPolicy.getFields()
                        .stream()
                        .collect(Collectors.toMap(DataSourceField::getName, Function.identity()));

                // Also make sure we create a list of rules that are enabled and have at least one enabled term.
                final Set<String> fieldSet = new HashSet<>();
                final List<Rule> activeRules = new ArrayList<>();
                dataReceiptPolicy.getRules().forEach(rule -> {
                    if (rule.isEnabled() && rule.getExpression() != null && rule.getExpression().enabled()) {
                        final Set<String> set = new HashSet<>();
                        addToFieldSet(rule, set);
                        if (set.size() > 0) {
                            fieldSet.addAll(set);
                            activeRules.add(rule);
                        }
                    }
                });

                // Create a map of fields that are valid fields and have been used in the expressions.
                final Map<String, DataSourceField> usedFieldMap = fieldSet
                        .stream()
                        .map(fieldMap::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(DataSourceField::getName, Function.identity()));

                final ExpressionMatcher expressionMatcher = new ExpressionMatcher(usedFieldMap, dictionaryStore);
                checker = new CheckerImpl(expressionMatcher, activeRules, fieldMap);

            } else {
                checker = new ReceiveAllChecker();
            }

            lastRefresh.set(System.currentTimeMillis());
        }
    }

    private void addToFieldSet(final Rule rule, final Set<String> fieldSet) {
        if (rule.isEnabled()) {
            fieldSet.addAll(ExpressionUtil.fields(rule.getExpression()));
        }
    }

    private interface Checker {
        DataReceiptAction check(MetaMap metaMap);
    }

    private static class ReceiveAllChecker implements Checker {
        @Override
        public DataReceiptAction check(final MetaMap metaMap) {
            return DataReceiptAction.RECEIVE;
        }
    }

    private static class CheckerImpl implements Checker {
        private final ExpressionMatcher expressionMatcher;
        private final List<Rule> activeRules;
        private final Map<String, DataSourceField> fieldMap;

        CheckerImpl(final ExpressionMatcher expressionMatcher, final List<Rule> activeRules, final Map<String, DataSourceField> fieldMap) {
            this.expressionMatcher = expressionMatcher;
            this.activeRules = activeRules;
            this.fieldMap = fieldMap;
        }

        @Override
        public DataReceiptAction check(final MetaMap metaMap) {
            final Map<String, Object> attributeMap = createAttributeMap(metaMap, fieldMap);

            final Rule matchingRule = findMatchingRule(expressionMatcher, attributeMap, activeRules);
            if (matchingRule != null && matchingRule.getAction() != null) {
                return matchingRule.getAction();
            }

            // The default action is to receive data.
            return DataReceiptAction.RECEIVE;
        }

        private Map<String, Object> createAttributeMap(final MetaMap metaMap, final Map<String, DataSourceField> fieldMap) {
            final Map<String, Object> attributeMap = new HashMap<>();
            fieldMap.forEach((fieldName, field) -> {
                try {
                    final String string = metaMap.get(fieldName);
                    switch (field.getType()) {
                        case FIELD:
                            attributeMap.put(fieldName, string);
                            break;
                        default:
                            attributeMap.put(fieldName, getSafeLong(string));
                            break;
                    }
                } catch (final RuntimeException e) {
                    LOGGER.debug(e.getMessage(), e);
                }
            });
            return attributeMap;
        }

        private Long getSafeLong(final String string) {
            if (string != null) {
                try {
                    return Long.valueOf(string);
                } catch (final RuntimeException e) {
                    LOGGER.debug(e.getMessage(), e);
                }
            }

            return null;
        }

        private Rule findMatchingRule(final ExpressionMatcher expressionMatcher, final Map<String, Object> attributeMap, final List<Rule> activeRules) {
            for (final Rule rule : activeRules) {
                try {
                    if (expressionMatcher.match(attributeMap, rule.getExpression())) {
                        return rule;
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error("Error in rule '" + rule.toString() + "' - " + e.getMessage(), e);
                }
            }

            return null;
        }
    }
}