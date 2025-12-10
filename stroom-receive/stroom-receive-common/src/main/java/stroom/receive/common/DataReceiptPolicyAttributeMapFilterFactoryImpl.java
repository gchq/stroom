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

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapper;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactory;
import stroom.query.common.v2.ExpressionPredicateFactoryFactory;
import stroom.receive.common.ReceiveDataRuleSetService.BundledRules;
import stroom.receive.rules.shared.HashedReceiveDataRules;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.util.collections.CollectionUtil;
import stroom.util.collections.CollectionUtil.DuplicateMode;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
public class DataReceiptPolicyAttributeMapFilterFactoryImpl implements DataReceiptPolicyAttributeMapFilterFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
            DataReceiptPolicyAttributeMapFilterFactoryImpl.class);

    private static final ReceiveAction NO_MATCH_OR_NO_RULES_ACTION = ReceiveAction.REJECT;

    private final ReceiveDataRuleSetService ruleSetService;
    private final ExpressionPredicateFactoryFactory expressionPredicateFactoryFactory;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final Provider<ReceiveActionMetricsRecorder> receiveActionMetricsRecorderProvider;

    @Inject
    public DataReceiptPolicyAttributeMapFilterFactoryImpl(
            final ReceiveDataRuleSetService ruleSetService,
            final ExpressionPredicateFactoryFactory expressionPredicateFactoryFactory,
            final Provider<ReceiveDataConfig> receiveDataConfigProvider,
            final Provider<ReceiveActionMetricsRecorder> receiveActionMetricsRecorderProvider) {

        this.ruleSetService = ruleSetService;
        this.expressionPredicateFactoryFactory = expressionPredicateFactoryFactory;
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.receiveActionMetricsRecorderProvider = receiveActionMetricsRecorderProvider;
    }

    @Override
    public AttributeMapFilter create() {
        // We need to examine the meta map and ensure we aren't dropping or rejecting this data.
        BundledRules bundledRules = null;
        try {
            bundledRules = ruleSetService.getBundledRules();
        } catch (final Exception e) {
            LOGGER.error("Error fetching receipt policy rules: {}", LogUtil.exceptionMessage(e), e);
        }
        if (bundledRules == null) {
            // There has been some problem in obtaining the rules, as opposed to there being no rules.
            final ReceiveAction fallbackReceiveAction = Objects.requireNonNullElse(
                    receiveDataConfigProvider.get().getFallbackReceiveAction(),
                    ReceiveDataConfig.DEFAULT_FALLBACK_RECEIVE_ACTION);
            final AttributeMapFilter fallbackFilter = AttributeMapFilter.getBlanketFilter(fallbackReceiveAction);
            LOGGER.warn("Unable to fetch/read receipt policy rules. " +
                        "Using fallback action: {} and filter: '{}'",
                    fallbackReceiveAction, fallbackFilter);
            return fallbackFilter;
        } else {
            Checker checker = null;
            final List<ReceiveDataRule> rules = NullSafe.get(bundledRules, BundledRules::getRules);
            final List<QueryField> fields = NullSafe.get(bundledRules, BundledRules::getFields);

            if (NullSafe.hasItems(rules) && NullSafe.hasItems(fields)) {
                // Create a map of fields.
                final Map<String, QueryField> fieldNameToFieldMap = CollectionUtil.mapBy(
                        QueryField::getFldName,
                        DuplicateMode.THROW,
                        fields);

                // Also make sure we create a list of rules that are enabled and have at least one enabled term.
                final Set<String> fieldSet = new HashSet<>();
                final List<ReceiveDataRule> activeRules = new ArrayList<>();
                rules.forEach(rule -> {
                    if (isRuleActive(rule)) {
                        final Set<String> set = new HashSet<>();
                        addToFieldSet(rule, set);
                        if (!set.isEmpty()) {
                            fieldSet.addAll(set);
                        }
                        // expression may have no fields in it.
                        activeRules.add(rule);
                    }
                });

                if (NullSafe.hasItems(activeRules)) {
                    // Create a map of fields that are valid fields and have been used in the expressions.
                    final Map<String, QueryField> usedFieldMap = fieldSet
                            .stream()
                            .map(fieldName -> {
                                if (HashedReceiveDataRules.isHashedField(fieldName)) {
                                    // Create a QueryField for the suffixed field name
                                    final String strippedField = HashedReceiveDataRules.stripHashedSuffix(
                                            fieldName);
                                    final QueryField queryField = fieldNameToFieldMap.get(strippedField);
                                    return queryField.copy()
                                            .fldName(fieldName)
                                            .build();
                                } else {
                                    return fieldNameToFieldMap.get(fieldName);
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toMap(
                                    QueryField::getFldName,
                                    Function.identity()));

                    final ValueFunctionFactories<AttributeMap> valueFunctionFactories =
                            createAttributeMapExtractor(usedFieldMap);

                    final AttributeMapper attributeMapper = bundledRules.attributeMapper();

                    final ExpressionPredicateFactory expressionPredicateFactory =
                            expressionPredicateFactoryFactory.createFactory(bundledRules.wordListProvider());

                    checker = new CheckerImpl(
                            expressionPredicateFactory,
                            activeRules,
                            valueFunctionFactories,
                            attributeMapper,
                            NO_MATCH_OR_NO_RULES_ACTION, // Rules are essentially 'allow' rules rather than 'deny'.
                            receiveActionMetricsRecorderProvider.get());
                }
            }
            // If no rules then fall back to a reject-all filter
            return NullSafe.getOrElseGet(
                    checker,
                    DataReceiptPolicyAttributeMapFilter::new,
                    () -> {
                        final AttributeMapFilter filter = AttributeMapFilter.getBlanketFilter(
                                NO_MATCH_OR_NO_RULES_ACTION);
                        LOGGER.debug("Falling back to filter {}", filter);
                        return filter;
                    });
        }
    }

    private static boolean isRuleActive(final ReceiveDataRule rule) {
        return rule != null
               && rule.isEnabled()
               && (rule.getExpression() == null || rule.getExpression().enabled());
    }

    static ValueFunctionFactories<AttributeMap> createAttributeMapExtractor(final Map<String, QueryField> usedFields) {

        final Map<String, ValueFunctionFactory<AttributeMap>> fieldPositionMap = usedFields.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Entry::getKey,
                        entry -> {
                            final QueryField field = entry.getValue();
                            return new AttributeMapFunctionFactory(field);
                        }));
        return fieldPositionMap::get;
    }

    private void addToFieldSet(final ReceiveDataRule rule, final Set<String> fieldSet) {
        if (rule.isEnabled()) {
            fieldSet.addAll(ExpressionUtil.fields(rule.getExpression()));
        }
    }


    // --------------------------------------------------------------------------------


    public interface Checker {

        ReceiveAction check(AttributeMap attributeMap) throws StroomStreamException;
    }


    // --------------------------------------------------------------------------------


    private static class CheckerImpl implements Checker {

        private final ExpressionPredicateFactory expressionMatcher;
        private final List<ReceiveDataRule> activeRules;
        private final ValueFunctionFactories<AttributeMap> valueFunctionFactories;
        private final AttributeMapper attributeMapper;
        private final Map<Integer, Predicate<AttributeMap>> ruleNoToPredicateFactoryMap;
        private final ReceiveAction noMatchAction;
        private final ReceiveActionMetricsRecorder receiveActionMetricsRecorder;

        CheckerImpl(final ExpressionPredicateFactory expressionMatcher,
                    final List<ReceiveDataRule> activeRules,
                    final ValueFunctionFactories<AttributeMap> valueFunctionFactories,
                    final AttributeMapper attributeMapper,
                    final ReceiveAction noMatchAction,
                    final ReceiveActionMetricsRecorder receiveActionMetricsRecorder) {
            this.expressionMatcher = expressionMatcher;
            this.activeRules = activeRules;
            this.valueFunctionFactories = valueFunctionFactories;
            this.attributeMapper = attributeMapper;
            this.receiveActionMetricsRecorder = receiveActionMetricsRecorder;
            this.ruleNoToPredicateFactoryMap = new HashMap<>();
            this.noMatchAction = noMatchAction;
        }

        @Override
        public ReceiveAction check(final AttributeMap attributeMap) throws StroomStreamException {
            return LOGGER.logDurationIfDebugEnabled(
                    () -> {
                        // First we need to hash any values for fields that need hashing.
                        // Then we will be evaluating hashed values in the attribute map against hashed
                        // values in the terms.
                        final AttributeMap effectiveAttrMap = attributeMapper.mapAttributes(attributeMap);

                        final ReceiveDataRule matchingRule = findMatchingRule(effectiveAttrMap);
                        // The default action is to receive data.
                        final ReceiveAction receiveAction = NullSafe.getOrElse(
                                matchingRule,
                                ReceiveDataRule::getAction,
                                noMatchAction);

                        receiveActionMetricsRecorder.record(receiveAction);

                        final String ruleNo = NullSafe.getOrElse(
                                matchingRule,
                                ReceiveDataRule::getRuleNumber,
                                Object::toString,
                                "NO_MATCH");

                        attributeMap.put(StandardHeaderArguments.DATA_RECEIPT_RULE, ruleNo);

                        LOGGER.debug(() -> LogUtil.message(
                                "check() - matchingRule: '{}', ruleNo: {}, receiveAction: {}, " +
                                "noMatchAction: {}, rule count: {}",
                                matchingRule, ruleNo, receiveAction, noMatchAction, NullSafe.size(activeRules)));

                        if (receiveAction == ReceiveAction.REJECT) {
                            throw new StroomStreamException(StroomStatusCode.REJECTED_BY_POLICY_RULES, attributeMap);
                        } else {
                            return receiveAction;
                        }
                    },
                    ruleAction -> LogUtil.message("Checked attributeMap: {} with result: {}",
                            attributeMap, ruleAction));
        }

        private ReceiveDataRule findMatchingRule(final AttributeMap attributeMap) {
            for (final ReceiveDataRule rule : activeRules) {
                final ExpressionOperator ruleExpression = rule.getExpression();

                if (ruleExpression == null) {
                    LOGGER.trace(() -> LogUtil.message(
                            "findMatchingRule() - Null ruleExpression, rule {}, ruleAction: {}, attributeMap: {}",
                            rule, rule.getAction(), attributeMap));
                    return rule;
                }

                // Lazily create the predicate in case we match on the first rule
                final Predicate<AttributeMap> predicate = ruleNoToPredicateFactoryMap.computeIfAbsent(
                        rule.getRuleNumber(),
                        ruleNo -> expressionMatcher.create(ruleExpression, valueFunctionFactories));
                try {
                    final boolean isMatch = predicate.test(attributeMap);
                    LOGGER.trace(() -> LogUtil.message(
                            "findMatchingRule() - Rule {}, isMatch: {}, ruleAction: {}, attributeMap: {}",
                            rule, isMatch, rule.getAction(), attributeMap));
                    if (isMatch) {
                        return rule;
                    }
                    // Carry on to the next rule
                } catch (final RuntimeException e) {
                    LOGGER.error("Error in rule '{}': {}", rule, LogUtil.exceptionMessage(e), e);
                    // Try the next rule
                }
            }
            LOGGER.trace(() -> LogUtil.message("findMatchingRule() - No matched after {} active rules",
                    activeRules.size()));
            return null;
        }
    }
}
