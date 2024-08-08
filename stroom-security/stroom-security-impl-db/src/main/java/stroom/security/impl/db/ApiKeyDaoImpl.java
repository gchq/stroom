/*
 * Copyright 2024 Crown Copyright
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

import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.security.api.SecurityContext;
import stroom.security.impl.HashedApiKeyParts;
import stroom.security.impl.UserCache;
import stroom.security.impl.apikey.ApiKeyDao;
import stroom.security.impl.apikey.ApiKeyService.DuplicateHashException;
import stroom.security.impl.apikey.ApiKeyService.DuplicatePrefixException;
import stroom.security.impl.db.jooq.tables.records.ApiKeyRecord;
import stroom.security.shared.ApiKeyResultPage;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.HashedApiKey;
import stroom.util.NullSafe;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.UserName;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static stroom.security.impl.db.jooq.Tables.API_KEY;
import static stroom.security.impl.db.jooq.Tables.STROOM_USER;

@Singleton
public class ApiKeyDaoImpl implements ApiKeyDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApiKeyDaoImpl.class);

    // We need a db field to mirror the Owner column in the UI so we can sort on it
    private static final Name OWNER_UI_VALUE_JOOQ_FIELD_NAME = DSL.name("owner_ui_value");
    private static final Field<String> OWNER_UI_VALUE_JOOQ_FIELD = DSL.field(
            DSL.coalesce(STROOM_USER.DISPLAY_NAME, STROOM_USER.NAME).as(OWNER_UI_VALUE_JOOQ_FIELD_NAME));

    private static final Map<CIKey, Field<?>> FIELD_MAP = CIKey.mapOf(
            FindApiKeyCriteria.FIELD_NAME, API_KEY.NAME,
            FindApiKeyCriteria.FIELD_PREFIX, API_KEY.API_KEY_PREFIX,
            FindApiKeyCriteria.FIELD_OWNER, OWNER_UI_VALUE_JOOQ_FIELD,
            FindApiKeyCriteria.FIELD_COMMENTS, API_KEY.COMMENTS,
            FindApiKeyCriteria.FIELD_EXPIRE_TIME, API_KEY.EXPIRES_ON_MS,
            FindApiKeyCriteria.FIELD_STATE, API_KEY.ENABLED);
    public static final int INITIAL_VERSION = 1;

    private final SecurityDbConnProvider securityDbConnProvider;
    private final GenericDao<ApiKeyRecord, HashedApiKey, Integer> genericDao;
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
    public ApiKeyResultPage find(final FindApiKeyCriteria criteria) {

        final Condition ownerCondition = NullSafe.getOrElseGet(
                criteria.getOwner(),
                owner -> API_KEY.FK_OWNER_UUID.eq(owner.getUuid()),
                DSL::trueCondition);

        final String fullyQualifyFilterInput = QuickFilterPredicateFactory.fullyQualifyInput(
                criteria.getQuickFilterInput(),
                ApiKeyDao.FILTER_FIELD_MAPPERS);

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(
                FIELD_MAP,
                criteria,
                API_KEY.EXPIRES_ON_MS);

        final ApiKeyResultPage resultPage = QuickFilterPredicateFactory.filterStream(
                        criteria.getQuickFilterInput(),
                        FILTER_FIELD_MAPPERS,
                        JooqUtil.contextResult(securityDbConnProvider, context -> context
                                        .select(API_KEY.asterisk(), OWNER_UI_VALUE_JOOQ_FIELD)
                                        .from(API_KEY)
                                        .innerJoin(STROOM_USER).on(API_KEY.FK_OWNER_UUID.eq(STROOM_USER.UUID))
                                        .where(ownerCondition)
                                        .orderBy(orderFields)
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
    public Optional<HashedApiKey> fetchValidApiKeyByHash(final String hash) {
        Objects.requireNonNull(hash);

        final long nowMs = Instant.now().toEpochMilli();
        final Optional<ApiKeyRecord> result = JooqUtil.contextResult(securityDbConnProvider, context ->
                context.selectFrom(API_KEY)
                        .where(API_KEY.API_KEY_HASH.eq(hash))
                        .and(API_KEY.ENABLED.isTrue())
                        .and(DSL.or(
                                API_KEY.EXPIRES_ON_MS.isNull(),
                                API_KEY.EXPIRES_ON_MS.greaterThan(nowMs)))
                        .fetchOptional());
        return result.map(this::mapRecordToApiKey);
    }

    @Override
    public HashedApiKey create(final CreateHashedApiKeyRequest createHashedApiKeyRequest,
                               final HashedApiKeyParts hashedApiKeyParts)
            throws DuplicateHashException, DuplicatePrefixException {
        Objects.requireNonNull(createHashedApiKeyRequest);
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
                                    Objects.requireNonNull(createHashedApiKeyRequest.getOwner().getUuid()),
                                    hashedApiKeyParts.apiKeyHash(),
                                    hashedApiKeyParts.apiKeyPrefix(),
                                    createHashedApiKeyRequest.getExpireTimeMs(),
                                    createHashedApiKeyRequest.getName(),
                                    createHashedApiKeyRequest.getComments(),
                                    createHashedApiKeyRequest.getEnabled())
                            .returning(API_KEY.ID)
                            .fetchOne()
            );
        } catch (DataAccessException e) {
            final Throwable rootCause = ExceptionUtils.getRootCause(e);
            if (rootCause instanceof SQLIntegrityConstraintViolationException) {
                if (rootCause.getMessage().contains(API_KEY.API_KEY_HASH.getName())) {
                    throw new DuplicateHashException("Duplicate API key hash value.", e);
                } else if (rootCause.getMessage().contains(API_KEY.API_KEY_PREFIX.getName())) {
                    throw new DuplicatePrefixException("Duplicate API prefix.", e);
                } else if (rootCause.getMessage().contains(API_KEY.NAME.getName())) {
                    throw new RuntimeException("Duplicate API key name.", e);
                } else {
                    throw e;
                }
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
    public Optional<HashedApiKey> fetch(final int id) {
        return genericDao.fetch(id);
    }

    @Override
    public HashedApiKey update(final HashedApiKey apiKey) {
        try {
            return genericDao.update(apiKey);
        } catch (DataAccessException e) {
            final Throwable rootCause = ExceptionUtils.getRootCause(e);
            if (rootCause instanceof SQLIntegrityConstraintViolationException &&
                    rootCause.getMessage().contains(API_KEY.NAME.getName())) {
                throw new RuntimeException(LogUtil.message(
                        "An API key already exists for user '{}' with API Key Name '{}'. " +
                                "A user's API keys must all have unique names.",
                        apiKey.getOwner().getUserIdentityForAudit(), apiKey.getName()));
            } else {
                throw e;
            }
        }
    }

    @Override
    public boolean delete(final int id) {
        return genericDao.delete(id);
    }

    @Override
    public int delete(final Collection<Integer> ids) {
        if (NullSafe.hasItems(ids)) {
            final Integer count = JooqUtil.contextResult(securityDbConnProvider, dslContext ->
                    dslContext.deleteFrom(API_KEY)
                            .where(API_KEY.ID.in(ids))
                            .execute());
            return count;
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
        record.set(API_KEY.FK_OWNER_UUID, NullSafe.get(apiKey.getOwner(), UserName::getUuid));
        record.set(API_KEY.API_KEY_HASH, apiKey.getApiKeyHash());
        record.set(API_KEY.API_KEY_PREFIX, apiKey.getApiKeyPrefix());
        record.set(API_KEY.EXPIRES_ON_MS, apiKey.getExpireTimeMs());
        record.set(API_KEY.NAME, apiKey.getName());
        record.set(API_KEY.COMMENTS, apiKey.getComments());
        record.set(API_KEY.ENABLED, apiKey.getEnabled());
        return record;
    }

    private HashedApiKey mapRecordToApiKey(final Record record) {
        final String ownerUuid = record.get(API_KEY.FK_OWNER_UUID);
        final UserName owner = userCache.getByUuid(ownerUuid)
                .orElseThrow(() -> new RuntimeException(LogUtil.message(
                        "User with uuid {} not found", ownerUuid)));

        return HashedApiKey.builder()
                .withId(record.get(API_KEY.ID))
                .withVersion(record.get(API_KEY.VERSION))
                .withCreateTimeMs(record.get(API_KEY.CREATE_TIME_MS))
                .withCreateUser(record.get(API_KEY.CREATE_USER))
                .withUpdateTimeMs(record.get(API_KEY.UPDATE_TIME_MS))
                .withUpdateUser(record.get(API_KEY.UPDATE_USER))
                .withOwner(owner)
                .withApiKeyHash(record.get(API_KEY.API_KEY_HASH))
                .withApiKeyPrefix(record.get(API_KEY.API_KEY_PREFIX))
                .withExpireTimeMs(record.get(API_KEY.EXPIRES_ON_MS))
                .withName(record.get(API_KEY.NAME))
                .withComments(record.get(API_KEY.COMMENTS))
                .withEnabled(record.get(API_KEY.ENABLED))
                .build();
    }
}
