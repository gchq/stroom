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

package stroom.receive.common;

import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.QueryField;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapper;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactory;
import stroom.query.common.v2.ExpressionPredicateFactoryFactory;
import stroom.receive.common.ReceiveDataRuleSetService.BundledRules;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
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
//    private final CachedValue<AttributeMapFilter, Void> cachedFilter; // = new ReceiveAllChecker();

    @Inject
    public DataReceiptPolicyAttributeMapFilterFactoryImpl(
            final ReceiveDataRuleSetService ruleSetService,
            final ExpressionPredicateFactoryFactory expressionPredicateFactoryFactory,
            final Provider<ReceiveDataConfig> receiveDataConfigProvider) {

        this.ruleSetService = ruleSetService;
        this.expressionPredicateFactoryFactory = expressionPredicateFactoryFactory;
        this.receiveDataConfigProvider = receiveDataConfigProvider;
//        this.cachedFilter = CachedValue.builder()
//                .withMaxCheckIntervalSeconds(60)
//                .withoutStateSupplier()
//                .withValueSupplier(this::doCreate)
//                .build();
//
//        // Eagerly init the filter
//        this.cachedFilter.getValue();
    }

    @Override
    public AttributeMapFilter create() {
        // We need to examine the meta map and ensure we aren't dropping or rejecting this data.
        BundledRules bundledRules = null;
        try {
            bundledRules = ruleSetService.getBundledRules();
        } catch (Exception e) {
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
                final Map<String, QueryField> fieldNameToFieldMap = fields.stream()
                        .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));

                // Also make sure we create a list of rules that are enabled and have at least one enabled term.
                final Set<String> fieldSet = new HashSet<>();
                final List<ReceiveDataRule> activeRules = new ArrayList<>();
                rules.forEach(rule -> {
                    if (rule.isEnabled() && NullSafe.test(rule.getExpression(), ExpressionItem::enabled)) {
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
                            .map(fieldNameToFieldMap::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));

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
                            NO_MATCH_OR_NO_RULES_ACTION); // Rules are essentially 'allow' rules rather than 'deny'.
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

//    private AttributeMapFilter doCreate() {
//        // We need to examine the meta map and ensure we aren't dropping or rejecting this data.
//        BundledRules bundledRules = null;
//        Checker checker = null;
//        try {
//            bundledRules = ruleSetService.getBundledRules();
//        } catch (Exception e) {
//            LOGGER.error("Error reading rule set. The default receive all policy will be applied", e);
//        }
//
//        final List<ReceiveDataRule> rules = NullSafe.get(bundledRules, BundledRules::getRules);
//        final List<QueryField> fields = NullSafe.get(bundledRules, BundledRules::getFields);
//
//        if (NullSafe.hasItems(rules) && NullSafe.hasItems(fields)) {
//            // Create a map of fields.
//            final Map<String, QueryField> fieldNameToFieldMap = fields.stream()
//                    .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
//
//            // Also make sure we create a list of rules that are enabled and have at least one enabled term.
//            final Set<String> fieldSet = new HashSet<>();
//            final List<ReceiveDataRule> activeRules = new ArrayList<>();
//            rules.forEach(rule -> {
//                if (rule.isEnabled() && NullSafe.test(rule.getExpression(), ExpressionItem::enabled)) {
//                    final Set<String> set = new HashSet<>();
//                    addToFieldSet(rule, set);
//                    if (!set.isEmpty()) {
//                        fieldSet.addAll(set);
//                    }
//                    // expression may have no fields in it.
//                    activeRules.add(rule);
//                }
//            });
//
//            if (NullSafe.hasItems(activeRules)) {
//                // Create a map of fields that are valid fields and have been used in the expressions.
//                final Map<String, QueryField> usedFieldMap = fieldSet
//                        .stream()
//                        .map(fieldNameToFieldMap::get)
//                        .filter(Objects::nonNull)
//                        .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
//
//                final ValueFunctionFactories<AttributeMap> valueFunctionFactories =
//                        createAttributeMapExtractor(usedFieldMap);
//
//                final AttributeMapper attributeMapper = bundledRules.attributeMapper();
//
//                final ExpressionPredicateFactory expressionPredicateFactory =
//                        expressionPredicateFactoryFactory.createFactory(bundledRules.wordListProvider());
//
//                checker = new CheckerImpl(
//                        expressionPredicateFactory,
//                        activeRules,
//                        valueFunctionFactories,
//                        attributeMapper);
//            }
//        }
//        // If no rules then fall back to a receive-all filter
//        return NullSafe.getOrElseGet(
//                checker,
//                DataReceiptPolicyAttributeMapFilter::new,
//                PermissiveAttributeMapFilter::getInstance);
//    }

    static ValueFunctionFactories<AttributeMap> createAttributeMapExtractor(final Map<String, QueryField> usedFields) {

        final Map<String, ValueFunctionFactory<AttributeMap>> fieldPositionMap = usedFields.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Entry::getKey,
                        entry -> new AttributeMapFunctionFactory(entry.getValue())));
        return fieldPositionMap::get;
    }

    private void addToFieldSet(final ReceiveDataRule rule, final Set<String> fieldSet) {
        if (rule.isEnabled()) {
            fieldSet.addAll(ExpressionUtil.fields(rule.getExpression()));
        }
    }


    // --------------------------------------------------------------------------------


    public interface Checker {

        ReceiveAction check(AttributeMap attributeMap);
    }


    // --------------------------------------------------------------------------------


//    private static class ReceiveAllChecker implements Checker {
//
//        public static final ReceiveAllChecker INSTANCE = new ReceiveAllChecker();
//
//        @Override
//        public RuleAction check(final AttributeMap attributeMap) {
//            return RuleAction.RECEIVE;
//        }
//    }


    // --------------------------------------------------------------------------------


    private static class CheckerImpl implements Checker {

        private final ExpressionPredicateFactory expressionMatcher;
        private final List<ReceiveDataRule> activeRules;
        private final ValueFunctionFactories<AttributeMap> valueFunctionFactories;
        private final AttributeMapper attributeMapper;
        private final Map<Integer, Predicate<AttributeMap>> ruleNoToPredicateFactoryMap;
        private final ReceiveAction noMatchAction;

        CheckerImpl(final ExpressionPredicateFactory expressionMatcher,
                    final List<ReceiveDataRule> activeRules,
                    final ValueFunctionFactories<AttributeMap> valueFunctionFactories,
                    final AttributeMapper attributeMapper,
                    final ReceiveAction noMatchAction) {
            this.expressionMatcher = expressionMatcher;
            this.activeRules = activeRules;
            this.valueFunctionFactories = valueFunctionFactories;
            this.attributeMapper = attributeMapper;
            this.ruleNoToPredicateFactoryMap = new HashMap<>();
            this.noMatchAction = noMatchAction;
        }

        @Override
        public ReceiveAction check(final AttributeMap attributeMap) {
            return LOGGER.logDurationIfDebugEnabled(
                    () -> {
                        // First we need to hash any values for fields that need hashing.
                        // Then we will be evaluating hashed values in the attribute map against hashed
                        // values in the terms.
                        final AttributeMap effectiveAttrMap = attributeMapper.mapAttributes(attributeMap);

                        final ReceiveDataRule matchingRule = findMatchingRule(effectiveAttrMap);
                        // The default action is to receive data.
                        LOGGER.debug("check() - matchingRule: {}, noMatchAction: {}",
                                matchingRule, noMatchAction);
                        return NullSafe.getOrElse(
                                matchingRule,
                                ReceiveDataRule::getAction,
                                noMatchAction);
                    },
                    ruleAction -> LogUtil.message("Checked attributeMap: {} with result: {}",
                            attributeMap, ruleAction));
        }

//        private Predicate<AttributeMap> createPredicate(final ReceiveDataRule rule) {
//            return expressionMatcher.create(rule.getExpression(), valueFunctionFactories);
//        }

        private ReceiveDataRule findMatchingRule(final AttributeMap attributeMap) {
            for (final ReceiveDataRule rule : activeRules) {
                // Lazily create the predicate in case we match on the first rule
                final Predicate<AttributeMap> predicate = ruleNoToPredicateFactoryMap.computeIfAbsent(
                        rule.getRuleNumber(),
                        ruleNo -> expressionMatcher.create(rule.getExpression(), valueFunctionFactories));
                try {
                    final boolean isMatch = predicate.test(attributeMap);
                    LOGGER.debug("Rule {}, attributeMap: {}, result: {}", rule, attributeMap, isMatch);
                    if (isMatch) {
                        return rule;
                    }
                    // Carry on to the next rule
                } catch (final RuntimeException e) {
                    LOGGER.error("Error in rule '{}': {}", rule, LogUtil.exceptionMessage(e), e);
                    // Try the next rule
                }
            }
            return null;
        }
    }


// --------------------------------------------------------------------------------


//    private static class AttributeMapHasher {
//
//        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AttributeMapHasher.class);
//
//        private final HashFunction hashFunction;
//        private final Map<String, String> fieldNameToSaltMap;
//
//        private AttributeMapHasher(final HashFunction hashFunction,
//                                   final Map<String, String> fieldNameToSaltMap) {
//            this.hashFunction = hashFunction;
//            this.fieldNameToSaltMap = fieldNameToSaltMap;
//        }
//
//        /**
//         * Return a new {@link AttributeMap} instance containing all entries from attributeMap,
//         * except that those values that need to be hashed will have been hashed.
//         */
//        public AttributeMap hashValues(final AttributeMap attributeMap) {
//            if (attributeMap == null) {
//                return null;
//            } else if (attributeMap.isEmpty()) {
//                return new AttributeMap(attributeMap.isOverrideEmbeddedMeta());
//            } else {
//                final AttributeMap newAttrMap = new AttributeMap(attributeMap.isOverrideEmbeddedMeta());
//                LOGGER.logDurationIfDebugEnabled(
//                        () -> {
//                            attributeMap.forEach((key, val) -> {
//                                final String salt = fieldNameToSaltMap.get(key);
//                                if (NullSafe.isNonEmptyString(salt)) {
//                                    // Needs to be hashed
//                                    final String hashedVal = hashFunction.hash(val, salt);
//                                    newAttrMap.put(key, hashedVal);
//                                } else {
//                                    // Not hashed, put as was
//                                    newAttrMap.put(key, val);
//                                }
//                            });
//                        },
//                        "Hash attributeMap values");
//                return newAttrMap;
//            }
//        }
//    }


// --------------------------------------------------------------------------------


    private static class AttributeMapFunctionFactory implements ValueFunctionFactory<AttributeMap> {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AttributeMapFunctionFactory.class);

        private final FieldType fieldType;
        private final String fieldName;

        private AttributeMapFunctionFactory(final QueryField queryField) {
            Objects.requireNonNull(queryField);
            this.fieldType = queryField.getFldType();
            this.fieldName = queryField.getFldName();
        }

        @Override
        public Function<AttributeMap, Boolean> createNullCheck() {
            return attributeMap -> Objects.isNull(attributeMap.get(fieldName));
        }

        @Override
        public Function<AttributeMap, String> createStringExtractor() {
            return attributeMap -> attributeMap.get(fieldName);
        }

        @Override
        public Function<AttributeMap, Long> createDateExtractor() {
            return attributeMap -> {
                try {
                    return attributeMap.getAsEpochMillis(fieldName);
                } catch (Exception e) {
                    // attributeMap could contain any old rubbish so swallow and return null
                    LOGGER.debug(LogUtil.message("Error extracting field {} of type {} as millis: {}",
                            fieldName, fieldType, LogUtil.exceptionMessage(e), e));
                    return null;
                }
            };
        }

        @Override
        public Function<AttributeMap, Double> createNumberExtractor() {
            return attributeMap -> {
                try {
                    return NullSafe.get(
                            attributeMap.get(fieldName),
                            val -> new BigDecimal(val).doubleValue());
                } catch (final RuntimeException e) {
                    // attributeMap could contain any old rubbish so swallow and return null
                    LOGGER.debug(LogUtil.message("Error extracting field {} of type {} as double: {}",
                            fieldName, fieldType, LogUtil.exceptionMessage(e), e));
                    return null;
                }
            };
        }

        @Override
        public FieldType getFieldType() {
            return fieldType;
        }
    }
}
