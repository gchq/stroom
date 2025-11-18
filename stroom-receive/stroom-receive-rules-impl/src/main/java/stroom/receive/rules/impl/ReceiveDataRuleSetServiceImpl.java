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

package stroom.receive.rules.impl;

import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.meta.api.AttributeMapper;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Builder;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.QueryField;
import stroom.receive.common.ReceiveDataRuleSetService;
import stroom.receive.rules.shared.HashedReceiveDataRules;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.security.api.HashFunction;
import stroom.security.api.HashFunctionFactory;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;
import stroom.security.shared.HashAlgorithm;
import stroom.util.collections.CollectionUtil;
import stroom.util.collections.CollectionUtil.DuplicateMode;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Singleton
public class ReceiveDataRuleSetServiceImpl implements ReceiveDataRuleSetService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiveDataRuleSetServiceImpl.class);

    // For users in stroom that need to create/update the rules
    private static final AppPermissionSet MANAGE_RULES_PERM_SET = AppPermission.MANAGE_DATA_RECEIPT_RULES_PERMISSION
            .asAppPermissionSet();
    // For proxies/api calls that just want to get the hashed rules
    private static final AppPermissionSet FETCH_HASHED_RULES_PERM_SET = AppPermissionSet.oneOf(
            AppPermission.FETCH_HASHED_RECEIPT_POLICY_RULES,
            AppPermission.STROOM_PROXY);

    private final Provider<StroomReceiptPolicyConfig> stroomReceiptPolicyConfigProvider;
    private final WordListProvider wordListProvider;
    private final HashFunctionFactory hashFunctionFactory;
    private final ReceiveDataRuleSetStore receiveDataRuleSetStore;
    private final SecurityContext securityContext;

    @Inject
    public ReceiveDataRuleSetServiceImpl(final WordListProvider wordListProvider,
                                         final Provider<StroomReceiptPolicyConfig> stroomReceiptPolicyConfigProvider,
                                         final HashFunctionFactory hashFunctionFactory,
                                         final ReceiveDataRuleSetStore receiveDataRuleSetStore,
                                         final SecurityContext securityContext) {
        this.stroomReceiptPolicyConfigProvider = stroomReceiptPolicyConfigProvider;
        this.wordListProvider = wordListProvider;
        this.hashFunctionFactory = hashFunctionFactory;
        this.receiveDataRuleSetStore = receiveDataRuleSetStore;
        this.securityContext = securityContext;
    }

    @Override
    public ReceiveDataRules getReceiveDataRules() {
        return getReceiveDataRules(MANAGE_RULES_PERM_SET);
    }

    private ReceiveDataRules getReceiveDataRules(final AppPermissionSet requiredPerms) {
        final ReceiveDataRules receiveDataRules = securityContext.secureResult(
                Objects.requireNonNull(requiredPerms),
                () -> {
                    // The user will never have any doc perms on the DRR as it is not an explorer doc, thus
                    // access it via the proc user. Assumes it is called from a service that will
                    return securityContext.asProcessingUserResult(receiveDataRuleSetStore::getOrCreate);
                });
        LOGGER.debug("getReceiveDataRules() - receiveDataRules: {}", receiveDataRules);
        return receiveDataRules;
    }

    @Override
    public ReceiveDataRules updateReceiveDataRules(final ReceiveDataRules receiveDataRules) {

        final ReceiveDataRules receiveDataRules2 = securityContext.secureResult(
                MANAGE_RULES_PERM_SET, () -> {
                    // The user will never have any doc perms on the DRR as it is not an explorer doc, thus
                    // access it via the proc user. Assumes it is called from a service that will
                    return securityContext.asProcessingUserResult(() ->
                            receiveDataRuleSetStore.writeDocument(receiveDataRules));
                });
        LOGGER.debug("updateReceiveDataRules() - receiveDataRules2: {}", receiveDataRules2);
        return receiveDataRules2;
    }

    @Override
    public HashedReceiveDataRules getHashedReceiveDataRules() {
        final AppPermissionSet requiredPerms = FETCH_HASHED_RULES_PERM_SET;
        final HashedReceiveDataRules hashedReceiveDataRules = securityContext.secureResult(
                requiredPerms,
                () -> {
                    final ReceiveDataRules receiveDataRules = getReceiveDataRules(requiredPerms);
                    final List<ReceiveDataRule> ruleList = receiveDataRules.getRules();
                    if (NullSafe.hasItems(ruleList)) {
                        return buildHashedReceiveDataRules(receiveDataRules);
                    } else {
                        return new HashedReceiveDataRules(
                                receiveDataRules.getUpdateTimeMs(),
                                receiveDataRules,
                                Collections.emptyMap(),
                                Collections.emptyMap(),
                                null);
                    }
                });
        LOGGER.debug("getHashedReceiveDataRules() - receiveDataRules: {}", hashedReceiveDataRules);
        return hashedReceiveDataRules;
    }

    @Override
    public BundledRules getBundledRules() {
        return securityContext.secureResult(
                FETCH_HASHED_RULES_PERM_SET,
                () -> {
                    final BundledRules bundledRules = new BundledRules(
                            getReceiveDataRules(),
                            wordListProvider,
                            AttributeMapper.identity());
                    LOGGER.debug("getBundledRules() - bundledRules: {}", bundledRules);
                    return bundledRules;
                });
    }

    private HashedReceiveDataRules buildHashedReceiveDataRules(final ReceiveDataRules receiveDataRules) {
        final StroomReceiptPolicyConfig receiptPolicyConfig = stroomReceiptPolicyConfigProvider.get();
        final Set<CIKey> obfuscatedFields = CIKey.setOf(receiptPolicyConfig.getObfuscatedFields());
        final HashAlgorithm obfuscationHashAlgorithm = receiptPolicyConfig.getObfuscationHashAlgorithm();
        final HashFunction hashFunction = hashFunctionFactory.getHashFunction(obfuscationHashAlgorithm);
        final List<ReceiveDataRule> enabledRules = receiveDataRules.getEnabledRules();

        final Map<String, DictionaryDoc> uuidToFlattenedDictMap = new HashMap<>();
        final Map<String, String> fieldNameToSaltMap = new HashMap<>();
        final List<ReceiveDataRule> ruleCopies = new ArrayList<>(enabledRules.size());
        final Map<String, QueryField> fieldNameToFieldMap = CollectionUtil.mapBy(
                QueryField::getFldName, DuplicateMode.USE_FIRST, receiveDataRules.getFields());
        final Set<QueryField> usedFields = new HashSet<>(fieldNameToFieldMap.size());

        for (final ReceiveDataRule rule : enabledRules) {
            ExpressionOperator obfuscatedExpression = copyAndObfuscateOperator(
                    rule.getExpression(),
                    fieldNameToSaltMap,
                    hashFunction,
                    obfuscatedFields,
                    uuidToFlattenedDictMap);
            // If all are disabled result will be null
            if (obfuscatedExpression == null) {
                obfuscatedExpression = ExpressionOperator.builder().build();
            }

            final ReceiveDataRule obfuscatedRule = ReceiveDataRule.copy(rule)
                    .withExpression(obfuscatedExpression)
                    .build();
            ruleCopies.add(obfuscatedRule);
            final List<String> fieldsInExpression = ExpressionUtil.fields(rule.getExpression());
            fieldsInExpression.stream()
                    .map(fieldNameToFieldMap::get)
                    .filter(Objects::nonNull)
                    .forEach(usedFields::add);
        }

        // There is no point sending over the full list of fields if they are not used in the terms
        final ReceiveDataRules receiveDataRulesCopy = receiveDataRules
                .copy()
                .rules(ruleCopies)
                .fields(new ArrayList<>(usedFields))
                .build();

        return new HashedReceiveDataRules(
                receiveDataRules.getUpdateTimeMs(),
                receiveDataRulesCopy,
                uuidToFlattenedDictMap,
                fieldNameToSaltMap,
                obfuscationHashAlgorithm);
    }

    private ExpressionOperator copyAndObfuscateOperator(final ExpressionOperator expressionOperator,
                                                        final Map<String, String> fieldNameToSaltMap,
                                                        final HashFunction hashFunction,
                                                        final Set<CIKey> obfuscatedFields,
                                                        final Map<String, DictionaryDoc> uuidToFlattenedDictMap) {
        if (expressionOperator.enabled() && expressionOperator.hasChildren()) {
            final List<ExpressionItem> childrenCopy = new ArrayList<>();
            for (final ExpressionItem child : expressionOperator.getChildren()) {
                final ExpressionItem childCopy;
                if (child instanceof final ExpressionTerm childTerm) {
                    childCopy = copyAndObfuscateTerm(
                            childTerm,
                            fieldNameToSaltMap,
                            hashFunction,
                            obfuscatedFields,
                            uuidToFlattenedDictMap);
                } else if (child instanceof final ExpressionOperator childOperator) {
                    childCopy = copyAndObfuscateOperator(
                            childOperator,
                            fieldNameToSaltMap,
                            hashFunction,
                            obfuscatedFields,
                            uuidToFlattenedDictMap);
                } else {
                    throw new IllegalStateException("Unexpected type " + child.getClass());
                }
                NullSafe.consume(childCopy, childrenCopy::add);
            }

            if (childrenCopy.isEmpty()) {
                return null;
            } else {
                return expressionOperator.copy()
                        .children(childrenCopy)
                        .build();
            }
        } else {
            return null;
        }
    }

    private ExpressionTerm copyAndObfuscateTerm(final ExpressionTerm term,
                                                final Map<String, String> fieldNameToSaltMap,
                                                final HashFunction hashFunction,
                                                final Set<CIKey> obfuscatedFields,
                                                final Map<String, DictionaryDoc> flattenedDictsMap) {
        // No point copying disabled terms
        if (term.enabled()) {
            final Builder builder = term.copy();
            final CIKey fieldCIKey = CIKey.of(term.getField());
            final boolean isObfuscatedField = obfuscatedFields.contains(fieldCIKey);

            if (term.hasCondition(Condition.IN_DICTIONARY)) {
                // We don't have to change the term, just the dict that it links to
                flattenedDictsMap.computeIfAbsent(term.getDocRef().getUuid(), k ->
                        getFlattenedDictionary(
                                term.getDocRef(),
                                fieldCIKey,
                                hashFunction,
                                fieldNameToSaltMap,
                                isObfuscatedField));
                if (isObfuscatedField) {
                    builder.field(getSuffixedFieldName(term));
                }
            } else if (isObfuscatedField && term.getValue() != null) {
                // We can only obfuscate certain conditions, the rest stay in the clear
                if (term.hasCondition(Condition.IN, Condition.BETWEEN)) {
                    final String[] parts = term.getValue()
                            .split(Condition.IN_CONDITION_DELIMITER);
                    final String obfuscatedValue = Arrays.stream(parts)
                            .map(part ->
                                    hashValue(part, fieldCIKey, fieldNameToSaltMap, hashFunction))
                            .collect(Collectors.joining(Condition.IN_CONDITION_DELIMITER));
                    builder.value(obfuscatedValue);
                    builder.field(getSuffixedFieldName(term));

                } else if (term.hasCondition(
                        Condition.EQUALS_CASE_SENSITIVE,
                        Condition.NOT_EQUALS_CASE_SENSITIVE)) {

                    final String obfuscatedValue = hashValue(
                            term.getValue(),
                            fieldCIKey,
                            fieldNameToSaltMap,
                            hashFunction);
                    builder.value(obfuscatedValue);
                    builder.field(getSuffixedFieldName(term));
                }
            }
            return builder.build();
        } else {
            return null;
        }
    }

    private String getSuffixedFieldName(final ExpressionTerm term) {
        // When the expr tree is evaluated we need to distinguish between a 'Feed' term that has
        // been obfuscated and one that has not and the value extractors are only passed the field
        // name. This is not ideal, but the alternative is refactoring all the ExpressionPredicateFactory
        // code so the whole term is passed to the value extractor.
        return NullSafe.get(
                term,
                ExpressionTerm::getField,
                HashedReceiveDataRules::markFieldAsHashed);
    }

    private String hashValue(final String value,
                             final CIKey fieldName,
                             final Map<String, String> fieldNameToSaltMap,
                             final HashFunction hashFunction) {
        final String lowerFieldName = fieldName.getAsLowerCase();
        final String salt = fieldNameToSaltMap.computeIfAbsent(
                lowerFieldName,
                k -> hashFunction.generateSalt());
        return hashFunction.hash(value, salt);
    }

    private DictionaryDoc getFlattenedDictionary(final DocRef docRef,
                                                 final CIKey fieldName,
                                                 final HashFunction hashFunction,
                                                 final Map<String, String> fieldNameToSaltMap,
                                                 final boolean obfuscateContent) {
        // combinedData includes all the words from the imports
        String combinedData = wordListProvider.getCombinedData(docRef);

        if (obfuscateContent) {
            // hash each line. We have no choice but to use the same salt
            combinedData = combinedData.lines()
                    .map(line ->
                            hashDictLine(line, fieldName, fieldNameToSaltMap, hashFunction))
                    .collect(Collectors.joining("\n"));
        }

        final Long timeMs = System.currentTimeMillis();
        return new DictionaryDoc(
                docRef.getUuid(),
                docRef.getName(),
                UUID.randomUUID().toString(), // The version doesn't matter
                timeMs,
                timeMs,
                "",
                "",
                null,
                combinedData,
                Collections.emptyList());
    }

    /**
     * A dictionary line can contain parts that are separated by a space. In lucene search, parts
     * on a line are AND'd, while lines are OR'd. In ExpressionMatcher parts are OR'd. Either way
     * we need to hash each part separately.
     */
    private String hashDictLine(final String line,
                                final CIKey fieldName,
                                final Map<String, String> fieldNameToSaltMap,
                                final HashFunction hashFunction) {
        if (NullSafe.isBlankString(line)) {
            return line;
        } else {
            if (line.contains(" ")) {
                return Arrays.stream(line.split(" "))
                        .map(part ->
                                hashValue(part, fieldName, fieldNameToSaltMap, hashFunction))
                        .collect(Collectors.joining(" "));
            } else {
                return hashValue(line, fieldName, fieldNameToSaltMap, hashFunction);
            }
        }
    }

//    private Set<DocRef> getDictionaryDocRefsFromRule(final ReceiveDataRule receiveDataRule) {
//        return ExpressionUtil.terms(receiveDataRule.getExpression(), null)
//                .stream()
//                .filter(term -> term.hasCondition(Condition.IN_DICTIONARY))
//                .map(ExpressionTerm::getDocRef)
//                .collect(Collectors.toSet());
//    }
}
