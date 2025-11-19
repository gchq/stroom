package stroom.receive.rules.impl;

import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMap.Builder;
import stroom.meta.api.StandardHeaderArguments;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.receive.rules.shared.HashedReceiveDataRules;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.security.api.HashFunction;
import stroom.security.api.HashFunctionFactory;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.security.shared.HashAlgorithm;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.Base58;
import stroom.util.string.StringUtil;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestReceiveDataRuleSetServiceImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestReceiveDataRuleSetServiceImpl.class);

    // Not actually bcrypt, but the test doesn't care
    private static final HashAlgorithm HASH_ALGORITHM = HashAlgorithm.BCRYPT;
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

    private final SecurityContext allowAllSecurityContext = MockSecurityContext.getInstance();

    @Mock
    private WordListProvider mockWordListProvider;
    @Mock
    private HashFunctionFactory mockHashFunctionFactory;
    @Mock
    private ReceiveDataRuleSetStore mockReceiveDataRuleSetStore;
    @Mock
    private StroomReceiptPolicyConfig receiptPolicyConfig;

    @Test
    void getHashedReceiveDataRules_empty() {

        final ReceiveDataRuleSetServiceImpl service = new ReceiveDataRuleSetServiceImpl(
                mockWordListProvider,
                () -> receiptPolicyConfig,
                mockHashFunctionFactory,
                mockReceiveDataRuleSetStore,
                allowAllSecurityContext);

        Mockito.when(mockReceiveDataRuleSetStore.getOrCreate())
                .thenReturn(ReceiveDataRules.builder()
                        .uuid(UUID.randomUUID().toString())
                        .updateTimeMs(Instant.now().toEpochMilli())
                        .build());

        final HashedReceiveDataRules hashedReceiveDataRules = service.getHashedReceiveDataRules();

        assertThat(hashedReceiveDataRules.getRules())
                .isEmpty();
        assertThat(hashedReceiveDataRules.getFields())
                .isEmpty();
        assertThat(hashedReceiveDataRules.getHashAlgorithm())
                .isEqualTo(null);
        assertThat(hashedReceiveDataRules.getFieldNameToSaltMap())
                .isEmpty();
    }

    @Test
    void getHashedReceiveDataRules_twoRules() {

        final ReceiveDataRuleSetServiceImpl service = new ReceiveDataRuleSetServiceImpl(
                mockWordListProvider,
                () -> receiptPolicyConfig,
                mockHashFunctionFactory,
                mockReceiveDataRuleSetStore,
                allowAllSecurityContext);

        final Md5Hasher md5Hasher = new Md5Hasher();

        Mockito.when(receiptPolicyConfig.getObfuscationHashAlgorithm())
                .thenReturn(HASH_ALGORITHM);
        Mockito.when(receiptPolicyConfig.getObfuscatedFields())
                .thenReturn(Set.of(
                        StandardHeaderArguments.FEED,
                        StandardHeaderArguments.ENVIRONMENT));
        Mockito.when(mockHashFunctionFactory.getHashFunction(Mockito.any()))
                .thenReturn(md5Hasher);
        int ruleNo = 0;
        Mockito.when(mockReceiveDataRuleSetStore.getOrCreate())
                .thenReturn(ReceiveDataRules.builder()
                        .uuid(UUID.randomUUID().toString())
                        .updateTimeMs(Instant.now().toEpochMilli())
                        .addRule(ReceiveDataRule.builder()
                                .withRuleNumber(++ruleNo)
                                .withEnabled(true)
                                .withAction(ReceiveAction.REJECT)
                                .withExpression(ExpressionOperator.builder()
                                        .addTerm(ExpressionTerm.equalsCaseSensitive(
                                                StandardHeaderArguments.FEED, FEED_1))
                                        .addTerm(ExpressionTerm.equals(StandardHeaderArguments.SYSTEM, SYSTEM_1))
                                        .build())
                                .build())
                        .addRule(ReceiveDataRule.builder()
                                .withRuleNumber(++ruleNo)
                                .withEnabled(true)
                                .withAction(ReceiveAction.DROP)
                                .withExpression(ExpressionOperator.builder()
                                        .addTerm(ExpressionTerm.equalsCaseSensitive(
                                                StandardHeaderArguments.FEED, FEED_2))
                                        .addTerm(ExpressionTerm.equalsCaseSensitive(
                                                StandardHeaderArguments.ENVIRONMENT, ENVIRONMENT_1))
                                        .build())
                                .build())
                        .addField(FIELD_FEED)
                        .addField(FIELD_SYSTEM)
                        .addField(FIELD_ENVIRONMENT)
                        .build());

        final HashedReceiveDataRules hashedReceiveDataRules = service.getHashedReceiveDataRules();

        LOGGER.debug("hashedReceiveDataRules: {}", hashedReceiveDataRules);

        assertThat(hashedReceiveDataRules.getRules())
                .hasSize(2);
        assertThat(hashedReceiveDataRules.getFields())
                .hasSize(3);
        assertThat(hashedReceiveDataRules.getHashAlgorithm())
                .isEqualTo(HASH_ALGORITHM);
        assertThat(hashedReceiveDataRules.getFieldNameToSaltMap())
                .hasSize(2) // Feed and Environment
                .containsKey(StandardHeaderArguments.FEED.toLowerCase());
        final String feedSalt = hashedReceiveDataRules.getFieldNameToSaltMap()
                .get(StandardHeaderArguments.FEED.toLowerCase());
        final String environmentSalt = hashedReceiveDataRules.getFieldNameToSaltMap()
                .get(StandardHeaderArguments.ENVIRONMENT.toLowerCase());

        final ReceiveDataRule rule1 = hashedReceiveDataRules.getReceiveDataRules().getRules().get(0);
        final ExpressionTerm rule1Term1 = getChildAsTerm(rule1.getExpression(), 0);
        assertThat(rule1Term1.getField())
                .isEqualTo(HashedReceiveDataRules.markFieldAsHashed(StandardHeaderArguments.FEED));
        assertThat(rule1Term1.getValue())
                .isNotEqualTo(FEED_1);
        final String hashedFeed1 = md5Hasher.hash(FEED_1, feedSalt);
        // This is a hashed term value
        assertThat(rule1Term1.getValue())
                .isEqualTo(hashedFeed1);
        final ExpressionTerm rule1Term2 = getChildAsTerm(rule1.getExpression(), 1);
        assertThat(rule1Term2.getValue())
                .isEqualTo(SYSTEM_1);

        final ReceiveDataRule rule2 = hashedReceiveDataRules.getReceiveDataRules().getRules().get(1);
        final ExpressionTerm rule2Term1 = getChildAsTerm(rule2.getExpression(), 0);
        assertThat(rule2Term1.getField())
                .isEqualTo(HashedReceiveDataRules.markFieldAsHashed(StandardHeaderArguments.FEED));
        assertThat(rule2Term1.getValue())
                .isNotEqualTo(FEED_2);
        final String hashedFeed2 = md5Hasher.hash(FEED_2, feedSalt);
        // This is a hashed term value
        assertThat(rule2Term1.getValue())
                .isEqualTo(hashedFeed2);
        final ExpressionTerm rule2Term2 = getChildAsTerm(rule2.getExpression(), 1);
        assertThat(rule2Term2.getField())
                .isEqualTo(HashedReceiveDataRules.markFieldAsHashed(StandardHeaderArguments.ENVIRONMENT));
        // This is a hashed term value
        final String hashedEnvironment = md5Hasher.hash(ENVIRONMENT_1, environmentSalt);
        assertThat(rule2Term2.getValue())
                .isEqualTo(hashedEnvironment);
    }

    @Test
    void getHashedReceiveDataRules_withDicts() {

        final ReceiveDataRuleSetServiceImpl service = new ReceiveDataRuleSetServiceImpl(
                mockWordListProvider,
                () -> receiptPolicyConfig,
                mockHashFunctionFactory,
                mockReceiveDataRuleSetStore,
                allowAllSecurityContext);

        final Md5Hasher md5Hasher = new Md5Hasher();

        Mockito.when(receiptPolicyConfig.getObfuscationHashAlgorithm())
                .thenReturn(HASH_ALGORITHM);
        Mockito.when(receiptPolicyConfig.getObfuscatedFields())
                .thenReturn(Set.of(
                        StandardHeaderArguments.FEED,
                        StandardHeaderArguments.ENVIRONMENT));
        Mockito.when(mockHashFunctionFactory.getHashFunction(Mockito.any()))
                .thenReturn(md5Hasher);

        final DictionaryDoc parentFeedDict = createDict(
                "ParentFeedDict",
                null,
                FEED_2,
                FEED_3);

        final DictionaryDoc feedDict = createDict(
                "FeedDict",
                parentFeedDict,
                FEED_4,
                FEED_5);

        // Non-obfuscated dict
        final DictionaryDoc systemDict = createDict(
                "SystemDict",
                parentFeedDict,
                SYSTEM_1,
                SYSTEM_2);

        // mock the merging of imports
        Mockito.when(mockWordListProvider.getCombinedData(Mockito.eq(feedDict.asDocRef())))
                .thenReturn(String.join("\n", FEED_2, FEED_3, FEED_4, FEED_5));
        Mockito.when(mockWordListProvider.getCombinedData(Mockito.eq(systemDict.asDocRef())))
                .thenReturn(String.join("\n", SYSTEM_1, SYSTEM_2));

        int ruleNo = 0;
        Mockito.when(mockReceiveDataRuleSetStore.getOrCreate())
                .thenReturn(ReceiveDataRules.builder()
                        .uuid(UUID.randomUUID().toString())
                        .updateTimeMs(Instant.now().toEpochMilli())
                        .addRule(ReceiveDataRule.builder()
                                .withRuleNumber(++ruleNo)
                                .withEnabled(true)
                                .withAction(ReceiveAction.REJECT)
                                .withExpression(ExpressionOperator.builder()
                                        .op(Op.OR)
                                        .addTerm(ExpressionTerm.equalsCaseSensitive(
                                                StandardHeaderArguments.FEED, FEED_1))
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
                                        .addTerm(ExpressionTerm.equalsCaseSensitive(
                                                StandardHeaderArguments.FEED, FEED_2))
                                        .addTerm(ExpressionTerm.equalsCaseSensitive(
                                                StandardHeaderArguments.ENVIRONMENT, ENVIRONMENT_1))
                                        .build())
                                .build())
                        .addField(FIELD_FEED)
                        .addField(FIELD_SYSTEM)
                        .addField(FIELD_ENVIRONMENT)
                        .build());

        final HashedReceiveDataRules hashedReceiveDataRules = service.getHashedReceiveDataRules();

        LOGGER.debug("hashedReceiveDataRules: {}", hashedReceiveDataRules);
        final Map<String, DictionaryDoc> uuidToDictMap = hashedReceiveDataRules.getUuidToFlattenedDictMap();
        uuidToDictMap.values()
                .forEach(doc -> LOGGER.debug("Dict {}\n{}", doc.getName(), doc.getData()));

        final String feedSalt = hashedReceiveDataRules.getFieldNameToSaltMap()
                .get(StandardHeaderArguments.FEED.toLowerCase());
        final String environmentSalt = hashedReceiveDataRules.getFieldNameToSaltMap()
                .get(StandardHeaderArguments.ENVIRONMENT.toLowerCase());

        final DictionaryDoc feedDict2 = uuidToDictMap.get(feedDict.getUuid());
        assertThat(feedDict2)
                .isNotNull();
        assertThat(feedDict2.getData().lines().toList())
                .containsExactly(
                        md5Hasher.hash(FEED_2, feedSalt),
                        md5Hasher.hash(FEED_3, feedSalt),
                        md5Hasher.hash(FEED_4, feedSalt),
                        md5Hasher.hash(FEED_5, feedSalt));

        final DictionaryDoc parentFeedDict2 = uuidToDictMap.get(parentFeedDict.getUuid());
        assertThat(parentFeedDict2)
                .isNull(); // It got flattened into feedDict2
        final DictionaryDoc systemDict2 = uuidToDictMap.get(systemDict.getUuid());
        assertThat(systemDict2)
                .isNotNull();
        assertThat(systemDict2.getData().lines().toList())
                .containsExactly(SYSTEM_1, SYSTEM_2);

        assertThat(hashedReceiveDataRules.getRules())
                .hasSize(2);
        assertThat(hashedReceiveDataRules.getFields())
                .hasSize(3);
        assertThat(hashedReceiveDataRules.getHashAlgorithm())
                .isEqualTo(HASH_ALGORITHM);
        assertThat(hashedReceiveDataRules.getFieldNameToSaltMap())
                .hasSize(2) // Feed and Environment
                .containsKey(StandardHeaderArguments.FEED.toLowerCase());

        final ReceiveDataRule rule1 = hashedReceiveDataRules.getReceiveDataRules().getRules().get(0);
        final ExpressionTerm rule1Term1 = getChildAsTerm(rule1.getExpression(), 0);
        assertThat(rule1Term1.getField())
                .isEqualTo(HashedReceiveDataRules.markFieldAsHashed(StandardHeaderArguments.FEED));
        assertThat(rule1Term1.getValue())
                .isNotEqualTo(FEED_1);
        final String hashedFeed1 = md5Hasher.hash(FEED_1, feedSalt);
        // This is a hashed term value
        assertThat(rule1Term1.getValue())
                .isEqualTo(hashedFeed1);

        final ReceiveDataRule rule2 = hashedReceiveDataRules.getReceiveDataRules().getRules().get(1);
        final ExpressionTerm rule2Term1 = getChildAsTerm(rule2.getExpression(), 0);
        assertThat(rule2Term1.getField())
                .isEqualTo(HashedReceiveDataRules.markFieldAsHashed(StandardHeaderArguments.FEED));
        assertThat(rule2Term1.getValue())
                .isNotEqualTo(FEED_2);
        final String hashedFeed2 = md5Hasher.hash(FEED_2, feedSalt);
        // This is a hashed term value
        assertThat(rule2Term1.getValue())
                .isEqualTo(hashedFeed2);
    }

    private DictionaryDoc createDict(final String name,
                                     final DictionaryDoc importedDoc,
                                     final String... lines) {
        final DictionaryDoc dict = DictionaryDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name(name)
                .build();
        if (importedDoc != null) {
            dict.setImports(List.of(importedDoc.asDocRef()));
        }
        dict.setData(String.join("\n", lines));
        return dict;
    }

    private ExpressionTerm getChildAsTerm(final ExpressionOperator expressionOperator,
                                          final int index) {
        return (ExpressionTerm) expressionOperator.getChildren().get(index);
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


    // --------------------------------------------------------------------------------


    /**
     * Use md5 for shorter/faster hashes in testing
     */
    private static class Md5Hasher implements HashFunction {

        private final SecureRandom secureRandom = new SecureRandom();

        @Override
        public String generateSalt() {
            return StringUtil.createRandomCode(secureRandom, 5);
        }

        protected String getSaltedValue(final String value, final String salt) {
            return salt != null
                    ? salt + value
                    : value;
        }

        @Override
        public String hash(final String value, final String salt) {
            final String saltedVal = getSaltedValue(value, salt);
            return Base58.encode(DigestUtils.md5(saltedVal));
        }

        @Override
        public HashAlgorithm getType() {
            return HASH_ALGORITHM;
        }
    }
}
