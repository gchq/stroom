package stroom.security.impl.db;

import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.security.impl.ApiKeyDao;
import stroom.security.impl.db.jooq.tables.records.ApiKeyRecord;
import stroom.security.shared.ApiKey;
import stroom.security.shared.ApiKeyResultPage;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.UserNameProvider;
import stroom.util.NullSafe;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.time.Instant;
import java.util.Map;
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

    private final SecurityDbConnProvider securityDbConnProvider;
    private final GenericDao<ApiKeyRecord, ApiKey, Integer> genericDao;
    private final UserNameProvider userNameProvider;

    @Inject
    public ApiKeyDaoImpl(final UserNameProvider userNameProvider,
                         final SecurityDbConnProvider securityDbConnProvider) {
        this.securityDbConnProvider = securityDbConnProvider;
        this.genericDao = new GenericDao<>(
                securityDbConnProvider,
                API_KEY,
                API_KEY.ID,
                this::mapApiKeyToRecord,
                this::mapRecordToApiKey);
        this.userNameProvider = userNameProvider;
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
    public Optional<String> fetchVerifiedIdentity(final String apiKey) {
        if (NullSafe.isBlankString(apiKey)) {
            return Optional.empty();
        } else {
            final long nowMs = Instant.now().toEpochMilli();
            return JooqUtil.contextResult(securityDbConnProvider, context -> context
                    .select(API_KEY.FK_OWNER_UUID)
                    .from(API_KEY)
                    .where(API_KEY.API_KEY_.eq(apiKey.trim()))
                    .and(API_KEY.ENABLED.isTrue())
                    .and(API_KEY.EXPIRES_ON_MS.greaterThan(nowMs))
                    .fetchOptional(API_KEY.FK_OWNER_UUID));
        }
    }

    @Override
    public ApiKey create(final ApiKey apiKey) {
        return genericDao.create(apiKey);
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
        record.set(API_KEY.API_KEY_, apiKey.getApiKey());
        record.set(API_KEY.EXPIRES_ON_MS, apiKey.getExpireTimeMs());
        record.set(API_KEY.NAME, apiKey.getName());
        record.set(API_KEY.COMMENTS, apiKey.getComments());
        record.set(API_KEY.ENABLED, apiKey.getEnabled());
        return record;
    }

    private ApiKey mapRecordToApiKey(final Record record) {
        final String ownerUuid = record.get(API_KEY.FK_OWNER_UUID);
        final UserName owner = userNameProvider.getByUuid(ownerUuid)
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
                .withApiKey(record.get(API_KEY.API_KEY_))
                .withExpireTimeMs(record.get(API_KEY.EXPIRES_ON_MS))
                .withName(record.get(API_KEY.NAME))
                .withComments(record.get(API_KEY.COMMENTS))
                .withEnabled(record.get(API_KEY.ENABLED))
                .build();
    }
}
