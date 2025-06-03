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

import stroom.datasource.api.v2.QueryField;
import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.meta.api.AttributeMapper;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Builder;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.ExpressionUtil;
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
import stroom.util.logging.LogUtil;
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

    private static final AppPermissionSet REQUIRED_PERMISSION_SET = AppPermissionSet.oneOf(
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
        final ReceiveDataRules receiveDataRules = securityContext.secureResult(
                REQUIRED_PERMISSION_SET,
                receiveDataRuleSetStore::getOrCreate);
        LOGGER.debug("getReceiveDataRules() - receiveDataRules: {}", receiveDataRules);
        return receiveDataRules;
    }

    @Override
    public ReceiveDataRules updateReceiveDataRules(final ReceiveDataRules receiveDataRules) {

        final ReceiveDataRules receiveDataRules2 = securityContext.secureResult(
                REQUIRED_PERMISSION_SET,
                () -> receiveDataRuleSetStore.writeDocument(receiveDataRules));
        LOGGER.debug("updateReceiveDataRules() - receiveDataRules2: {}", receiveDataRules2);
        return receiveDataRules2;
    }

    @Override
    public HashedReceiveDataRules getHashedReceiveDataRules() {
        final HashedReceiveDataRules hashedReceiveDataRules = securityContext.secureResult(
                REQUIRED_PERMISSION_SET,
                () -> {
                    final ReceiveDataRules receiveDataRules = receiveDataRuleSetStore.getOrCreate();
                    final List<ReceiveDataRule> rules = receiveDataRules.getRules();
                    if (NullSafe.hasItems(rules)) {
                        return buildHashedReceiveDataRules(receiveDataRules);
                    } else {
                        return new HashedReceiveDataRules(
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
        final BundledRules bundledRules = new BundledRules(
                getReceiveDataRules(),
                wordListProvider,
                AttributeMapper.identity());
        LOGGER.debug("getBundledRules() - bundledRules: {}", bundledRules);
        return bundledRules;
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
        final ReceiveDataRules receiveDataRulesCopy = ReceiveDataRules.copy(receiveDataRules)
                .withRules(ruleCopies)
                .withFields(new ArrayList<>(usedFields))
                .build();

        return new HashedReceiveDataRules(
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
                final ExpressionItem copy;
                if (child instanceof ExpressionTerm childTerm) {
                    copy = copyAndObfuscateTerm(childTerm,
                            fieldNameToSaltMap,
                            hashFunction,
                            obfuscatedFields,
                            uuidToFlattenedDictMap);
                } else if (child instanceof ExpressionOperator childOperator) {
                    copy = copyAndObfuscateOperator(childOperator,
                            fieldNameToSaltMap,
                            hashFunction,
                            obfuscatedFields,
                            uuidToFlattenedDictMap);
                } else {
                    throw new IllegalStateException("Unexpected type " + child.getClass());
                }
                if (copy != null) {
                    childrenCopy.add(copy);
                }
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
        if (term.enabled()) {
            final Builder builder = term.copy();
            final CIKey fieldCIKey = CIKey.of(term.getField());
            if (term.hasCondition(Condition.IN_DICTIONARY)) {
                // We don't have to change the term, just the dict that it links to
                final boolean isObfuscatedField = obfuscatedFields.contains(fieldCIKey);
                flattenedDictsMap.computeIfAbsent(term.getDocRef().getUuid(), k ->
                        getFlattenedDictionary(
                                term.getDocRef(),
                                fieldCIKey,
                                hashFunction,
                                fieldNameToSaltMap,
                                isObfuscatedField));
            } else if (obfuscatedFields.contains(fieldCIKey) && term.getValue() != null) {
                if (term.hasCondition(Condition.IN)) {
                    final String[] parts = term.getValue()
                            .split(",");
                    final String obfuscatedValue = Arrays.stream(parts)
                            .map(part ->
                                    hashValue(part, fieldCIKey, fieldNameToSaltMap, hashFunction))
                            .collect(Collectors.joining(","));
                    builder.value(obfuscatedValue);
                } else if (term.hasCondition(Condition.EQUALS, Condition.NOT_EQUALS)) {
                    final String obfuscatedValue = hashValue(
                            term.getValue(),
                            fieldCIKey,
                            fieldNameToSaltMap,
                            hashFunction);
                    builder.value(obfuscatedValue);
                } else {
                    throw new IllegalStateException(LogUtil.message(
                            "Condition {} is not supported with obfuscated fields", term.getCondition()));
                }
            }
            return builder.build();
        } else {
            return null;
        }
    }

    private String hashValue(final String value,
                             final CIKey fieldName,
                             final Map<String, String> fieldNameToSaltMap,
                             final HashFunction hashFunction) {
        final String salt = fieldNameToSaltMap.computeIfAbsent(
                fieldName.getAsLowerCase(),
                k -> hashFunction.generateSalt());
        return hashFunction.hash(value, salt);
    }

    private DictionaryDoc getFlattenedDictionary(final DocRef docRef,
                                                 final CIKey fieldName,
                                                 final HashFunction hashFunction,
                                                 final Map<String, String> fieldNameToSaltMap,
                                                 boolean obfuscateContent) {
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
                DictionaryDoc.TYPE,
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
