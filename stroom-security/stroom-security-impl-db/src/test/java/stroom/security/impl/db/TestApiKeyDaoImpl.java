package stroom.security.impl.db;

import stroom.db.util.JooqUtil;
import stroom.security.impl.ApiKeyDao;
import stroom.security.impl.ApiKeyService.DuplicateHashException;
import stroom.security.impl.HashedApiKeyParts;
import stroom.security.impl.TestModule;
import stroom.security.impl.UserDao;
import stroom.security.shared.ApiKey;
import stroom.security.shared.CreateApiKeyRequest;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.FindUserNameCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserNameProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;
import stroom.util.shared.SimpleUserName;
import stroom.util.shared.UserName;
import stroom.util.string.StringUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.security.impl.db.jooq.Tables.API_KEY;

class TestApiKeyDaoImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestApiKeyDaoImpl.class);

    private static final Map<String, String> SUBJECT_ID_TO_UUID_MAP = Map.of(
            "user1", "user1_uuid",
            "user2", "user2_uuid",
            "user3", "user3_uuid");

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
    private SecurityDbConnProvider dbConnProvider;

    private SecureRandom secureRandom = new SecureRandom();

    private ApiKey user1ApiKey1;
    private ApiKey user1ApiKey2;
    private ApiKey user1ApiKey3;
    private ApiKey user1ApiKey4;
    private ApiKey user1ApiKey5;
    private ApiKey user2ApiKey1;
    private ApiKey user2ApiKey2;
    private ApiKey user2ApiKey3;
    private ApiKey user2ApiKey4;
    private ApiKey user2ApiKey5;
    private ApiKey user3ApiKey1;
//    private List<ApiKey>

    @BeforeEach
    void beforeAll() {
        final Injector injector = Guice.createInjector(
                new SecurityDaoModule(),
                new SecurityDbModule(),
                new TestModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(UserNameProvider.class).to(MyUserNameProvider.class);
                    }
                });

        injector.injectMembers(this);
        loadData();
    }

    @AfterEach
    void tearDown() {
        JooqUtil.context(dbConnProvider, context -> {
            JooqUtil.deleteAll(context, API_KEY);
        });
        UUID_TO_SUBJECT_ID_MAP.keySet()
                .forEach(userDao::delete);
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

        final ApiKey apiKey = apiKeyDao.fetch(user1ApiKey1.getId())
                .orElseThrow();

        assertThat(apiKey.getEnabled())
                .isTrue();

        final ApiKey apiKey2 = ApiKey.builder(apiKey)
                .withEnabled(false)
                .build();

        final ApiKey apiKey3 = apiKeyDao.update(apiKey2);

        assertThat(apiKey3.getEnabled())
                .isFalse();

        final ApiKey apiKey4 = apiKeyDao.fetch(user1ApiKey1.getId())
                .orElseThrow();

        assertThat(apiKey4.getEnabled())
                .isFalse();
    }

    @Test
    void testFind_oneUser() {
        final FindApiKeyCriteria criteria = new FindApiKeyCriteria(null, user1ApiKey1.getOwner());

        final ResultPage<ApiKey> resultPage = apiKeyDao.find(criteria);
        assertThat(resultPage.size())
                .isEqualTo(5);
    }

    @Test
    void testFind_allUsers() {
        final FindApiKeyCriteria criteria = new FindApiKeyCriteria();

        final ResultPage<ApiKey> resultPage = apiKeyDao.find(criteria);
        assertThat(resultPage.size())
                .isEqualTo(11);
    }

    @Test
    void testFind_withFilter() {
        final FindApiKeyCriteria criteria = new FindApiKeyCriteria(
                "\"user1 key 3 inv\"", user1ApiKey3.getOwner());

        final ResultPage<ApiKey> resultPage = apiKeyDao.find(criteria);
        assertThat(resultPage.size())
                .isEqualTo(1);
        final ApiKey apiKey = resultPage.getValues().get(0);
        assertThat(apiKey.getId())
                .isEqualTo(user1ApiKey3.getId());
    }

    @Test
    void testDelete() {
        final int id = user1ApiKey1.getId();
        apiKeyDao.delete(id);

        final Optional<ApiKey> optKey = apiKeyDao.fetch(id);
        assertThat(optKey)
                .isEmpty();
    }

    @Test
    void testFind_withFilter2() {
        final FindApiKeyCriteria criteria = new FindApiKeyCriteria(
                "\"key 1\"", null);

        final ResultPage<ApiKey> resultPage = apiKeyDao.find(criteria);
        assertThat(resultPage.size())
                .isEqualTo(3);
        assertThat(resultPage.getValues())
                .containsExactlyInAnyOrder(
                        user1ApiKey1,
                        user2ApiKey1,
                        user3ApiKey1);
    }

    @Test
    void testCreate() throws DuplicateHashException {
        final String salt = "mySalt";
        final String saltedApiKeyHash = "myHash";
        final String prefix = "sak_1234567_";

        final UserName owner = SimpleUserName.builder()
                .subjectId("user1")
                .uuid("user1_uuid")
                .build();

        final CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .withExpireTimeMs(Instant.now().plus(1, ChronoUnit.DAYS))
                .withName("myKey")
                .withOwner(owner)
                .withEnabled(true)
                .withComments("myComments")
                .build();

        final ApiKey apiKey = apiKeyDao.create(
                request,
                new HashedApiKeyParts(saltedApiKeyHash, salt, prefix));

        assertThat(apiKey)
                .isNotNull();
        assertThat(apiKey.getApiKeySalt())
                .isEqualTo(salt);
        assertThat(apiKey.getApiKeyHash())
                .isEqualTo(saltedApiKeyHash);
        assertThat(apiKey.getApiKeyPrefix())
                .isEqualTo(prefix);
        assertThat(apiKey.getOwner())
                .isEqualTo(request.getOwner());
        assertThat(apiKey.getEnabled())
                .isEqualTo(request.getEnabled());
        assertThat(apiKey.getName())
                .isEqualTo(request.getName());
        assertThat(apiKey.getComments())
                .isEqualTo(request.getComments());
        assertThat(apiKey.getExpireTimeMs())
                .isEqualTo(request.getExpireTimeMs());
    }

    @Test
    void testCreate_dupHashes() throws DuplicateHashException {
        final String salt1 = "mySalt1";
        final String saltedApiKeyHash1 = "myHash";
        final String prefix1 = "sak_1234567_";

        final String salt2 = "mySalt2";
        final String saltedApiKeyHash2 = saltedApiKeyHash1;
        final String prefix2 = "sak_7654321_";

        final UserName owner = SimpleUserName.builder()
                .subjectId("user1")
                .uuid("user1_uuid")
                .build();

        final CreateApiKeyRequest request1 = CreateApiKeyRequest.builder()
                .withExpireTimeMs(Instant.now().plus(1, ChronoUnit.DAYS))
                .withName("myKey1")
                .withOwner(owner)
                .withEnabled(true)
                .withComments("myComments")
                .build();

        final ApiKey apiKey1 = apiKeyDao.create(
                request1,
                new HashedApiKeyParts(saltedApiKeyHash1, salt1, prefix1));

        final CreateApiKeyRequest request2 = CreateApiKeyRequest.builder()
                .withExpireTimeMs(Instant.now().plus(1, ChronoUnit.DAYS))
                .withName("myKey2")
                .withOwner(owner)
                .withEnabled(true)
                .withComments("myComments")
                .build();

        Assertions.assertThatThrownBy(() -> {
                    apiKeyDao.create(
                            request2,
                            new HashedApiKeyParts(saltedApiKeyHash2, salt2, prefix2));
                })
                .isInstanceOf(DuplicateHashException.class);
    }

    @Test
    void testCreate_dupNames() throws DuplicateHashException {
        final String salt1 = "mySalt1";
        final String saltedApiKeyHash1 = "myHash1";
        final String prefix1 = "sak_1234567_";

        final String salt2 = "mySalt2";
        final String saltedApiKeyHash2 = "myHash2";
        final String prefix2 = "sak_7654321_";

        final UserName owner = SimpleUserName.builder()
                .subjectId("user1")
                .uuid("user1_uuid")
                .build();

        final CreateApiKeyRequest request1 = CreateApiKeyRequest.builder()
                .withExpireTimeMs(Instant.now().plus(1, ChronoUnit.DAYS))
                .withName("myKey1")
                .withOwner(owner)
                .withEnabled(true)
                .withComments("myComments")
                .build();

        final ApiKey apiKey1 = apiKeyDao.create(
                request1,
                new HashedApiKeyParts(saltedApiKeyHash1, salt1, prefix1));

        final CreateApiKeyRequest request2 = CreateApiKeyRequest.builder()
                .withExpireTimeMs(Instant.now().plus(1, ChronoUnit.DAYS))
                .withName("myKey1")
                .withOwner(owner)
                .withEnabled(true)
                .withComments("myComments")
                .build();

        Assertions.assertThatThrownBy(() -> {
                    apiKeyDao.create(
                            request2,
                            new HashedApiKeyParts(saltedApiKeyHash2, salt2, prefix2));
                })
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("name");
    }

    private void doFetchVerifiedIdentityTest(final ApiKey apiKey, boolean isValid) {

        final Optional<String> optUuid = apiKeyDao.fetchVerifiedUserUuid(apiKey.getApiKeyHash());
        if (isValid) {
            assertThat(optUuid)
                    .hasValue(apiKey.getOwner().getUuid());
        } else {
            assertThat(optUuid)
                    .isEmpty();
        }
    }

    private void loadData() {
        final long nowMs = Instant.now().toEpochMilli();

        UUID_TO_SUBJECT_ID_MAP.forEach((uuid, subjectId) -> {
            final User user = User.builder()
                    .subjectId(subjectId)
                    .uuid(uuid)
                    .group(false)
                    .build();
            user.setCreateUser(subjectId);
            user.setUpdateUser(subjectId);
            user.setCreateTimeMs(nowMs);
            user.setUpdateTimeMs(nowMs);
            userDao.create(user);
        });

        user1ApiKey1 = apiKey("user1 key 1 valid", "user1", EXPIRY_IN_FUTURE, true);
        user1ApiKey2 = apiKey("user1 key 2 valid", "user1", EXPIRY_IN_FUTURE, true);
        user1ApiKey3 = apiKey("user1 key 3 invalid", "user1", EXPIRY_IN_PAST, true);
        user1ApiKey4 = apiKey("user1 key 4 invalid", "user1", EXPIRY_IN_FUTURE, false);
        user1ApiKey5 = apiKey("user1 key 5 invalid", "user1", EXPIRY_IN_PAST, false);

        user2ApiKey1 = apiKey("user2 key 1 valid", "user2", EXPIRY_IN_FUTURE, true);
        user2ApiKey2 = apiKey("user2 key 2 valid", "user2", EXPIRY_IN_FUTURE, true);
        user2ApiKey3 = apiKey("user2 key 3 invalid", "user2", EXPIRY_IN_PAST, true);
        user2ApiKey4 = apiKey("user2 key 4 invalid", "user2", EXPIRY_IN_FUTURE, false);
        user2ApiKey5 = apiKey("user2 key 5 invalid", "user2", EXPIRY_IN_PAST, false);

        user3ApiKey1 = apiKey("user3 key 1 valid", "user3", EXPIRY_IN_FUTURE, true);
    }

    private ApiKey apiKey(final String keyName,
                          final String userSubjectId,
                          final Instant expiryTime,
                          final boolean enabled) {
        // Doesn't matter what the values are for these tests
        final String salt = StringUtil.createRandomCode(secureRandom, 10);
        final String hash = StringUtil.createRandomCode(secureRandom, 20);
        final String prefixHash = StringUtil.createRandomCode(secureRandom, 7);

        final CreateApiKeyRequest createApiKeyRequest = CreateApiKeyRequest.builder()
                .withName(keyName)
                .withOwner(SimpleUserName.builder()
                        .uuid(userSubjectId + "_uuid")
                        .subjectId(userSubjectId)
                        .build())
                .withEnabled(enabled)
                .withExpireTimeMs(expiryTime)
                .build();

        final HashedApiKeyParts hashedApiKeyParts = new HashedApiKeyParts(
                hash, salt, "sak_" + prefixHash + "_");

        try {
            return apiKeyDao.create(createApiKeyRequest, hashedApiKeyParts);
        } catch (DuplicateHashException e) {
            throw new RuntimeException(e);
        }
    }


    // --------------------------------------------------------------------------------


    private static class MyUserNameProvider implements UserNameProvider {

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public ResultPage<UserName> findUserNames(final FindUserNameCriteria criteria) {
            return null;
        }

        @Override
        public ResultPage<UserName> findAssociates(final FindUserNameCriteria criteria) {
            return null;
        }

        @Override
        public Optional<UserName> getBySubjectId(final String subjectId) {
            return Optional.ofNullable(SUBJECT_ID_TO_UUID_MAP.get(subjectId))
                    .map(uuid -> SimpleUserName.builder()
                            .uuid(uuid)
                            .subjectId(subjectId)
                            .build());
        }

        @Override
        public Optional<UserName> getByDisplayName(final String displayName) {
            return Optional.empty();
        }

        @Override
        public Optional<UserName> getByUuid(final String userUuid) {
            return Optional.ofNullable(UUID_TO_SUBJECT_ID_MAP.get(userUuid))
                    .map(subjectId -> SimpleUserName.builder()
                            .uuid(userUuid)
                            .subjectId(subjectId)
                            .build());
        }
    }
}
