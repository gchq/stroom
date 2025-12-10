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

import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMap.Builder;
import stroom.meta.api.AttributeMapper;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.ExpressionPredicateFactoryFactory;
import stroom.receive.common.ReceiveDataRuleSetService.BundledRules;
import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.util.collections.CollectionUtil;
import stroom.util.collections.CollectionUtil.DuplicateMode;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestDataReceiptPolicyAttributeMapFilterFactoryImpl {

    private static final String FEED_1 = "FEED_1";
    private static final String FEED_2 = "FEED_2";
    private static final String FEED_3 = "FEED_3";
    private static final String FEED_4 = "FEED_4";
    private static final String FEED_5 = "FEED_5";
    private static final String SYSTEM_1 = "SYSTEM_1";
    private static final String SYSTEM_2 = "SYSTEM_2";
    private static final String ENVIRONMENT_1 = "ENVIRONMENT_1";
    private static final String ENVIRONMENT_2 = "ENVIRONMENT_2";
    private static final AttributeMap ATTRIBUTE_MAP = AttributeMap.builder()
            .put(StandardHeaderArguments.FEED, FEED_1)
            .put(StandardHeaderArguments.SYSTEM, SYSTEM_1)
            .put(StandardHeaderArguments.ENVIRONMENT, ENVIRONMENT_1)
            .build();

    private static final QueryField FIELD_FEED = QueryField.createText(StandardHeaderArguments.FEED);
    private static final QueryField FIELD_SYSTEM = QueryField.createText(StandardHeaderArguments.SYSTEM);
    private static final QueryField FIELD_ENVIRONMENT = QueryField.createText(StandardHeaderArguments.ENVIRONMENT);
    private static final QueryField FIELD_RECEIVED_TIME = QueryField.createDate(StandardHeaderArguments.RECEIVED_TIME);
    private static final QueryField FIELD_CONTENT_LENGTH = QueryField.createDouble(
            StandardHeaderArguments.CONTENT_LENGTH);

    @Mock
    private ReceiveDataRuleSetService mockReceiveDataRuleSetService;
    @Mock
    private ReceiveDataConfig mockReceiveDataConfig;
    @Mock
    private ReceiveActionMetricsRecorder mockReceiveActionMetricsRecorder;

    @Test
    void test_noRules() {
        final DataReceiptPolicyAttributeMapFilterFactoryImpl factory =
                new DataReceiptPolicyAttributeMapFilterFactoryImpl(
                        mockReceiveDataRuleSetService,
                        new ExpressionPredicateFactoryFactory(),
                        () -> mockReceiveDataConfig,
                        () -> mockReceiveActionMetricsRecorder);

        final ReceiveDataRules receiveDataRules = ReceiveDataRules.builder()
                .uuid(UUID.randomUUID().toString())
                .build();

        final Map<String, DictionaryDoc> uuidToDictMap = Map.of();

        final WordListProvider wordListProvider = new WordListProviderFactory().create(uuidToDictMap);

        Mockito.when(mockReceiveDataRuleSetService.getBundledRules())
                .thenReturn(new BundledRules(
                        receiveDataRules,
                        wordListProvider,
                        AttributeMapper.identity()));

        assertRejected(() ->
                factory.create()
                        .filter(ATTRIBUTE_MAP));
    }

    @Test
    void test_noActiveExpression() {
        final DataReceiptPolicyAttributeMapFilterFactoryImpl factory =
                new DataReceiptPolicyAttributeMapFilterFactoryImpl(
                        mockReceiveDataRuleSetService,
                        new ExpressionPredicateFactoryFactory(),
                        () -> mockReceiveDataConfig,
                        () -> mockReceiveActionMetricsRecorder);

        final ReceiveDataRules receiveDataRules = ReceiveDataRules.builder()
                .uuid(UUID.randomUUID().toString())
                .addRule(ReceiveDataRule.builder()
                        .withRuleNumber(1)
                        .withExpression(ExpressionOperator.builder()
                                .build())
                        .withAction(ReceiveAction.RECEIVE)
                        .build())
                .build();

        final Map<String, DictionaryDoc> uuidToDictMap = CollectionUtil.mapBy(
                DictionaryDoc::getUuid, DuplicateMode.THROW, List.of(
                ));

        final WordListProvider wordListProvider = new WordListProviderFactory().create(uuidToDictMap);

        Mockito.when(mockReceiveDataRuleSetService.getBundledRules())
                .thenReturn(new BundledRules(
                        receiveDataRules,
                        wordListProvider,
                        AttributeMapper.identity()));
//        setReceiptCheckModeInMockConfig();

        // No active expression so fallback is reject all
        assertRejected(() -> {
            factory.create()
                    .filter(ATTRIBUTE_MAP);
        });

        assertRejected(() -> {
            factory.create()
                    .filter(createAttrMap(null, null, null));
        });
    }

    @Test
    void test_oneRule_Reject() {
        final DataReceiptPolicyAttributeMapFilterFactoryImpl factory =
                new DataReceiptPolicyAttributeMapFilterFactoryImpl(
                        mockReceiveDataRuleSetService,
                        new ExpressionPredicateFactoryFactory(),
                        () -> mockReceiveDataConfig,
                        () -> mockReceiveActionMetricsRecorder);


        final ReceiveDataRules receiveDataRules = ReceiveDataRules.builder()
                .uuid(UUID.randomUUID().toString())
                .addRule(ReceiveDataRule.builder()
                        .withRuleNumber(1)
                        .withAction(ReceiveAction.REJECT)
                        .withEnabled(true)
                        .withExpression(ExpressionOperator.builder()
                                .op(Op.OR)
                                .addTerm(ExpressionTerm.builder()
                                        .field(StandardHeaderArguments.FEED)
                                        .condition(Condition.EQUALS)
                                        .value(FEED_1)
                                        .build())
                                .addTerm(ExpressionTerm.builder()
                                        .field(StandardHeaderArguments.FEED)
                                        .condition(Condition.EQUALS)
                                        .value(FEED_2)
                                        .build())
                                .build())
                        .build())
                .addField(FIELD_FEED)
                .build();

        final Map<String, DictionaryDoc> uuidToDictMap = CollectionUtil.mapBy(
                DictionaryDoc::getUuid, DuplicateMode.THROW, List.of(
                ));

        final WordListProvider wordListProvider = new WordListProviderFactory().create(uuidToDictMap);

        Mockito.when(mockReceiveDataRuleSetService.getBundledRules())
                .thenReturn(new BundledRules(
                        receiveDataRules,
                        wordListProvider,
                        AttributeMapper.identity()));

        final AttributeMapFilter filter = factory.create();

        assertRejected(() ->
                filter.filter(ATTRIBUTE_MAP));
    }

    @Test
    void test_oneRule_Drop() {
        final DataReceiptPolicyAttributeMapFilterFactoryImpl factory =
                new DataReceiptPolicyAttributeMapFilterFactoryImpl(
                        mockReceiveDataRuleSetService,
                        new ExpressionPredicateFactoryFactory(),
                        () -> mockReceiveDataConfig,
                        () -> mockReceiveActionMetricsRecorder);

        final ReceiveDataRules receiveDataRules = ReceiveDataRules.builder()
                .uuid(UUID.randomUUID().toString())
                .addRule(ReceiveDataRule.builder()
                        .withRuleNumber(1)
                        .withAction(ReceiveAction.DROP)
                        .withEnabled(true)
                        .withExpression(ExpressionOperator.builder()
                                .op(Op.OR)
                                .addTerm(ExpressionTerm.builder()
                                        .field(StandardHeaderArguments.FEED)
                                        .condition(Condition.EQUALS)
                                        .value(FEED_1)
                                        .build())
                                .addTerm(ExpressionTerm.builder()
                                        .field(StandardHeaderArguments.FEED)
                                        .condition(Condition.EQUALS)
                                        .value(FEED_2)
                                        .build())
                                .build())
                        .build())
                .addField(FIELD_FEED)
                .build();

        final Map<String, DictionaryDoc> uuidToDictMap = CollectionUtil.mapBy(
                DictionaryDoc::getUuid, DuplicateMode.THROW, List.of(
                ));

        final WordListProvider wordListProvider = new WordListProviderFactory().create(uuidToDictMap);

        Mockito.when(mockReceiveDataRuleSetService.getBundledRules())
                .thenReturn(new BundledRules(
                        receiveDataRules,
                        wordListProvider,
                        AttributeMapper.identity()));
//        setReceiptCheckModeInMockConfig();

        final boolean result = factory.create()
                .filter(ATTRIBUTE_MAP);

        assertThat(result)
                .isFalse();
    }

    @Test
    void test_multipleRules() {
        final DataReceiptPolicyAttributeMapFilterFactoryImpl factory =
                new DataReceiptPolicyAttributeMapFilterFactoryImpl(
                        mockReceiveDataRuleSetService,
                        new ExpressionPredicateFactoryFactory(),
                        () -> mockReceiveDataConfig,
                        () -> mockReceiveActionMetricsRecorder);

        int ruleNo = 0;
        final ReceiveDataRules receiveDataRules = ReceiveDataRules.builder()
                .uuid(UUID.randomUUID().toString())
                .addRule(ReceiveDataRule.builder()
                        .withRuleNumber(++ruleNo)
                        .withAction(ReceiveAction.RECEIVE)
                        .withEnabled(true)
                        .withExpression(ExpressionUtil.equals(StandardHeaderArguments.SYSTEM, SYSTEM_1))
                        .build())
                .addRule(ReceiveDataRule.builder()
                        .withRuleNumber(++ruleNo)
                        .withAction(ReceiveAction.REJECT)
                        .withEnabled(true)
                        .withExpression(ExpressionOperator.builder()
                                .op(Op.OR)
                                .addTerm(ExpressionTerm.equals(StandardHeaderArguments.FEED, FEED_1))
                                .addTerm(ExpressionTerm.equals(StandardHeaderArguments.FEED, FEED_2))
                                .build())
                        .build())
                .addRule(ReceiveDataRule.builder()
                        .withRuleNumber(++ruleNo)
                        .withAction(ReceiveAction.DROP)
                        .withEnabled(true)
                        .withExpression(ExpressionOperator.builder()
                                .addTerm(ExpressionTerm.equals(StandardHeaderArguments.SYSTEM, SYSTEM_2))
                                .addTerm(ExpressionTerm.equals(StandardHeaderArguments.ENVIRONMENT, ENVIRONMENT_2))
                                .build())
                        .build())
                .addField(FIELD_FEED)
                .addField(FIELD_SYSTEM)
                .addField(FIELD_ENVIRONMENT)
                .build();

        final Map<String, DictionaryDoc> uuidToDictMap = CollectionUtil.mapBy(
                DictionaryDoc::getUuid, DuplicateMode.THROW, List.of(
                ));

        final WordListProvider wordListProvider = new WordListProviderFactory().create(uuidToDictMap);

        Mockito.when(mockReceiveDataRuleSetService.getBundledRules())
                .thenReturn(new BundledRules(
                        receiveDataRules,
                        wordListProvider,
                        AttributeMapper.identity()));
//        setReceiptCheckModeInMockConfig();

        final AttributeMapFilter filter = factory.create();
        // RECEIVE by rule 1
        boolean result = filter.filter(createAttrMap(FEED_1, SYSTEM_1, ENVIRONMENT_1));
        assertThat(result)
                .isTrue();

        // REJECT by rule 2
        assertRejected(() ->
                filter.filter(createAttrMap(FEED_1, SYSTEM_2, ENVIRONMENT_1)));

        // DROP by rule 3
        result = filter.filter(createAttrMap(FEED_3, SYSTEM_2, ENVIRONMENT_2));
        assertThat(result)
                .isFalse();

        // No match on any rule, so REJECT
        assertRejected(() -> {
            filter.filter(createAttrMap(null, null, null));
        });
    }

    @Test
    void test_numericValue() {
        final DataReceiptPolicyAttributeMapFilterFactoryImpl factory =
                new DataReceiptPolicyAttributeMapFilterFactoryImpl(
                        mockReceiveDataRuleSetService,
                        new ExpressionPredicateFactoryFactory(),
                        () -> mockReceiveDataConfig,
                        () -> mockReceiveActionMetricsRecorder);

        final ReceiveDataRules receiveDataRules = ReceiveDataRules.builder()
                .uuid(UUID.randomUUID().toString())
                .addRule(ReceiveDataRule.builder()
                        .withRuleNumber(1)
                        .withAction(ReceiveAction.DROP)
                        .withEnabled(true)
                        .withExpression(ExpressionOperator.builder()
                                .addTerm(ExpressionTerm.builder()
                                        .field(StandardHeaderArguments.CONTENT_LENGTH)
                                        .condition(Condition.GREATER_THAN)
                                        .value("100")
                                        .build())
                                .build())
                        .build())
                .addRule(createReceiveAllRule())
                .addField(FIELD_CONTENT_LENGTH)
                .build();

        final Map<String, DictionaryDoc> uuidToDictMap = CollectionUtil.mapBy(
                DictionaryDoc::getUuid, DuplicateMode.THROW, List.of(
                ));

        final WordListProvider wordListProvider = new WordListProviderFactory().create(uuidToDictMap);

        Mockito.when(mockReceiveDataRuleSetService.getBundledRules())
                .thenReturn(new BundledRules(
                        receiveDataRules,
                        wordListProvider,
                        AttributeMapper.identity()));
//        setReceiptCheckModeInMockConfig();

        boolean result = factory.create()
                .filter(createAttrMap(FEED_1, SYSTEM_1, ENVIRONMENT_1, 50, null));

        // Matches on the catch-all 2nd rule
        assertThat(result)
                .isTrue();

        result = factory.create()
                .filter(createAttrMap(FEED_1, SYSTEM_1, ENVIRONMENT_1, 150, null));

        // 1st rule match
        assertThat(result)
                .isFalse();
    }

    @Test
    void test_dateValue() {
        final DataReceiptPolicyAttributeMapFilterFactoryImpl factory =
                new DataReceiptPolicyAttributeMapFilterFactoryImpl(
                        mockReceiveDataRuleSetService,
                        new ExpressionPredicateFactoryFactory(),
                        () -> mockReceiveDataConfig,
                        () -> mockReceiveActionMetricsRecorder);

        final ReceiveDataRules receiveDataRules = ReceiveDataRules.builder()
                .uuid(UUID.randomUUID().toString())
                .addRule(ReceiveDataRule.builder()
                        .withRuleNumber(1)
                        .withAction(ReceiveAction.DROP)
                        .withEnabled(true)
                        .withExpression(ExpressionOperator.builder()
                                .addTerm(ExpressionTerm.builder()
                                        .field(StandardHeaderArguments.RECEIVED_TIME)
                                        .condition(Condition.GREATER_THAN)
                                        .value("now()")
                                        .build())
                                .build())
                        .build())
                .addField(FIELD_RECEIVED_TIME)
                .build();

        final Map<String, DictionaryDoc> uuidToDictMap = CollectionUtil.mapBy(
                DictionaryDoc::getUuid, DuplicateMode.THROW, List.of(
                ));

        final WordListProvider wordListProvider = new WordListProviderFactory().create(uuidToDictMap);

        Mockito.when(mockReceiveDataRuleSetService.getBundledRules())
                .thenReturn(new BundledRules(
                        receiveDataRules,
                        wordListProvider,
                        AttributeMapper.identity()));

        final Instant now = Instant.now();

        // No rule match
        assertRejected(() -> {
            factory.create()
                    .filter(createAttrMap(
                            FEED_1,
                            SYSTEM_1,
                            ENVIRONMENT_1,
                            null,
                            now.minus(1, ChronoUnit.DAYS)));
        });

        final boolean result = factory.create()
                .filter(createAttrMap(
                        FEED_1,
                        SYSTEM_1,
                        ENVIRONMENT_1,
                        null,
                        now.plus(1, ChronoUnit.DAYS)));

        // rule match
        assertThat(result)
                .isFalse();
    }

    @Test
    void test_dict() {
        final DataReceiptPolicyAttributeMapFilterFactoryImpl factory =
                new DataReceiptPolicyAttributeMapFilterFactoryImpl(
                        mockReceiveDataRuleSetService,
                        new ExpressionPredicateFactoryFactory(),
                        () -> mockReceiveDataConfig,
                        () -> mockReceiveActionMetricsRecorder);

        final DictionaryDoc feedDict = createDict(
                "FeedDict",
                FEED_2,
                FEED_3,
                FEED_4,
                FEED_5);

        final ReceiveDataRules receiveDataRules = ReceiveDataRules.builder()
                .uuid(UUID.randomUUID().toString())
                .addRule(ReceiveDataRule.builder()
                        .withRuleNumber(1)
                        .withAction(ReceiveAction.DROP)
                        .withEnabled(true)
                        .withExpression(ExpressionOperator.builder()
                                .addTerm(ExpressionTerm.builder()
                                        .field(StandardHeaderArguments.FEED)
                                        .condition(Condition.IN_DICTIONARY)
                                        .docRef(feedDict.asDocRef())
                                        .build())
                                .addTerm(ExpressionTerm.equals(StandardHeaderArguments.SYSTEM, SYSTEM_1))
                                .build())
                        .build())
                .addRule(createReceiveAllRule())
                .addField(FIELD_FEED)
                .addField(FIELD_SYSTEM)
                .build();

        final Map<String, DictionaryDoc> uuidToDictMap = CollectionUtil.mapBy(
                DictionaryDoc::getUuid, DuplicateMode.THROW, List.of(feedDict));

        final WordListProvider wordListProvider = new WordListProviderFactory().create(uuidToDictMap);

        Mockito.when(mockReceiveDataRuleSetService.getBundledRules())
                .thenReturn(new BundledRules(
                        receiveDataRules,
                        wordListProvider,
                        AttributeMapper.identity()));
//        setReceiptCheckModeInMockConfig();

        // Feed1 not in dict, so receive from 2nd catch-all rule
        assertThat(factory.create().filter(
                createAttrMap(FEED_1, SYSTEM_1, ENVIRONMENT_1, 50, null)))
                .isTrue();

        // Feed2 in dict, so drop
        assertThat(factory.create().filter(
                createAttrMap(FEED_2, SYSTEM_1, ENVIRONMENT_1, 50, null)))
                .isFalse();

        // Feed2 in dict, but system2 is not match, so receive from 2nd catch-all rule
        assertThat(factory.create().filter(
                createAttrMap(FEED_2, SYSTEM_2, ENVIRONMENT_1, 50, null)))
                .isTrue();
    }

    private void setReceiptCheckModeInMockConfig() {
        Mockito.when(mockReceiveDataConfig.getReceiptCheckMode())
                .thenReturn(ReceiptCheckMode.RECEIPT_POLICY);
    }

    private void assertRejected(final ThrowingCallable throwingCallable) {
        Assertions.assertThatThrownBy(throwingCallable)
                .isInstanceOf(StroomStreamException.class)
                .extracting(throwable ->
                        ((StroomStreamException) throwable).getStroomStreamStatus()
                                .getStroomStatusCode())
                .isEqualTo(StroomStatusCode.REJECTED_BY_POLICY_RULES);
    }

    private AttributeMap createAttrMap(final String feed,
                                       final String system,
                                       final String environment) {
        return createAttrMap(feed, system, environment, null, null);
    }

    private AttributeMap createAttrMap(final String feed,
                                       final String system,
                                       final String environment,
                                       final Integer contentLength,
                                       final Instant receiveTime) {
        final Builder builder = AttributeMap.builder();
        if (feed != null) {
            builder.put(StandardHeaderArguments.FEED, feed);
        }
        if (system != null) {
            builder.put(StandardHeaderArguments.SYSTEM, system);
        }
        if (environment != null) {
            builder.put(StandardHeaderArguments.ENVIRONMENT, environment);
        }
        if (contentLength != null) {
            builder.put(StandardHeaderArguments.CONTENT_LENGTH, String.valueOf(contentLength));
        }
        if (receiveTime != null) {
            builder.putDateTime(StandardHeaderArguments.RECEIVED_TIME, receiveTime);
        }
        return builder.build();
    }

    private DictionaryDoc createDict(final String name,
                                     final String... lines) {
        return DictionaryDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name(name)
                .data(String.join("\n", lines))
                .build();
    }

    private ReceiveDataRule createReceiveAllRule() {
        return ReceiveDataRule.builder()
                .withEnabled(true)
                .withRuleNumber(999)
                .withExpression(null)
                .withAction(ReceiveAction.RECEIVE)
                .withName("Receive ALL")
                .build();
    }
}
