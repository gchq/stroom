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

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.query.api.ExpressionOperator;
import stroom.security.api.SecurityContext;
import stroom.security.impl.HashedApiKeyParts;
import stroom.security.impl.UserCache;
import stroom.security.impl.apikey.ApiKeyDao;
import stroom.security.impl.apikey.ApiKeyService.DuplicateApiKeyException;
import stroom.security.impl.db.jooq.tables.records.ApiKeyRecord;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.HashAlgorithm;
import stroom.security.shared.HashedApiKey;
import stroom.security.shared.User;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.string.StringUtil;

import com.mysql.cj.exceptions.MysqlErrorNumbers;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.CaseConditionStep;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static stroom.security.impl.db.jooq.tables.ApiKey.API_KEY;
import static stroom.security.impl.db.jooq.tables.StroomUser.STROOM_USER;

@Singleton
public class ApiKeyDaoImpl implements ApiKeyDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApiKeyDaoImpl.class);

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            FindApiKeyCriteria.FIELD_NAME, API_KEY.NAME,
            FindApiKeyCriteria.FIELD_PREFIX, API_KEY.API_KEY_PREFIX,
            FindApiKeyCriteria.FIELD_OWNER, STROOM_USER.DISPLAY_NAME,
            FindApiKeyCriteria.FIELD_COMMENTS, API_KEY.COMMENTS,
            FindApiKeyCriteria.FIELD_EXPIRE_TIME, API_KEY.EXPIRES_ON_MS,
            FindApiKeyCriteria.FIELD_STATE, API_KEY.ENABLED,
            FindApiKeyCriteria.FIELD_HASH_ALGORITHM, API_KEY.HASH_ALGORITHM);

    public static final int INITIAL_VERSION = 1;


    private static final Field<String> HASH_NAME;

    private final SecurityDbConnProvider securityDbConnProvider;
    private final GenericDao<ApiKeyRecord, HashedApiKey, Integer> genericDao;
    private final UserCache userCache;
    private final SecurityContext securityContext;
    private final ExpressionMapper expressionMapper;

    static {
        // Make a SQL field for the hash algo name so the QF can work on it
        CaseConditionStep<String> step = null;
        for (final HashAlgorithm hashAlgorithm : HashAlgorithm.values()) {
            final Condition condition = API_KEY.HASH_ALGORITHM.eq(hashAlgorithm.getPrimitiveValue());
            final String valIfTrue = hashAlgorithm.getDisplayValue();
            if (step == null) {
                step = DSL.when(condition, valIfTrue);
            } else {
                step = step.when(condition, valIfTrue);
            }
        }
        HASH_NAME = Objects.requireNonNull(step)
                .as(DSL.field("hash_name", String.class));
    }

    @Inject
    public ApiKeyDaoImpl(final SecurityDbConnProvider securityDbConnProvider,
                         final UserCache userCache,
                         final SecurityContext securityContext,
                         final ExpressionMapperFactory expressionMapperFactory) {
        this.securityDbConnProvider = securityDbConnProvider;
        this.genericDao = new GenericDao<>(
                securityDbConnProvider,
                API_KEY,
                API_KEY.ID,
                this::mapApiKeyToRecord,
                this::mapRecordToApiKey);
        this.userCache = userCache;
        this.securityContext = securityContext;

        expressionMapper = expressionMapperFactory.create()
                .map(FindApiKeyCriteria.OWNER, STROOM_USER.DISPLAY_NAME, String::valueOf)
                .map(FindApiKeyCriteria.NAME, API_KEY.NAME, String::valueOf)
                .map(FindApiKeyCriteria.PREFIX, API_KEY.API_KEY_PREFIX, String::valueOf)
                .map(FindApiKeyCriteria.COMMENTS, API_KEY.COMMENTS, String::valueOf)
                .map(FindApiKeyCriteria.STATE, API_KEY.ENABLED, StringUtil::asBoolean)
                .map(FindApiKeyCriteria.HASH_ALGORITHM, HASH_NAME, String::valueOf);
    }

    @Override
    public ResultPage<HashedApiKey> find(final FindApiKeyCriteria criteria) {

        Objects.requireNonNull(criteria);
        final PageRequest pageRequest = criteria.getPageRequest();
        final Condition ownerCondition = NullSafe.getOrElseGet(
                criteria.getOwner(),
                owner -> API_KEY.FK_OWNER_UUID.eq(owner.getUuid()),
                DSL::trueCondition);

        final ExpressionOperator expressionOperator = criteria.getExpression();

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(
                FIELD_MAP,
                criteria,
                API_KEY.EXPIRES_ON_MS);
        final int limit = JooqUtil.getLimit(pageRequest, true);
        final int offset = JooqUtil.getOffset(pageRequest);

        final List<SelectFieldOrAsterisk> selectFields = new ArrayList<>();
        selectFields.add(API_KEY.asterisk());
        // Only add it if we need it to save the cost of evaluating it
        final Condition exprCondition;
        if (expressionOperator != null) {
            if (expressionOperator.containsField(FindApiKeyCriteria.HASH_ALGORITHM.getFldName())) {
                selectFields.add(HASH_NAME);
            }
            exprCondition = expressionMapper.apply(expressionOperator);
        } else {
            exprCondition = DSL.trueCondition();
        }

        final ResultPage<HashedApiKey> resultPage = JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select(selectFields)
                        .from(API_KEY)
                        .innerJoin(STROOM_USER).on(API_KEY.FK_OWNER_UUID.eq(STROOM_USER.UUID))
                        .where(ownerCondition)
                        .and(exprCondition)
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit)
                        .fetch())
                .stream()
                .map(this::mapRecordToApiKey)
                .collect(ResultPage.collector(pageRequest));

        LOGGER.debug(() -> LogUtil.message("Returning {} results", resultPage.size()));
        return resultPage;
    }

    @Override
    public List<HashedApiKey> fetchValidApiKeysByPrefix(final String prefix) {
        Objects.requireNonNull(prefix);

        final long nowMs = Instant.now().toEpochMilli();
        // Prefix is not unique, so we may get a few keys back, however in most cases it will be one.
        // In tests creating 10mil keys, there were only 50 odd prefixes clashes.
        return JooqUtil.contextResult(securityDbConnProvider, context ->
                        context.selectFrom(API_KEY)
                                .where(API_KEY.API_KEY_PREFIX.eq(prefix))
                                .and(API_KEY.ENABLED.isTrue())
                                .and(DSL.or(
                                        API_KEY.EXPIRES_ON_MS.isNull(),
                                        API_KEY.EXPIRES_ON_MS.greaterThan(nowMs)))
                                .fetch())
                .map(this::mapRecordToApiKey);
    }

    @Override
    public HashedApiKey create(final CreateHashedApiKeyRequest createHashedApiKeyRequest,
                               final HashedApiKeyParts hashedApiKeyParts) throws DuplicateApiKeyException {

        Objects.requireNonNull(createHashedApiKeyRequest);
        Objects.requireNonNull(hashedApiKeyParts);
        final String userIdentityForAudit = securityContext.getUserIdentityForAudit();
        final long nowMs = Instant.now().toEpochMilli();

        final ApiKeyRecord apiKeyRecord;
        try {
            apiKeyRecord = JooqUtil.contextResult(securityDbConnProvider, context -> context
                    .insertInto(API_KEY)
                    .columns(API_KEY.VERSION,
                            API_KEY.CREATE_TIME_MS,
                            API_KEY.CREATE_USER,
                            API_KEY.UPDATE_TIME_MS,
                            API_KEY.UPDATE_USER,
                            API_KEY.FK_OWNER_UUID,
                            API_KEY.API_KEY_HASH,
                            API_KEY.HASH_ALGORITHM,
                            API_KEY.API_KEY_PREFIX,
                            API_KEY.EXPIRES_ON_MS,
                            API_KEY.NAME,
                            API_KEY.COMMENTS,
                            API_KEY.ENABLED)
                    .values(INITIAL_VERSION,
                            nowMs,
                            userIdentityForAudit,
                            nowMs,
                            userIdentityForAudit,
                            Objects.requireNonNull(createHashedApiKeyRequest.getOwner().getUuid()),
                            hashedApiKeyParts.apiKeyHash(),
                            createHashedApiKeyRequest.getHashAlgorithm().getPrimitiveValue(),
                            hashedApiKeyParts.apiKeyPrefix(),
                            createHashedApiKeyRequest.getExpireTimeMs(),
                            createHashedApiKeyRequest.getName(),
                            createHashedApiKeyRequest.getComments(),
                            createHashedApiKeyRequest.getEnabled())
                    .returning(API_KEY.ID)
                    .fetchOne()
            );
        } catch (final DataAccessException e) {
            final Throwable rootCause = ExceptionUtils.getRootCause(e);
            if (rootCause instanceof final SQLIntegrityConstraintViolationException constraintException) {
                LOGGER.debug(constraintException.getMessage());
                if (constraintException.getErrorCode() == MysqlErrorNumbers.ER_DUP_ENTRY) {
                    final String msg = constraintException.getMessage();
                    if (msg.contains("api_key_hash_idx")) {
                        throw new DuplicateApiKeyException(
                                "Duplicate API key hash and prefix value to an existing key.", e);
                    } else if (msg.contains("api_key_owner_name_idx")) {
                        throw new RuntimeException("Duplicate API key name '"
                                                   + createHashedApiKeyRequest.getName() + "' for owner "
                                                   + createHashedApiKeyRequest.getOwner(), e);
                    }
                }
            }
            throw e;
        }
        final Integer id = apiKeyRecord.get(API_KEY.ID);
        // Re-fetch so we know we have what is in the DB.
        return fetch(id)
                .orElseThrow(() -> new RuntimeException("No API key found for ID " + id));
    }

    @Override
    public Optional<HashedApiKey> fetch(final int id) {
        return genericDao.fetch(id);
    }


    @Override
    public HashedApiKey update(final HashedApiKey apiKey) {
        try {
            return genericDao.update(apiKey);
        } catch (final DataAccessException e) {
            final Throwable rootCause = ExceptionUtils.getRootCause(e);
            if (rootCause instanceof SQLIntegrityConstraintViolationException &&
                rootCause.getMessage().contains(API_KEY.NAME.getName())) {
                throw new RuntimeException(LogUtil.message(
                        "An API key already exists for user '{}' with API Key Name '{}'. " +
                        "A user's API keys must all have unique names.",
                        apiKey.getOwner().toInfoString(), apiKey.getName()));
            } else {
                throw e;
            }
        }
    }

    @Override
    public boolean delete(final int id) {
        return genericDao.delete(id);
    }

    int deleteByOwner(final DSLContext dslContext, final String userUuid) {
        Objects.requireNonNull(userUuid);

        final int delCount = dslContext.deleteFrom(API_KEY)
                .where(API_KEY.FK_OWNER_UUID.eq(userUuid))
                .execute();
        LOGGER.debug(() -> LogUtil.message("Deleted {} {} records for userUuid {}",
                delCount, API_KEY.getName(), userUuid));
        return delCount;
    }

    @Override
    public int delete(final Collection<Integer> ids) {
        if (NullSafe.hasItems(ids)) {
            return JooqUtil.contextResult(securityDbConnProvider, dslContext ->
                    dslContext.deleteFrom(API_KEY)
                            .where(API_KEY.ID.in(ids))
                            .execute());
        } else {
            return 0;
        }
    }

    private ApiKeyRecord mapApiKeyToRecord(final HashedApiKey apiKey, final ApiKeyRecord record) {
        record.from(apiKey);
        record.set(API_KEY.ID, apiKey.getId());
        record.set(API_KEY.VERSION, apiKey.getVersion());
        record.set(API_KEY.CREATE_TIME_MS, apiKey.getCreateTimeMs());
        record.set(API_KEY.CREATE_USER, apiKey.getCreateUser());
        record.set(API_KEY.UPDATE_TIME_MS, apiKey.getUpdateTimeMs());
        record.set(API_KEY.UPDATE_USER, apiKey.getUpdateUser());
        record.set(API_KEY.FK_OWNER_UUID, NullSafe.get(apiKey.getOwner(), UserRef::getUuid));
        record.set(API_KEY.API_KEY_HASH, apiKey.getApiKeyHash());
        record.set(API_KEY.API_KEY_PREFIX, apiKey.getApiKeyPrefix());
        record.set(API_KEY.EXPIRES_ON_MS, apiKey.getExpireTimeMs());
        record.set(API_KEY.NAME, apiKey.getName());
        record.set(API_KEY.COMMENTS, apiKey.getComments());
        record.set(API_KEY.ENABLED, apiKey.getEnabled());
        record.set(API_KEY.HASH_ALGORITHM, apiKey.getHashAlgorithm().getPrimitiveValue());
        return record;
    }

    private HashedApiKey mapRecordToApiKey(final Record record) {
        final String ownerUuid = record.get(API_KEY.FK_OWNER_UUID);
        final User owner = userCache.getByUuid(ownerUuid)
                .orElseThrow(() -> new RuntimeException(LogUtil.message(
                        "User with uuid {} not found", ownerUuid)));

        return HashedApiKey.builder()
                .withId(record.get(API_KEY.ID))
                .withVersion(record.get(API_KEY.VERSION))
                .withCreateTimeMs(record.get(API_KEY.CREATE_TIME_MS))
                .withCreateUser(record.get(API_KEY.CREATE_USER))
                .withUpdateTimeMs(record.get(API_KEY.UPDATE_TIME_MS))
                .withUpdateUser(record.get(API_KEY.UPDATE_USER))
                .withOwner(owner.asRef())
                .withApiKeyHash(record.get(API_KEY.API_KEY_HASH))
                .withApiKeyPrefix(record.get(API_KEY.API_KEY_PREFIX))
                .withExpireTimeMs(record.get(API_KEY.EXPIRES_ON_MS))
                .withName(record.get(API_KEY.NAME))
                .withComments(record.get(API_KEY.COMMENTS))
                .withEnabled(record.get(API_KEY.ENABLED))
                .withHashAlgorithm(HashAlgorithm.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                        record.get(API_KEY.HASH_ALGORITHM)))
                .build();
    }
}
