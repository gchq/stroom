/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.security.identity.db;

import stroom.security.identity.account.Account;
import stroom.security.identity.db.jooq.tables.records.TokenRecord;
import stroom.security.identity.token.SearchTokenRequest;
import stroom.security.identity.token.Token;
import stroom.security.identity.token.TokenDao;
import stroom.security.identity.token.TokenResource;
import stroom.security.identity.token.TokenType;
import stroom.security.identity.token.TokenTypeDao;
import stroom.db.util.JooqUtil;
import stroom.util.collections.ResultPageCollector;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record12;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static stroom.security.identity.db.jooq.tables.TokenType.TOKEN_TYPE;

@Singleton
class TokenDaoImpl implements TokenDao {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AccountDaoImpl.class);

    private static final Function<Record, Token> RECORD_TO_TOKEN_MAPPER = record -> {
        final Token token = new Token();
        token.setId(record.get(stroom.security.identity.db.jooq.tables.Token.TOKEN.ID));
        token.setVersion(record.get(stroom.security.identity.db.jooq.tables.Token.TOKEN.VERSION));
        token.setCreateTimeMs(record.get(stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_TIME_MS));
        token.setUpdateTimeMs(record.get(stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_TIME_MS));
        token.setCreateUser(record.get(stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_USER));
        token.setUpdateUser(record.get(stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_USER));
        try {
            token.setUserId(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID));
        } catch (final IllegalArgumentException e) {
            // Ignore
        }
        try {
            token.setUserEmail(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.EMAIL));
        } catch (final IllegalArgumentException e) {
            // Ignore
        }
        try {
            token.setTokenType(record.get(TOKEN_TYPE.TYPE));
        } catch (final IllegalArgumentException e) {
            // Ignore
        }
        token.setData(record.get(stroom.security.identity.db.jooq.tables.Token.TOKEN.DATA));
        token.setExpiresOnMs(record.get(stroom.security.identity.db.jooq.tables.Token.TOKEN.EXPIRES_ON_MS));
        token.setComments(record.get(stroom.security.identity.db.jooq.tables.Token.TOKEN.COMMENTS));
        token.setEnabled(record.get(stroom.security.identity.db.jooq.tables.Token.TOKEN.ENABLED));
        return token;
    };

    private static final BiFunction<Token, TokenRecord, TokenRecord> TOKEN_TO_RECORD_MAPPER = (token, record) -> {
        record.setId(token.getId());
        record.setVersion(token.getVersion());
        record.setCreateTimeMs(token.getCreateTimeMs());
        record.setUpdateTimeMs(token.getUpdateTimeMs());
        record.setCreateUser(token.getCreateUser());
        record.setUpdateUser(token.getUpdateUser());
//        record.setUserEmail(token.get(ACCOUNT.EMAIL));
//        record.setTokenType(token.get(TOKEN_TYPE.TYPE));
        record.setData(token.getData());
        record.setExpiresOnMs(token.getExpiresOnMs());
        record.setComments(token.getComments());
        record.setEnabled(token.isEnabled());
        return record;
    };

    private static final Map<String, Field<?>> FIELD_MAP = Map.ofEntries(
            entry("id", stroom.security.identity.db.jooq.tables.Token.TOKEN.ID),
            entry("version", stroom.security.identity.db.jooq.tables.Token.TOKEN.VERSION),
            entry("createTimeMs", stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_TIME_MS),
            entry("updateTimeMs", stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_TIME_MS),
            entry("createUser", stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_USER),
            entry("updateUser", stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_USER),
            entry("userId", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID),
            entry("userEmail", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.EMAIL),
            entry("tokenType", stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_TOKEN_TYPE_ID),
            entry("data", stroom.security.identity.db.jooq.tables.Token.TOKEN.DATA),
            entry("expiresOnMs", stroom.security.identity.db.jooq.tables.Token.TOKEN.EXPIRES_ON_MS),
            entry("comments", stroom.security.identity.db.jooq.tables.Token.TOKEN.COMMENTS),
            entry("enabled", stroom.security.identity.db.jooq.tables.Token.TOKEN.ENABLED));

    private static final FilterFieldMappers<Token> FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(
                    TokenResource.FIELD_DEF_USER_ID,
                    Token::getUserId),
            FilterFieldMapper.of(
                    TokenResource.FIELD_DEF_USER_EMAIL,
                    Token::getUserEmail),
            FilterFieldMapper.of(
                    TokenResource.FIELD_DEF_COMMENTS,
                    Token::getComments));

    private final AuthDbConnProvider authDbConnProvider;
    private final TokenTypeDao tokenTypeDao;

    @Inject
    TokenDaoImpl(final AuthDbConnProvider authDbConnProvider,
                 final TokenTypeDao tokenTypeDao) {
        this.authDbConnProvider = authDbConnProvider;
        this.tokenTypeDao = tokenTypeDao;
    }

    @Override
    public ResultPage<Token> list() {
        final List<Token> list = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(stroom.security.identity.db.jooq.tables.Token.TOKEN)
                .where(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_TOKEN_TYPE_ID.eq(tokenTypeDao.getTokenTypeId(TokenType.USER.getText().toLowerCase())))
                .orderBy(stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_TIME_MS)
                .fetch()
                .map(RECORD_TO_TOKEN_MAPPER::apply));
        return ResultPage.createUnboundedList(list);
    }

    @Override
    public ResultPage<Token> search(final SearchTokenRequest request) {
        final Condition condition = createCondition(request);

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, request);

        return JooqUtil.contextResult(authDbConnProvider, context -> {
            if (request.getQuickFilter() == null || request.getQuickFilter().length() == 0) {
                final List<Token> list = context
                        .select(
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.ID,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.VERSION,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_TIME_MS,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_TIME_MS,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_USER,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_USER,
                                stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID,
                                TOKEN_TYPE.TYPE,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.DATA,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.EXPIRES_ON_MS,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.COMMENTS,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.ENABLED)
                        .from(stroom.security.identity.db.jooq.tables.Token.TOKEN
                                .join(TOKEN_TYPE)
                                .on(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_TOKEN_TYPE_ID.eq(TOKEN_TYPE.ID))
                                .join(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                                .on(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_ACCOUNT_ID.eq(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ID)))
                        .where(condition)
                        .orderBy(orderFields)
                        .offset(JooqUtil.getOffset(request.getPageRequest()))
                        .limit(JooqUtil.getLimit(request.getPageRequest(), true))
                        .fetch()
                        .map(RECORD_TO_TOKEN_MAPPER::apply);

                // Finally we need to get the number of tokens so we can calculate the total number of pages
                final int count = context
                        .selectCount()
                        .from(stroom.security.identity.db.jooq.tables.Token.TOKEN
                                .join(TOKEN_TYPE)
                                .on(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_TOKEN_TYPE_ID.eq(TOKEN_TYPE.ID))
                                .join(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                                .on(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_ACCOUNT_ID.eq(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ID)))
                        .where(condition)
                        .fetchOptional()
                        .map(Record1::value1)
                        .orElse(0);

                return ResultPage.createCriterialBasedList(list, request, (long) count);

            } else {
                // Create the predicate for the current filter value
                final Predicate<Token> fuzzyMatchPredicate = QuickFilterPredicateFactory.createFuzzyMatchPredicate(
                        request.getQuickFilter(), FIELD_MAPPERS);

                try (final Stream<Record12<Integer, Integer, Long, Long, String, String, String, String, String, Long, String, Boolean>> stream = context
                        .select(
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.ID,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.VERSION,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_TIME_MS,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_TIME_MS,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_USER,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_USER,
                                stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID,
                                TOKEN_TYPE.TYPE,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.DATA,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.EXPIRES_ON_MS,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.COMMENTS,
                                stroom.security.identity.db.jooq.tables.Token.TOKEN.ENABLED)
                        .from(stroom.security.identity.db.jooq.tables.Token.TOKEN
                                .join(TOKEN_TYPE)
                                .on(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_TOKEN_TYPE_ID.eq(TOKEN_TYPE.ID))
                                .join(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                                .on(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_ACCOUNT_ID.eq(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ID)))
                        .where(condition)
                        .orderBy(orderFields)
                        .stream()) {

                    return stream
                            .map(RECORD_TO_TOKEN_MAPPER)
                            .filter(fuzzyMatchPredicate)
                            .collect(ResultPageCollector.create(request.getPageRequest()))
                            .build();
                }
            }
        });
    }

    @Override
    public Token create(final int accountId, final Token token) {
        final String tokenType = token.getTokenType().toLowerCase();
        final int tokenTypeId = tokenTypeDao.getTokenTypeId(tokenType);

        return JooqUtil.contextResult(authDbConnProvider, context -> {
            LOGGER.debug(LambdaLogUtil.message("Creating a {}", stroom.security.identity.db.jooq.tables.Token.TOKEN.getName()));
            final TokenRecord record = TOKEN_TO_RECORD_MAPPER.apply(token, context.newRecord(stroom.security.identity.db.jooq.tables.Token.TOKEN));
            record.setFkAccountId(accountId);
            record.setFkTokenTypeId(tokenTypeId);
            record.store();

            final Token newToken = RECORD_TO_TOKEN_MAPPER.apply(record);
            newToken.setUserEmail(token.getUserEmail());
            newToken.setTokenType(token.getTokenType());
            return newToken;
        });
    }

//    /**
//     * Default ordering is by ISSUED_ON date, in descending order so the most recent tokens are shown first.
//     * If orderBy is specified but orderDirection is not this will default to ascending.
//     * <p>
//     * The user must have the 'Manage Users' permission to call this.
//     */
//    @Override
//    public SearchResponse searchTokens(SearchRequest searchRequest) {
//        // Create some vars to allow the rest of this method to be more succinct.
//        int page = searchRequest.getPage();
//        int limit = searchRequest.getLimit();
//        String orderBy = searchRequest.getOrderBy();
//        String orderDirection = searchRequest.getOrderDirection();
//        Map<String, String> filters = searchRequest.getFilters();
//
//        // Use a default if there's no order direction specified in the request
//        if (orderDirection == null) {
//            orderDirection = "asc";
//        }
//
//        // Special cases
//        SortField<?>[] orderByField;
//        if (orderBy != null && orderBy.equals("userEmail")) {
//            // Why is this a special case? Because the property on the target table is 'email' but the param is 'user_email'
//            // 'user_email' is a clearer param
//            if (orderDirection.equals("asc")) {
//                orderByField = new SortField[]{ACCOUNT.USER_ID.asc()};
//            } else {
//                orderByField = new SortField[]{ACCOUNT.USER_ID.desc()};
//            }
//        } else {
//            orderByField = getOrderBy(orderBy, orderDirection);
//        }
//
//        final List<Condition> conditions = getConditions(filters);
//        final int offset = limit * page;
//
//        return JooqUtil.contextResult(authDbConnProvider, context -> {
//            final List<Token> tokens = context
//                    .select(
//                            TOKEN.ID,
//                            TOKEN.VERSION,
//                            TOKEN.CREATE_TIME_MS,
//                            TOKEN.UPDATE_TIME_MS,
//                            TOKEN.CREATE_USER,
//                            TOKEN.UPDATE_USER,
//                            ACCOUNT.USER_ID,
//                            TOKEN_TYPE.TYPE,
//                            TOKEN.DATA,
//                            TOKEN.EXPIRES_ON_MS,
//                            TOKEN.COMMENTS,
//                            TOKEN.ENABLED)
//                    .from(TOKEN
//                            .join(TOKEN_TYPE)
//                            .on(TOKEN.FK_TOKEN_TYPE_ID.eq(TOKEN_TYPE.ID))
//                            .join(ACCOUNT)
//                            .on(TOKEN.FK_ACCOUNT_ID.eq(ACCOUNT.ID)))
//                    .where(conditions)
//                    .orderBy(orderByField)
//                    .limit(limit)
//                    .offset(offset)
//                    .fetch()
//                    .map(RECORD_TO_TOKEN_MAPPER::apply);
//
//
//            // Finally we need to get the number of tokens so we can calculate the total number of pages
//            final int count = context
//                    .selectCount()
//                    .from(TOKEN
//                            .join(TOKEN_TYPE)
//                            .on(TOKEN.FK_TOKEN_TYPE_ID.eq(TOKEN_TYPE.ID))
//                            .join(ACCOUNT)
//                            .on(TOKEN.FK_ACCOUNT_ID.eq(ACCOUNT.ID)))
//                    .where(conditions)
//                    .fetchOptional()
//                    .map(Record1::value1)
//                    .orElse(0);
//
//            // We need to round up so we always have enough pages even if there's a remainder.
//            int pages = (int) Math.ceil((double) count / limit);
//
//            final SearchResponse searchResponse = new SearchResponse();
//            searchResponse.setTokens(tokens);
//            searchResponse.setTotalPages(pages);
//            return searchResponse;
//        });
//    }

    @Override
    public int deleteAllTokensExceptAdmins() {
        final Integer adminUserId = JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ID).from(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                .where(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID.eq("admin"))
                .fetchOne()
                .map(r -> r.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ID)));

        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .deleteFrom(stroom.security.identity.db.jooq.tables.Token.TOKEN)
                .where(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_ACCOUNT_ID.ne(adminUserId))
                .execute());
    }

    @Override
    public int deleteTokenById(int tokenId) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context.deleteFrom(stroom.security.identity.db.jooq.tables.Token.TOKEN).where(stroom.security.identity.db.jooq.tables.Token.TOKEN.ID.eq(tokenId)).execute());
    }

    @Override
    public int deleteTokenByTokenString(String token) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context.deleteFrom(stroom.security.identity.db.jooq.tables.Token.TOKEN).where(stroom.security.identity.db.jooq.tables.Token.TOKEN.DATA.eq(token)).execute());
    }

    @Override
    public List<Token> getTokensForAccount(final int accountId) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.ID,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.VERSION,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_TIME_MS,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_TIME_MS,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_USER,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_USER,
                        stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID,
                        stroom.security.identity.db.jooq.tables.Account.ACCOUNT.EMAIL,
                        TOKEN_TYPE.TYPE,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.DATA,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.EXPIRES_ON_MS,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.COMMENTS,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.ENABLED)
                .from(stroom.security.identity.db.jooq.tables.Token.TOKEN)
                .join(TOKEN_TYPE).on(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_TOKEN_TYPE_ID.eq(TOKEN_TYPE.ID))
                .join(stroom.security.identity.db.jooq.tables.Account.ACCOUNT).on(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_ACCOUNT_ID.eq(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ID))
                .where(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_ACCOUNT_ID.eq(accountId))
                .orderBy(stroom.security.identity.db.jooq.tables.Token.TOKEN.ID)
                .fetch()
                .map(RECORD_TO_TOKEN_MAPPER::apply));
    }

    @Override
    public Optional<Token> readById(int tokenId) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.ID,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.VERSION,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_TIME_MS,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_TIME_MS,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_USER,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_USER,
                        stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID,
                        stroom.security.identity.db.jooq.tables.Account.ACCOUNT.EMAIL,
                        TOKEN_TYPE.TYPE,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.DATA,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.EXPIRES_ON_MS,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.COMMENTS,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.ENABLED)
                .from(stroom.security.identity.db.jooq.tables.Token.TOKEN)
                .join(TOKEN_TYPE).on(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_TOKEN_TYPE_ID.eq(TOKEN_TYPE.ID))
                .join(stroom.security.identity.db.jooq.tables.Account.ACCOUNT).on(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_ACCOUNT_ID.eq(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ID))
                .where(stroom.security.identity.db.jooq.tables.Token.TOKEN.ID.eq(tokenId))
                .fetchOptional()
                .map(RECORD_TO_TOKEN_MAPPER));
    }


    @Override
    public Optional<Token> readByToken(String token) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.ID,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.VERSION,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_TIME_MS,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_TIME_MS,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.CREATE_USER,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_USER,
                        stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID,
                        stroom.security.identity.db.jooq.tables.Account.ACCOUNT.EMAIL,
                        TOKEN_TYPE.TYPE,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.DATA,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.EXPIRES_ON_MS,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.COMMENTS,
                        stroom.security.identity.db.jooq.tables.Token.TOKEN.ENABLED)
                .from(stroom.security.identity.db.jooq.tables.Token.TOKEN)
                .join(TOKEN_TYPE).on(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_TOKEN_TYPE_ID.eq(TOKEN_TYPE.ID))
                .join(stroom.security.identity.db.jooq.tables.Account.ACCOUNT).on(stroom.security.identity.db.jooq.tables.Token.TOKEN.FK_ACCOUNT_ID.eq(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ID))
                .where(stroom.security.identity.db.jooq.tables.Token.TOKEN.DATA.eq(token))
                .fetchOptional()
                .map(RECORD_TO_TOKEN_MAPPER));
    }


    @Override
    public int enableOrDisableToken(int tokenId, boolean enabled, Account updatingAccount) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .update(stroom.security.identity.db.jooq.tables.Token.TOKEN)
                .set(stroom.security.identity.db.jooq.tables.Token.TOKEN.ENABLED, enabled)
                .set(stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_TIME_MS, System.currentTimeMillis())
                .set(stroom.security.identity.db.jooq.tables.Token.TOKEN.UPDATE_USER, updatingAccount.getUserId())
                .where(stroom.security.identity.db.jooq.tables.Token.TOKEN.ID.eq((tokenId)))
                .execute());
    }

    private Condition createCondition(final SearchTokenRequest request) {
        Condition condition = null;
//        if (request.getQuickFilter() != null) {
//            condition = ACCOUNT.USER_ID.contains(request.getQuickFilter());
//        }
        return condition;
    }

//    /**
//     * How do we match on dates? Must match exactly? Must match part of the date? What if the given date is invalid?
//     * Is this what a user would want? Maybe they want greater than or less than? This would need additional UI
//     * For now we can't sensible implement anything unless we have a better idea of requirements.
//     */
//    private static List<Condition> getConditions(Map<String, String> filters) {
//        // We need to set up conditions
//        List<Condition> conditions = new ArrayList<>();
//        final String unsupportedFilterMessage = "Unsupported filter: ";
//        final String unknownFilterMessage = "Unknown filter: ";
//        if (filters != null) {
//            for (String key : filters.keySet()) {
//                Condition condition;
//                switch (key) {
//                    case "enabled":
//                        condition = TOKEN.ENABLED.eq(Boolean.valueOf(filters.get(key)));
//                        break;
//                    case "expiresOn":
//                    case "issuedOn":
//                    case "updatedOn":
//                    case "userId":
//                        condition = ACCOUNT.USER_ID.contains(filters.get(key));
//                    case "userEmail":
//                        condition = ACCOUNT.EMAIL.contains(filters.get(key));
//                        break;
//                    case "issuedByUser":
//                        condition = TOKEN.CREATE_USER.eq(filters.get(key));
//                        break;
//                    case "token":
//                        // It didn't initially make sense that one might want to filter on token, because it's encrypted.
//                        // But if someone has a token copy/pasting some or all of it into the search might be the
//                        // fastest way to find the token.
//                        condition = TOKEN.DATA.contains(filters.get(key));
//                        break;
//                    case "tokenType":
//                        condition = TOKEN_TYPE.TYPE.eq(filters.get(key).toLowerCase());
//                        break;
//                    case "updatedByUser":
//                        condition = TOKEN.UPDATE_USER.eq(filters.get(key));
//                        break;
//                    default:
//                        throw new UnsupportedFilterException(unknownFilterMessage + key);
//                }
//
//                conditions.add(condition);
//            }
//        }
//        return conditions;
//    }
//
//    static SortField<?>[] getOrderBy(String orderBy, String orderDirection) {
//        // We might be ordering by TOKEN or ACCOUNT or TOKEN_TYPE - we join and select on all
//        SortField<?> orderByField = TOKEN.CREATE_TIME_MS.desc();
//        if (orderBy != null) {
//            switch (orderBy) {
//                case "userId":
//                    orderByField = orderDirection.equals("asc") ? ACCOUNT.USER_ID.asc() : ACCOUNT.USER_ID.desc();
//                case "userEmail":
//                    orderByField = orderDirection.equals("asc") ? ACCOUNT.EMAIL.asc() : ACCOUNT.EMAIL.desc();
//                    break;
//                case "enabled":
//                    orderByField = orderDirection.equals("asc") ? TOKEN.ENABLED.asc() : TOKEN.ENABLED.desc();
//                    break;
//                case "tokenType":
//                    orderByField = orderDirection.equals("asc") ? TOKEN_TYPE.TYPE.asc() : TOKEN_TYPE.TYPE.desc();
//                    break;
//                case "issuedOn":
//                default:
//                    orderByField = orderDirection.equals("asc") ? TOKEN.CREATE_TIME_MS.asc() : TOKEN.CREATE_TIME_MS.desc();
//            }
//        }
//        return new SortField[]{orderByField};
//    }

}
