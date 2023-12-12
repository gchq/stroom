package stroom.security.impl.db;

import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.security.api.SecurityContext;
import stroom.security.impl.ApiKeyDao;
import stroom.security.impl.ApiKeyService.DuplicateHashException;
import stroom.security.impl.HashedApiKeyParts;
import stroom.security.impl.UserCache;
import stroom.security.impl.db.jooq.tables.records.ApiKeyRecord;
import stroom.security.shared.ApiKey;
import stroom.security.shared.ApiKeyResultPage;
import stroom.security.shared.CreateApiKeyRequest;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.util.NullSafe;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.security.impl.db.jooq.Tables.API_KEY;

@Singleton
public class ApiKeyDaoImpl implements ApiKeyDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApiKeyDaoImpl.class);

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            FindApiKeyCriteria.FIELD_NAME, API_KEY.NAME,
            FindApiKeyCriteria.FIELD_COMMENTS, API_KEY.COMMENTS,
            FindApiKeyCriteria.FIELD_EXPIRE_TIME, API_KEY.EXPIRES_ON_MS,
            FindApiKeyCriteria.FIELD_ENABLED, API_KEY.ENABLED);
    public static final int INITIAL_VERSION = 1;

    private final SecurityDbConnProvider securityDbConnProvider;
    private final GenericDao<ApiKeyRecord, ApiKey, Integer> genericDao;
    private final UserCache userCache;
    private final SecurityContext securityContext;

    @Inject
    public ApiKeyDaoImpl(final SecurityDbConnProvider securityDbConnProvider,
                         final UserCache userCache,
                         final SecurityContext securityContext) {
        this.securityDbConnProvider = securityDbConnProvider;
        this.genericDao = new GenericDao<>(
                securityDbConnProvider,
                API_KEY,
                API_KEY.ID,
                this::mapApiKeyToRecord,
                this::mapRecordToApiKey);
        this.userCache = userCache;
        this.securityContext = securityContext;
    }

    @Override
    public ResultPage<ApiKey> find(final FindApiKeyCriteria criteria) {

        final Condition ownerCondition = NullSafe.getOrElseGet(
                criteria.getOwner(),
                owner -> API_KEY.FK_OWNER_UUID.eq(owner.getUuid()),
                DSL::trueCondition);

        final String fullyQualifyFilterInput = QuickFilterPredicateFactory.fullyQualifyInput(
                criteria.getQuickFilterInput(),
                ApiKeyDao.FILTER_FIELD_MAPPERS);

        ResultPage<ApiKey> resultPage = QuickFilterPredicateFactory.filterStream(
                        criteria.getQuickFilterInput(),
                        FILTER_FIELD_MAPPERS,
                        JooqUtil.contextResult(securityDbConnProvider, context -> context
                                        .select()
                                        .from(API_KEY)
                                        .where(ownerCondition)
                                        .orderBy(API_KEY.NAME)
                                        .fetch())
                                .stream()
                                .map(this::mapRecordToApiKey)
                )
                .collect(ApiKeyResultPage.collector(
                        criteria.getPageRequest(),
                        (apiKeys, pageResponse) ->
                                new ApiKeyResultPage(apiKeys, pageResponse, fullyQualifyFilterInput)));

        LOGGER.debug(() -> LogUtil.message("Returning {} results", resultPage.size()));
        return resultPage;
    }

    @Override
    public Optional<String> fetchVerifiedUserUuid(final String apiKeyHash) {
        if (NullSafe.isBlankString(apiKeyHash)) {
            return Optional.empty();
        } else {
            final long nowMs = Instant.now().toEpochMilli();
            return JooqUtil.contextResult(securityDbConnProvider, context -> context
                    .select(API_KEY.FK_OWNER_UUID)
                    .from(API_KEY)
                    .where(API_KEY.API_KEY_HASH.eq(apiKeyHash.trim()))
                    .and(API_KEY.ENABLED.isTrue())
                    .and(API_KEY.EXPIRES_ON_MS.greaterThan(nowMs))
                    .fetchOptional(API_KEY.FK_OWNER_UUID));
        }
    }

    @Override
    public List<ApiKey> fetchValidApiKeysByPrefix(final String apiKeyPrefix) {
        Objects.requireNonNull(apiKeyPrefix);

        final long nowMs = Instant.now().toEpochMilli();
        final Result<ApiKeyRecord> result = JooqUtil.contextResult(securityDbConnProvider, context ->
                context.selectFrom(API_KEY)
                        .where(API_KEY.API_KEY_PREFIX.eq(apiKeyPrefix))
                        .and(API_KEY.ENABLED.isTrue())
                        .and(DSL.or(
                                API_KEY.EXPIRES_ON_MS.isNull(),
                                API_KEY.EXPIRES_ON_MS.greaterThan(nowMs)))
                        .fetch());
        return result.map(this::mapRecordToApiKey);
    }

    @Override
    public ApiKey create(final CreateApiKeyRequest createApiKeyRequest,
                         final HashedApiKeyParts hashedApiKeyParts) throws DuplicateHashException {
        Objects.requireNonNull(createApiKeyRequest);
        Objects.requireNonNull(hashedApiKeyParts);
        final String userIdentityForAudit = securityContext.getUserIdentityForAudit();
        final long nowMs = Instant.now().toEpochMilli();
        final int version = INITIAL_VERSION;

        final ApiKeyRecord apiKeyRecord;
        try {
            apiKeyRecord = JooqUtil.contextResult(securityDbConnProvider, context ->
                    context.insertInto(API_KEY,
                                    API_KEY.VERSION,
                                    API_KEY.CREATE_TIME_MS,
                                    API_KEY.CREATE_USER,
                                    API_KEY.UPDATE_TIME_MS,
                                    API_KEY.UPDATE_USER,
                                    API_KEY.FK_OWNER_UUID,
                                    API_KEY.API_KEY_HASH,
                                    API_KEY.API_KEY_SALT,
                                    API_KEY.API_KEY_PREFIX,
                                    API_KEY.EXPIRES_ON_MS,
                                    API_KEY.NAME,
                                    API_KEY.COMMENTS,
                                    API_KEY.ENABLED)
                            .values(version,
                                    nowMs,
                                    userIdentityForAudit,
                                    nowMs,
                                    userIdentityForAudit,
                                    Objects.requireNonNull(createApiKeyRequest.getOwner().getUuid()),
                                    hashedApiKeyParts.saltedApiKeyHash(),
                                    hashedApiKeyParts.salt(),
                                    hashedApiKeyParts.apiKeyPrefix(),
                                    createApiKeyRequest.getExpireTimeMs(),
                                    createApiKeyRequest.getName(),
                                    createApiKeyRequest.getComments(),
                                    createApiKeyRequest.getEnabled())
                            .returning(API_KEY.ID)
                            .fetchOne()
            );
        } catch (DataAccessException e) {
            final Throwable rootCause = ExceptionUtils.getRootCause(e);
            if (rootCause instanceof SQLIntegrityConstraintViolationException
                    && rootCause.getMessage().contains(API_KEY.API_KEY_HASH.getName())) {
                throw new DuplicateHashException("Duplicate API key hash value.", e);
            } else {
                throw e;
            }
        }
        final Integer id = apiKeyRecord.get(API_KEY.ID);
        // Re-fetch so we know we have what is in the DB.
        return fetch(id)
                .orElseThrow(() -> new RuntimeException("No API key found for ID " + id));
    }

    @Override
    public Optional<ApiKey> fetch(final int id) {
        return genericDao.fetch(id);
    }

    @Override
    public ApiKey update(final ApiKey apiKey) {
        return genericDao.update(apiKey);
    }

    @Override
    public boolean delete(final int id) {
        return genericDao.delete(id);
    }

    private ApiKeyRecord mapApiKeyToRecord(final ApiKey apiKey, final ApiKeyRecord record) {
        record.from(apiKey);
        record.set(API_KEY.ID, apiKey.getId());
        record.set(API_KEY.VERSION, apiKey.getVersion());
        record.set(API_KEY.CREATE_TIME_MS, apiKey.getCreateTimeMs());
        record.set(API_KEY.CREATE_USER, apiKey.getCreateUser());
        record.set(API_KEY.UPDATE_TIME_MS, apiKey.getUpdateTimeMs());
        record.set(API_KEY.UPDATE_USER, apiKey.getUpdateUser());
        record.set(API_KEY.FK_OWNER_UUID, NullSafe.get(apiKey.getOwner(), UserName::getUuid));
        record.set(API_KEY.API_KEY_HASH, apiKey.getApiKeyHash());
        record.set(API_KEY.API_KEY_SALT, apiKey.getApiKeySalt());
        record.set(API_KEY.API_KEY_PREFIX, apiKey.getApiKeyPrefix());
        record.set(API_KEY.EXPIRES_ON_MS, apiKey.getExpireTimeMs());
        record.set(API_KEY.NAME, apiKey.getName());
        record.set(API_KEY.COMMENTS, apiKey.getComments());
        record.set(API_KEY.ENABLED, apiKey.getEnabled());
        return record;
    }

    private ApiKey mapRecordToApiKey(final Record record) {
        final String ownerUuid = record.get(API_KEY.FK_OWNER_UUID);
        final UserName owner = userCache.getByUuid(ownerUuid)
                .orElseThrow(() -> new RuntimeException(LogUtil.message(
                        "User with uuid {} not found", ownerUuid)));

        return ApiKey.builder()
                .withId(record.get(API_KEY.ID))
                .withVersion(record.get(API_KEY.VERSION))
                .withCreateTimeMs(record.get(API_KEY.CREATE_TIME_MS))
                .withCreateUser(record.get(API_KEY.CREATE_USER))
                .withUpdateTimeMs(record.get(API_KEY.UPDATE_TIME_MS))
                .withUpdateUser(record.get(API_KEY.UPDATE_USER))
                .withOwner(owner)
                .withApiKeyHash(record.get(API_KEY.API_KEY_HASH))
                .withApiKeySalt(record.get(API_KEY.API_KEY_SALT))
                .withApiKeyPrefix(record.get(API_KEY.API_KEY_PREFIX))
                .withExpireTimeMs(record.get(API_KEY.EXPIRES_ON_MS))
                .withName(record.get(API_KEY.NAME))
                .withComments(record.get(API_KEY.COMMENTS))
                .withEnabled(record.get(API_KEY.ENABLED))
                .build();
    }
}
