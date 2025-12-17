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

package stroom.proxy.app;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.shared.AbstractDoc;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMap.Builder;
import stroom.meta.api.AttributeMapper;
import stroom.meta.api.StandardHeaderArguments;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.common.ReceiveDataRuleSetService.BundledRules;
import stroom.receive.common.WordListProviderFactory;
import stroom.receive.rules.shared.HashedReceiveDataRules;
import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.security.api.HashFunction;
import stroom.security.common.impl.HashFunctionFactoryImpl;
import stroom.security.mock.MockCommonSecurityContext;
import stroom.security.shared.HashAlgorithm;
import stroom.test.common.TemporaryPathCreator;
import stroom.util.collections.CollectionUtil;
import stroom.util.collections.CollectionUtil.DuplicateMode;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestRemoteReceiveDataRuleSetServiceImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
            TestRemoteReceiveDataRuleSetServiceImpl.class);
    private static final HashAlgorithm HASH_ALGORITHM = HashAlgorithm.SHA2_256;
    private static final String FEED_1 = "FEED_1";
    private static final String FEED_2 = "FEED_2";
    private static final String FEED_3 = "FEED_3";
    private static final String FEED_4 = "FEED_4";
    private static final String FEED_5 = "FEED_5";
    private static final String SYSTEM_1 = "SYSTEM_1";
    private static final String SYSTEM_2 = "SYSTEM_2";
    private static final String ENVIRONMENT_1 = "ENVIRONMENT_1";
    private static final QueryField FIELD_FEED = QueryField.createText(StandardHeaderArguments.FEED);
    private static final QueryField FIELD_SYSTEM = QueryField.createText(StandardHeaderArguments.SYSTEM);
    private static final QueryField FIELD_ENVIRONMENT = QueryField.createText(StandardHeaderArguments.ENVIRONMENT);

    @Mock
    private ReceiveDataRuleSetClient mockReceiveDataRuleSetClient;
    @Mock
    private ProxyReceiptPolicyConfig mockProxyReceiptPolicyConfig;
    @Mock
    private ProxyConfig mockProxyConfig;
    @Mock
    private ReceiveDataConfig mockReceiveDataConfig;

    @Test
    void test() throws IOException {

        final HashFunctionFactoryImpl hashFunctionFactory = new HashFunctionFactoryImpl();
        final WordListProviderFactory wordListProviderFactory = new WordListProviderFactory();
        final HashFunction hashFunction = hashFunctionFactory.getHashFunction(HASH_ALGORITHM);
        final String feedSalt = hashFunction.generateSalt();
        final String environmentSalt = hashFunction.generateSalt();
        final UnaryOperator<String> feedHasher = str -> hashFunction.hash(str, feedSalt);
        final UnaryOperator<String> environmentHasher = str -> hashFunction.hash(str, environmentSalt);

        final DictionaryDoc feedDict = createDict(
                "FeedDict",
                feedHasher.apply(FEED_2),
                feedHasher.apply(FEED_3),
                feedHasher.apply(FEED_4),
                feedHasher.apply(FEED_5));

        // Non-obfuscated dict
        final DictionaryDoc systemDict = createDict(
                "SystemDict",
                SYSTEM_1,
                SYSTEM_2);

        Mockito.when(mockProxyReceiptPolicyConfig.getSyncFrequency())
                .thenReturn(StroomDuration.ofMinutes(1));

        try (final TemporaryPathCreator temporaryPathCreator = new TemporaryPathCreator()) {

            Mockito.when(mockProxyConfig.getContentDir())
                    .thenReturn(ProxyConfig.DEFAULT_CONTENT_DIR);
            Mockito.when(mockReceiveDataConfig.getReceiptCheckMode())
                    .thenReturn(ReceiptCheckMode.RECEIPT_POLICY);

            int ruleNo = 0;
            final ReceiveDataRules receiveDataRules = ReceiveDataRules.builder()
                    .uuid(UUID.randomUUID().toString())
                    .addRule(ReceiveDataRule.builder()
                            .withRuleNumber(++ruleNo)
                            .withEnabled(true)
                            .withAction(ReceiveAction.REJECT)
                            .withExpression(ExpressionOperator.builder()
                                    .op(Op.OR)
                                    .addTerm(ExpressionTerm.equals(
                                            StandardHeaderArguments.FEED,
                                            feedHasher.apply(FEED_1)))
                                    .addTerm(ExpressionTerm.builder()
                                            .field(StandardHeaderArguments.FEED)
                                            .condition(Condition.IN_DICTIONARY)
                                            .docRef(feedDict.asDocRef())
                                            .build())
                                    .addTerm(ExpressionTerm.builder()
                                            .field(StandardHeaderArguments.SYSTEM)
                                            .condition(Condition.IN_DICTIONARY)
                                            .docRef(systemDict.asDocRef())
                                            .build())
                                    .build())
                            .build())
                    .addRule(ReceiveDataRule.builder()
                            .withRuleNumber(++ruleNo)
                            .withEnabled(true)
                            .withAction(ReceiveAction.DROP)
                            .withExpression(ExpressionOperator.builder()
                                    .addTerm(ExpressionTerm.equals(
                                            StandardHeaderArguments.FEED,
                                            feedHasher.apply(FEED_2)))
                                    .addTerm(ExpressionTerm.equals(
                                            StandardHeaderArguments.ENVIRONMENT,
                                            environmentHasher.apply(ENVIRONMENT_1)))
                                    .build())
                            .build())
                    .addField(FIELD_FEED)
                    .addField(FIELD_SYSTEM)
                    .addField(FIELD_ENVIRONMENT)
                    .build();

            final Map<String, DictionaryDoc> uuidToFlattenedDictMap = CollectionUtil.mapBy(
                    AbstractDoc::getUuid,
                    DuplicateMode.THROW,
                    feedDict,
                    systemDict);

            final Map<String, String> fieldNameToSaltMap = Map.of(
                    FIELD_FEED.getFldName().toLowerCase(), feedSalt,
                    FIELD_ENVIRONMENT.getFldName().toLowerCase(), environmentSalt);

            Mockito.when(mockReceiveDataRuleSetClient.getHashedReceiveDataRules())
                    .thenReturn(Optional.of(
                            new HashedReceiveDataRules(
                                    receiveDataRules,
                                    uuidToFlattenedDictMap,
                                    fieldNameToSaltMap,
                                    HASH_ALGORITHM)));

            final Path jsonFile = temporaryPathCreator.getHomeDir()
                    .resolve(ProxyConfig.DEFAULT_CONTENT_DIR)
                    .resolve(RemoteReceiveDataRuleSetServiceImpl.FILE_NAME);

            assertThat(jsonFile)
                    .doesNotExist();

            final RemoteReceiveDataRuleSetServiceImpl service = new RemoteReceiveDataRuleSetServiceImpl(
                    mockReceiveDataRuleSetClient,
                    MockCommonSecurityContext::new,
                    () -> mockProxyReceiptPolicyConfig,
                    () -> mockProxyConfig,
                    () -> mockReceiveDataConfig,
                    temporaryPathCreator,
                    hashFunctionFactory,
                    wordListProviderFactory);

            final BundledRules bundledRules1 = service.getBundledRules();
            assertThat(bundledRules1)
                    .isNotNull();
            // Make sure the caching is working OK
            final BundledRules bundledRules2 = service.getBundledRules();
            assertThat(bundledRules2)
                    .isSameAs(bundledRules1);

            final AttributeMap attrMap1 = createAttrMap(
                    FEED_1, SYSTEM_1, ENVIRONMENT_1, 123, Instant.now());

            final AttributeMapper attributeMapper = bundledRules1.attributeMapper();

            final AttributeMap attrMap2 = attributeMapper.mapAttributes(attrMap1);
            assertThat(attrMap2)
                    .isNotEqualTo(attrMap1);
            // Make sure the attrs we expect to be hashed are hashed and have been hashed
            // with the correct salt
            assertThat(attrMap2.get(HashedReceiveDataRules.markFieldAsHashed(StandardHeaderArguments.FEED)))
                    .isEqualTo(feedHasher.apply(attrMap1.get(StandardHeaderArguments.FEED)));
            assertThat(attrMap2.get(StandardHeaderArguments.FEED))
                    .isEqualTo(attrMap1.get(StandardHeaderArguments.FEED));
            assertThat(attrMap2.get(StandardHeaderArguments.SYSTEM))
                    .isEqualTo(attrMap1.get(StandardHeaderArguments.SYSTEM));
            assertThat(attrMap2.get(StandardHeaderArguments.ENVIRONMENT))
                    .isEqualTo(attrMap1.get(StandardHeaderArguments.ENVIRONMENT));
            assertThat(attrMap2.get(HashedReceiveDataRules.markFieldAsHashed(StandardHeaderArguments.ENVIRONMENT)))
                    .isEqualTo(environmentHasher.apply(attrMap1.get(StandardHeaderArguments.ENVIRONMENT)));

            assertThat(bundledRules1.wordListProvider().getWords(feedDict.asDocRef()))
                    .containsExactly(
                            feedHasher.apply(FEED_2),
                            feedHasher.apply(FEED_3),
                            feedHasher.apply(FEED_4),
                            feedHasher.apply(FEED_5));

            assertThat(bundledRules1.wordListProvider().getWords(systemDict.asDocRef()))
                    .containsExactly(SYSTEM_1, SYSTEM_2);

            assertThat(jsonFile)
                    .exists()
                    .isRegularFile();

            assertThat(bundledRules1.receiveDataRules())
                    .isEqualTo(receiveDataRules);

            final String json = Files.readString(jsonFile);
            LOGGER.debug("json:\n{}", json);
        }
    }

    @Test
    void testFileReading() throws IOException {

        final HashFunctionFactoryImpl hashFunctionFactory = new HashFunctionFactoryImpl();
        final WordListProviderFactory wordListProviderFactory = new WordListProviderFactory();

        Mockito.when(mockProxyReceiptPolicyConfig.getSyncFrequency())
                .thenReturn(StroomDuration.ofSeconds(1));

        try (final TemporaryPathCreator temporaryPathCreator = new TemporaryPathCreator()) {
            Mockito.when(mockProxyConfig.getContentDir())
                    .thenReturn(ProxyConfig.DEFAULT_CONTENT_DIR);
            Mockito.when(mockReceiveDataConfig.getReceiptCheckMode())
                    .thenReturn(ReceiptCheckMode.RECEIPT_POLICY);

            int ruleNo = 0;
            final ReceiveDataRules receiveDataRules = ReceiveDataRules.builder()
                    .uuid(UUID.randomUUID().toString())
                    .addRule(ReceiveDataRule.builder()
                            .withRuleNumber(++ruleNo)
                            .withEnabled(true)
                            .withAction(ReceiveAction.REJECT)
                            .withExpression(ExpressionOperator.builder()
                                    .op(Op.OR)
                                    .addTerm(ExpressionTerm.equals(StandardHeaderArguments.FEED, FEED_1))
                                    .build())
                            .build())
                    .addField(FIELD_FEED)
                    .build();

            final Map<String, DictionaryDoc> uuidToFlattenedDictMap = Collections.emptyMap();

            final Map<String, String> fieldNameToSaltMap = Collections.emptyMap();

            Mockito.when(mockReceiveDataRuleSetClient.getHashedReceiveDataRules())
                    .thenReturn(Optional.of(
                            new HashedReceiveDataRules(
                                    receiveDataRules,
                                    uuidToFlattenedDictMap,
                                    fieldNameToSaltMap,
                                    HASH_ALGORITHM)));

            final Path jsonFile = temporaryPathCreator.getHomeDir()
                    .resolve(ProxyConfig.DEFAULT_CONTENT_DIR)
                    .resolve(RemoteReceiveDataRuleSetServiceImpl.FILE_NAME);

            assertThat(jsonFile)
                    .doesNotExist();

            final RemoteReceiveDataRuleSetServiceImpl service = new RemoteReceiveDataRuleSetServiceImpl(
                    mockReceiveDataRuleSetClient,
                    MockCommonSecurityContext::new,
                    () -> mockProxyReceiptPolicyConfig,
                    () -> mockProxyConfig,
                    () -> mockReceiveDataConfig,
                    temporaryPathCreator,
                    hashFunctionFactory,
                    wordListProviderFactory);

            final BundledRules bundledRules1 = service.getBundledRules();
            assertThat(bundledRules1)
                    .isNotNull();
            // Make sure the caching is working OK
            final BundledRules bundledRules2 = service.getBundledRules();
            assertThat(bundledRules2)
                    .isSameAs(bundledRules1);

            assertThat(jsonFile)
                    .exists()
                    .isRegularFile();
            final String json = Files.readString(jsonFile);
            LOGGER.debug("json:\n{}", json);

            // Simulate an outage of the remote, so it should be able to read from the file
            Mockito.when(mockReceiveDataRuleSetClient.getHashedReceiveDataRules())
                    .thenReturn(Optional.empty());

            // Wait for the cached bundle to age off
            sleep(2_000);

            service.getBundledRules();

            sleep(100);

            final BundledRules bundledRules3 = service.getBundledRules();
            assertThat(bundledRules3.receiveDataRules())
                    .isSameAs(bundledRules1.receiveDataRules());
        }
    }

    private void sleep(final long ms) {
        LOGGER.debug("Sleeping for {}ms", ms);
        ThreadUtil.sleepIgnoringInterrupts(ms);
    }

    private DictionaryDoc createDict(final String name,
                                     final String... lines) {
        return DictionaryDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name(name)
                .data(String.join("\n", lines))
                .build();
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
}
