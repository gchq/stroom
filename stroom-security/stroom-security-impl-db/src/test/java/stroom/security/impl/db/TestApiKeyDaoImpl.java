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

package stroom.security.impl.db;

import stroom.query.api.ExpressionOperator;
import stroom.security.impl.HashedApiKeyParts;
import stroom.security.impl.TestModule;
import stroom.security.impl.UserDao;
import stroom.security.impl.apikey.ApiKeyDao;
import stroom.security.impl.apikey.ApiKeyService.DuplicateApiKeyException;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.HashedApiKey;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.User;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.string.StringUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestApiKeyDaoImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestApiKeyDaoImpl.class);

    public static final String USER_1 = "user1";
    public static final String USER_2 = "user2";
    public static final String USER_3 = "user3";

    private static final Map<String, String> SUBJECT_ID_TO_UUID_MAP = Map.of(
            USER_1, "user1_uuid",
            USER_2, "user2_uuid",
            USER_3, "user3_uuid");

    private static final Map<String, String> UUID_TO_SUBJECT_ID_MAP = SUBJECT_ID_TO_UUID_MAP.entrySet()
            .stream()
            .collect(Collectors.toMap(Entry::getValue, Entry::getKey));

    private static final Instant EXPIRY_IN_PAST = Instant.now().minus(10, ChronoUnit.DAYS);
    private static final Instant EXPIRY_IN_FUTURE = Instant.now().plus(10, ChronoUnit.DAYS);

    @Inject
    private ApiKeyDao apiKeyDao;
    @Inject
    private UserDao userDao;
    @Inject
    private SecurityDbConnProvider securityDbConnProvider;

    private SecureRandom secureRandom = new SecureRandom();

    private HashedApiKey user1ApiKey1;
    private HashedApiKey user1ApiKey2;
    private HashedApiKey user1ApiKey3;
    private HashedApiKey user1ApiKey4;
    private HashedApiKey user1ApiKey5;
    private HashedApiKey user2ApiKey1;
    private HashedApiKey user2ApiKey2;
    private HashedApiKey user2ApiKey3;
    private HashedApiKey user2ApiKey4;
    private HashedApiKey user2ApiKey5;
    private HashedApiKey user3ApiKey1;
//    private List<ApiKey>

    @BeforeEach
    void beforeEach() {
        final Injector injector = Guice.createInjector(
                new SecurityDaoModule(),
                new SecurityDbModule(),
                new TestModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
//                        bind(UserNameProvider.class).to(MyUserNameProvider.class);
                    }
                });

        injector.injectMembers(this);
        loadData();
    }

    @AfterEach
    void tearDown() {
        SecurityTestUtil.teardown(securityDbConnProvider);
    }

    @Test
    void testFetchVerifiedIdentity() {

        doFetchVerifiedIdentityTest(user1ApiKey1, true);
        doFetchVerifiedIdentityTest(user1ApiKey2, true);
        doFetchVerifiedIdentityTest(user1ApiKey3, false);
        doFetchVerifiedIdentityTest(user1ApiKey4, false);
        doFetchVerifiedIdentityTest(user1ApiKey5, false);

        doFetchVerifiedIdentityTest(user2ApiKey1, true);
        doFetchVerifiedIdentityTest(user2ApiKey2, true);
        doFetchVerifiedIdentityTest(user2ApiKey3, false);
        doFetchVerifiedIdentityTest(user2ApiKey4, false);
        doFetchVerifiedIdentityTest(user2ApiKey5, false);

        doFetchVerifiedIdentityTest(user3ApiKey1, true);
    }

    @Test
    void testUpdate() {

        final HashedApiKey apiKey = apiKeyDao.fetch(user1ApiKey1.getId())
                .orElseThrow();

        assertThat(apiKey.getEnabled())
                .isTrue();

        final HashedApiKey apiKey2 = HashedApiKey.builder(apiKey)
                .withEnabled(false)
                .build();

        final HashedApiKey apiKey3 = apiKeyDao.update(apiKey2);

        assertThat(apiKey3.getEnabled())
                .isFalse();

        final HashedApiKey apiKey4 = apiKeyDao.fetch(user1ApiKey1.getId())
                .orElseThrow();

        assertThat(apiKey4.getEnabled())
                .isFalse();
    }

    @Test
    void testFind_oneUser() {
        final FindApiKeyCriteria criteria = FindApiKeyCriteria.create(user1ApiKey1.getOwner());

        final ResultPage<HashedApiKey> resultPage = apiKeyDao.find(criteria);
        assertThat(resultPage.size())
                .isEqualTo(5);
    }

    @Test
    void testFind_allUsers() {
        final FindApiKeyCriteria criteria = new FindApiKeyCriteria();

        final ResultPage<HashedApiKey> resultPage = apiKeyDao.find(criteria);
        assertThat(resultPage.size())
                .isEqualTo(11);
    }

    @Test
    void testFind_withFilter() {
        final ExpressionOperator expr = QuickFilterExpressionParser.parse(
                "\"user1 key 3 inv\"",
                FindApiKeyCriteria.DEFAULT_FIELDS,
                FindApiKeyCriteria.ALL_FIELDs_MAP);

        final FindApiKeyCriteria criteria = FindApiKeyCriteria.builder()
                .owner(user1ApiKey3.getOwner())
                .expression(expr)
                .build();

        final ResultPage<HashedApiKey> resultPage = apiKeyDao.find(criteria);
        assertThat(resultPage.size())
                .isEqualTo(1);
        final HashedApiKey apiKey = resultPage.getValues().get(0);
        assertThat(apiKey.getId())
                .isEqualTo(user1ApiKey3.getId());
    }

    @Test
    void testDelete() {
        final int id = user1ApiKey1.getId();
        apiKeyDao.delete(id);

        final Optional<HashedApiKey> optKey = apiKeyDao.fetch(id);
        assertThat(optKey)
                .isEmpty();
    }

    @Test
    void testFind_withFilter2() {
        final ExpressionOperator expr = QuickFilterExpressionParser.parse(
                "\"key 1\"",
                FindApiKeyCriteria.DEFAULT_FIELDS,
                FindApiKeyCriteria.ALL_FIELDs_MAP);
        final FindApiKeyCriteria criteria = FindApiKeyCriteria.builder()
                .expression(expr)
                .build();

        final ResultPage<HashedApiKey> resultPage = apiKeyDao.find(criteria);
        assertThat(resultPage.size())
                .isEqualTo(3);
        assertThat(resultPage.getValues())
                .containsExactlyInAnyOrder(
                        user1ApiKey1,
                        user2ApiKey1,
                        user3ApiKey1);
    }

    @Test
    void testCreate() throws DuplicateApiKeyException {
        final String saltedApiKeyHash = "myHash";
        final String prefix = "sak_1234567_";

        final UserRef user = createUser("user1");

        final CreateHashedApiKeyRequest request = CreateHashedApiKeyRequest.builder()
                .withExpireTimeMs(Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli())
                .withName("myKey")
                .withOwner(user)
                .withEnabled(true)
                .withComments("myComments")
                .build();

        final HashedApiKey apiKey = apiKeyDao.create(
                request,
                new HashedApiKeyParts(saltedApiKeyHash, prefix));

        assertThat(apiKey)
                .isNotNull();
        assertThat(apiKey.getApiKeyHash())
                .isEqualTo(saltedApiKeyHash);
        assertThat(apiKey.getApiKeyPrefix())
                .isEqualTo(prefix);
        assertThat(apiKey.getOwner().getSubjectId())
                .isEqualTo(request.getOwner().getSubjectId());
        assertThat(apiKey.getOwner().getUuid())
                .isEqualTo(request.getOwner().getUuid());
        assertThat(apiKey.getEnabled())
                .isEqualTo(request.getEnabled());
        assertThat(apiKey.getName())
                .isEqualTo(request.getName());
        assertThat(apiKey.getComments())
                .isEqualTo(request.getComments());
        assertThat(apiKey.getExpireTimeMs())
                .isEqualTo(request.getExpireTimeMs());
    }


    private HashedApiKey createApiKey(final String ownerSubjectId,
                                      final String keyName,
                                      final String prefix,
                                      final String hash) throws DuplicateApiKeyException {

        final UserRef user = createUser(ownerSubjectId);

        final CreateHashedApiKeyRequest request1 = CreateHashedApiKeyRequest.builder()
                .withExpireTimeMs(Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli())
                .withName(keyName)
                .withOwner(user)
                .withEnabled(true)
                .withComments("myComments")
                .build();

        return apiKeyDao.create(
                request1,
                new HashedApiKeyParts(hash, prefix));
    }

    @Test
    void testCreate_dupHashes() throws DuplicateApiKeyException {
        final String user1 = USER_1;
        final String user2 = USER_2;
        final String hash1 = "hash1";
        final String prefix1 = "sak_1234567_";

        final String hash2 = "hash2";
        final String prefix2 = "sak_7654321_";

        final HashedApiKey key1a = createApiKey(user1, "key1a", prefix1, hash1);

        // Same prefix and hash as an existing key
        Assertions.assertThatThrownBy(
                        () -> {
                            createApiKey(user1, "key1b", prefix1, hash1);
                        })
                .isInstanceOf(DuplicateApiKeyException.class);

        // Same prefix and hash as an existing key, even with diff user
        Assertions.assertThatThrownBy(
                        () -> {
                            createApiKey(user2, "key1c", prefix1, hash1);
                        })
                .isInstanceOf(DuplicateApiKeyException.class);

        // Diff prefix, but same hash
        Assertions.assertThatThrownBy(
                        () -> {
                            createApiKey(user1, "key2", prefix2, hash1);
                        })
                .isInstanceOf(DuplicateApiKeyException.class);

        // Diff hash, same prefix
        final HashedApiKey key3 = createApiKey(user1, "key3", prefix1, hash2);
    }

    @Test
    void testCreate_dupKeyNames() throws DuplicateApiKeyException {
        final String user1 = USER_1;
        final String user2 = USER_2;
        final String hash1 = "myHash1";
        final String prefix1 = "sak_1234567_";

        final String hash2 = "myHash2";
        final String prefix2 = "sak_7654321_";

        final String keyName = "dup_name";

        // Creates OK
        createApiKey(user1, keyName, hash1, prefix1);

        // Can't create, same name for owner
        Assertions.assertThatThrownBy(
                        () -> {
                            createApiKey(user1, keyName, hash2, prefix2);
                        })
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("name");

        // Creates OK, same keyName but different user
        createApiKey(user2, keyName, hash2, prefix2);
    }

    private void doFetchVerifiedIdentityTest(final HashedApiKey apiKey, final boolean isValid) {

        final List<HashedApiKey> apiKeys = apiKeyDao.fetchValidApiKeysByPrefix(apiKey.getApiKeyPrefix());
        if (isValid) {
            assertThat(apiKeys)
                    .hasSize(1);
            final HashedApiKey fetchedKey = apiKeys.getFirst();
            assertThat(fetchedKey.getApiKeyPrefix())
                    .isEqualTo(apiKey.getApiKeyPrefix());
            assertThat(fetchedKey.getApiKeyHash())
                    .isEqualTo(apiKey.getApiKeyHash());
            assertThat(fetchedKey.getExpireTimeMs())
                    .isGreaterThan(System.currentTimeMillis());
            assertThat(fetchedKey.getEnabled())
                    .isTrue();
            assertThat(fetchedKey.getOwner())
                    .isEqualTo(apiKey.getOwner());
        } else {
            assertThat(apiKeys)
                    .isEmpty();
        }
    }

    private void loadData() {
//        final long nowMs = Instant.now().toEpochMilli();
//
//        UUID_TO_SUBJECT_ID_MAP.forEach((uuid, subjectId) -> {
//            final User user = User.builder()
//                    .subjectId(subjectId)
//                    .uuid(uuid)
//                    .group(false)
//                    .build();
//            user.setCreateUser(subjectId);
//            user.setUpdateUser(subjectId);
//            user.setCreateTimeMs(nowMs);
//            user.setUpdateTimeMs(nowMs);
//            userDao.create(user);
//        });

        user1ApiKey1 = apiKey("user1 key 1 valid", USER_1, EXPIRY_IN_FUTURE, true);
        user1ApiKey2 = apiKey("user1 key 2 valid", USER_1, EXPIRY_IN_FUTURE, true);
        user1ApiKey3 = apiKey("user1 key 3 invalid", USER_1, EXPIRY_IN_PAST, true);
        user1ApiKey4 = apiKey("user1 key 4 invalid", USER_1, EXPIRY_IN_FUTURE, false);
        user1ApiKey5 = apiKey("user1 key 5 invalid", USER_1, EXPIRY_IN_PAST, false);

        user2ApiKey1 = apiKey("user2 key 1 valid", USER_2, EXPIRY_IN_FUTURE, true);
        user2ApiKey2 = apiKey("user2 key 2 valid", USER_2, EXPIRY_IN_FUTURE, true);
        user2ApiKey3 = apiKey("user2 key 3 invalid", USER_2, EXPIRY_IN_PAST, true);
        user2ApiKey4 = apiKey("user2 key 4 invalid", USER_2, EXPIRY_IN_FUTURE, false);
        user2ApiKey5 = apiKey("user2 key 5 invalid", USER_2, EXPIRY_IN_PAST, false);

        user3ApiKey1 = apiKey("user3 key 1 valid", USER_3, EXPIRY_IN_FUTURE, true);
    }

    private HashedApiKey apiKey(final String keyName,
                                final String userSubjectId,
                                final Instant expiryTime,
                                final boolean enabled) {
        // Doesn't matter what the values are for these tests
        final String hash = StringUtil.createRandomCode(secureRandom, 20);
        final String prefixHash = StringUtil.createRandomCode(secureRandom, 7);
        final UserRef user = createUser(userSubjectId);

        final CreateHashedApiKeyRequest createHashedApiKeyRequest = CreateHashedApiKeyRequest.builder()
                .withName(keyName)
                .withOwner(user)
//                .withOwner(SimpleUserName.builder()
//                        .uuid(userSubjectId + "_uuid")
//                        .subjectId(userSubjectId)
//                        .build())
                .withEnabled(enabled)
                .withExpireTimeMs(expiryTime.toEpochMilli())
                .build();

        final HashedApiKeyParts hashedApiKeyParts = new HashedApiKeyParts(hash, "sak_" + prefixHash + "_");

        try {
            return apiKeyDao.create(createHashedApiKeyRequest, hashedApiKeyParts);
        } catch (final DuplicateApiKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private UserRef createUser(final String subjectId) {
        final long nowMs = Instant.now().toEpochMilli();
        final User user = User.builder()
                .subjectId(subjectId)
                .group(false)
                .enabled(true)
                .build();
        user.setCreateUser(subjectId);
        user.setUpdateUser(subjectId);
        user.setCreateTimeMs(nowMs);
        user.setUpdateTimeMs(nowMs);
        final User persistedUser = userDao.tryCreate(user);
        return persistedUser.asRef();
    }
}
