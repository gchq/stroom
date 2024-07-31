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

package stroom.receive.rules.impl;

import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.meta.api.AttributeMap;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.receive.rules.shared.RuleAction;
import stroom.util.NullSafe;
import stroom.util.shared.string.CIKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

class ReceiveDataPolicyChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiveDataPolicyChecker.class);

    private static final int ONE_MINUTE = 60 * 1000;

    private final ReceiveDataRuleSetService ruleSetService;
    private final ExpressionMatcherFactory expressionMatcherFactory;
    private final DocRef policyRef;
    private final AtomicLong lastRefresh = new AtomicLong();
    private volatile Checker checker = new ReceiveAllChecker();

    ReceiveDataPolicyChecker(final ReceiveDataRuleSetService ruleSetService,
                             final ExpressionMatcherFactory expressionMatcherFactory,
                             final DocRef policyRef) {
        this.ruleSetService = ruleSetService;
        this.expressionMatcherFactory = expressionMatcherFactory;
        this.policyRef = policyRef;
    }

    RuleAction check(final AttributeMap attributeMap) {
        return getChecker().check(attributeMap);
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
            ReceiveDataRules dataReceiptPolicy = null;
            try {
                dataReceiptPolicy = ruleSetService.readDocument(policyRef);
            } catch (Exception e) {
                LOGGER.error("Error reading rule set {}. The default receive all policy will be applied",
                        policyRef, e);
            }
            if (dataReceiptPolicy != null
                    && dataReceiptPolicy.getRules() != null
                    && dataReceiptPolicy.getFields() != null) {
                // Create a map of fields.
                final Map<CIKey, QueryField> fieldMap = dataReceiptPolicy.getFields()
                        .stream()
                        .collect(Collectors.toMap(
                                (QueryField queryField1) -> CIKey.of(queryField1.getFldName()),
                                Function.identity()));

                // Also make sure we create a list of rules that are enabled and have at least one enabled term.
                final Set<String> fieldSet = new HashSet<>();
                final List<ReceiveDataRule> activeRules = new ArrayList<>();
                dataReceiptPolicy.getRules().forEach(rule -> {
                    if (rule.isEnabled()
                            && NullSafe.test(rule.getExpression(), ExpressionOperator::enabled)) {
                        final Set<String> set = new HashSet<>();
                        addToFieldSet(rule, set);
                        if (!set.isEmpty()) {
                            fieldSet.addAll(set);
                        }
                        // expression may have no fields in it.
                        activeRules.add(rule);
                    }
                });

                // Create a map of fields that are valid fields and have been used in the expressions.
                final Map<CIKey, QueryField> usedFieldMap = fieldSet
                        .stream()
                        .map(fieldMap::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(
                                (QueryField queryField) -> CIKey.of(queryField.getFldName()),
                                Function.identity()));

                final ExpressionMatcher expressionMatcher = expressionMatcherFactory.create(usedFieldMap);
                checker = new CheckerImpl(expressionMatcher, activeRules, fieldMap);

            } else {
                checker = new ReceiveAllChecker();
            }

            lastRefresh.set(System.currentTimeMillis());
        }
    }

    private void addToFieldSet(final ReceiveDataRule rule, final Set<String> fieldSet) {
        if (rule.isEnabled()) {
            fieldSet.addAll(ExpressionUtil.fields(rule.getExpression()));
        }
    }


    // --------------------------------------------------------------------------------


    private interface Checker {

        RuleAction check(AttributeMap attributeMap);
    }


    // --------------------------------------------------------------------------------


    private static class ReceiveAllChecker implements Checker {

        @Override
        public RuleAction check(final AttributeMap attributeMap) {
            return RuleAction.RECEIVE;
        }
    }


    // --------------------------------------------------------------------------------


    private static class CheckerImpl implements Checker {

        private final ExpressionMatcher expressionMatcher;
        private final List<ReceiveDataRule> activeRules;
        private final Map<CIKey, QueryField> fieldMap;

        CheckerImpl(final ExpressionMatcher expressionMatcher,
                    final List<ReceiveDataRule> activeRules,
                    final Map<CIKey, QueryField> fieldMap) {
            this.expressionMatcher = expressionMatcher;
            this.activeRules = activeRules;
            this.fieldMap = fieldMap;
        }

        @Override
        public RuleAction check(final AttributeMap attributeMap) {
            final Map<CIKey, Object> map = createAttributeMap(attributeMap, fieldMap);

            final ReceiveDataRule matchingRule = findMatchingRule(expressionMatcher, map, activeRules);
            if (matchingRule != null && matchingRule.getAction() != null) {
                return matchingRule.getAction();
            }

            // The default action is to receive data.
            return RuleAction.RECEIVE;
        }

        private Map<CIKey, Object> createAttributeMap(final AttributeMap attributeMap,
                                                      final Map<CIKey, QueryField> fieldMap) {
            final Map<CIKey, Object> map = new HashMap<>();
            fieldMap.forEach((fieldName, field) -> {
                try {
                    final String string = attributeMap.get(fieldName.get());
                    switch (field.getFldType()) {
                        case TEXT:
                            map.put(fieldName, string);
                            break;
                        default:
                            map.put(fieldName, getSafeLong(string));
                            break;
                    }
                } catch (final RuntimeException e) {
                    LOGGER.debug(e.getMessage(), e);
                }
            });
            return map;
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

        private ReceiveDataRule findMatchingRule(final ExpressionMatcher expressionMatcher,
                                                 final Map<CIKey, Object> attributeMap,
                                                 final List<ReceiveDataRule> activeRules) {
            for (final ReceiveDataRule rule : activeRules) {
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
