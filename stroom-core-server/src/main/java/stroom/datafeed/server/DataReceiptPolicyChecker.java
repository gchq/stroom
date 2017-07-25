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
 */

package stroom.datafeed.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.datasource.api.v1.DataSourceField;
import stroom.dictionary.shared.DictionaryService;
import stroom.feed.MetaMap;
import stroom.policy.server.DataReceiptService;
import stroom.policy.shared.DataReceiptAction;
import stroom.policy.shared.DataReceiptPolicy;
import stroom.policy.shared.DataReceiptRule;
import stroom.query.api.v1.ExpressionItem;
import stroom.query.api.v1.ExpressionOperator;
import stroom.query.api.v1.ExpressionTerm;
import stroom.streamstore.server.ExpressionMatcher;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DataReceiptPolicyChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataReceiptPolicyChecker.class);

    private static final int ONE_MINUTE = 60 * 1000;

    private final DataReceiptService dataReceiptService;
    private final DictionaryService dictionaryService;

    private volatile Checker checker;

    @Inject
    DataReceiptPolicyChecker(final DataReceiptService dataReceiptService,
                             final DictionaryService dictionaryService) {
        this.dataReceiptService = dataReceiptService;
        this.dictionaryService = dictionaryService;
    }

    public DataReceiptAction check(final MetaMap metaMap) {
        return getChecker().check(metaMap);
    }

    private Checker getChecker() {
        if (needsRefresh()) {
            refresh();
        }

        return checker;
    }

    private boolean needsRefresh() {
        final Checker checker = this.checker;
        return checker == null || checker.creationTime < (System.currentTimeMillis() - ONE_MINUTE);
    }

    private synchronized void refresh() {
        if (needsRefresh()) {
            checker = new Checker(dataReceiptService, dictionaryService);
        }
    }

    private static class Checker {
        private final ExpressionMatcher expressionMatcher;
        private final List<DataReceiptRule> activeRules;
        private final Map<String, DataSourceField> fieldMap;
        private final long creationTime;

        Checker(final DataReceiptService dataReceiptService, final DictionaryService dictionaryService) {
            this.creationTime = System.currentTimeMillis();

            // We need to examine the meta map and ensure we aren't dropping or rejecting this data.
            final DataReceiptPolicy dataReceiptPolicy = dataReceiptService.load();
            if (dataReceiptPolicy != null && dataReceiptPolicy.getRules() != null && dataReceiptPolicy.getFields() != null) {
                // Create a map of fields.
                final Map<String, DataSourceField> fieldMap = dataReceiptPolicy.getFields()
                        .stream()
                        .collect(Collectors.toMap(DataSourceField::getName, Function.identity()));

                // Also make sure we create a list of rules that are enabled and have at least one enabled term.
                final Set<String> fieldSet = new HashSet<>();
                this.activeRules = new ArrayList<>();
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
                this.fieldMap = fieldSet
                        .stream()
                        .map(fieldMap::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(DataSourceField::getName, Function.identity()));

                this.expressionMatcher = new ExpressionMatcher(this.fieldMap, dictionaryService);
            } else {
                this.fieldMap = null;
                this.activeRules = null;
                this.expressionMatcher = null;
            }
        }

        public DataReceiptAction check(final MetaMap metaMap) {
            if (expressionMatcher != null) {
                final Map<String, Object> attributeMap = createAttributeMap(metaMap, fieldMap);

                final DataReceiptRule matchingRule = findMatchingRule(expressionMatcher, attributeMap, activeRules);
                if (matchingRule != null && matchingRule.getAction() != null) {
                    return matchingRule.getAction();
                }
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
                } catch (final Exception e) {
                    LOGGER.debug(e.getMessage(), e);
                }
            });
            return attributeMap;
        }

        private Long getSafeLong(final String string) {
            if (string != null) {
                try {
                    return Long.valueOf(string);
                } catch (final Exception e) {
                    LOGGER.debug(e.getMessage(), e);
                }
            }

            return null;
        }

        private void addToFieldSet(final DataReceiptRule rule, final Set<String> fieldSet) {
            if (rule.isEnabled() && rule.getExpression() != null) {
                addChildren(rule.getExpression(), fieldSet);
            }
        }

        private void addChildren(final ExpressionItem item, final Set<String> fieldSet) {
            if (item.enabled()) {
                if (item instanceof ExpressionOperator) {
                    final ExpressionOperator operator = (ExpressionOperator) item;
                    operator.getChildren().forEach(i -> addChildren(i, fieldSet));
                } else if (item instanceof ExpressionTerm) {
                    final ExpressionTerm term = (ExpressionTerm) item;
                    fieldSet.add(term.getField());
                }
            }
        }

        private DataReceiptRule findMatchingRule(final ExpressionMatcher expressionMatcher, final Map<String, Object> attributeMap, final List<DataReceiptRule> activeRules) {
            for (final DataReceiptRule rule : activeRules) {
                try {
                    if (expressionMatcher.match(attributeMap, rule.getExpression())) {
                        return rule;
                    }
                } catch (final Exception e) {
                    LOGGER.error("Error in rule '" + rule.toString() + "' - " + e.getMessage(), e);
                }
            }

            return null;
        }
    }
}