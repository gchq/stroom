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

package stroom.authentication.impl.db;

import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SortField;
import stroom.authentication.account.Account;
import stroom.authentication.account.AccountDao;
import stroom.authentication.config.TokenConfig;
import stroom.authentication.exceptions.NoSuchUserException;
import stroom.authentication.exceptions.UnsupportedFilterException;
import stroom.authentication.impl.db.jooq.tables.records.TokenRecord;
import stroom.authentication.token.SearchRequest;
import stroom.authentication.token.SearchResponse;
import stroom.authentication.token.Token;
import stroom.authentication.token.TokenBuilder;
import stroom.authentication.token.TokenBuilderFactory;
import stroom.authentication.token.TokenDao;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static stroom.authentication.impl.db.jooq.tables.Account.ACCOUNT;
import static stroom.authentication.impl.db.jooq.tables.Token.TOKEN;
import static stroom.authentication.impl.db.jooq.tables.TokenType.TOKEN_TYPE;

@Singleton
class TokenDaoImpl implements TokenDao {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AccountDaoImpl.class);

    private static final Function<Record, Token> RECORD_TO_TOKEN_MAPPER = record -> {
        final Token token = new Token();
        token.setId(record.get(TOKEN.ID));
        token.setVersion(record.get(TOKEN.VERSION));
        token.setCreateTimeMs(record.get(TOKEN.CREATE_TIME_MS));
        token.setUpdateTimeMs(record.get(TOKEN.UPDATE_TIME_MS));
        token.setCreateUser(record.get(TOKEN.CREATE_USER));
        token.setUpdateUser(record.get(TOKEN.UPDATE_USER));
        try {
            token.setUserEmail(record.get(ACCOUNT.EMAIL));
        } catch (final IllegalArgumentException e) {
            // Ignore
        }
        try {
            token.setTokenType(record.get(TOKEN_TYPE.TYPE));
        } catch (final IllegalArgumentException e) {
            // Ignore
        }
        token.setData(record.get(TOKEN.DATA));
        token.setExpiresOnMs(record.get(TOKEN.EXPIRES_ON_MS));
        token.setComments(record.get(TOKEN.COMMENTS));
        token.setEnabled(record.get(TOKEN.ENABLED));
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

    private final TokenConfig config;
    private final AuthDbConnProvider authDbConnProvider;
    private final TokenBuilderFactory tokenBuilderFactory;
    private final AccountDao accountDao;

    @Inject
    TokenDaoImpl(final TokenConfig config,
                 final AuthDbConnProvider authDbConnProvider,
                 final TokenBuilderFactory tokenBuilderFactory,
                 final AccountDao accountDao) {
        this.config = config;
        this.authDbConnProvider = authDbConnProvider;
        this.tokenBuilderFactory = tokenBuilderFactory;
        this.accountDao = accountDao;
    }

    @Override
    public Token create(final Token token) {
        final Optional<Integer> optionalAccountId = accountDao.getId(token.getUserEmail());
        final Integer accountId = optionalAccountId.orElseThrow(() ->
                new NoSuchUserException("Cannot find user to associate with this API key!"));

        final String tokenType = token.getTokenType().toLowerCase();
        final Optional<Integer> optionalTokenTypeId = getTokenTypeId(tokenType);
        final Integer tokenTypeId = optionalTokenTypeId.orElseThrow(() ->
                new RuntimeException("Unknown token type: " + tokenType));

        return JooqUtil.contextResult(authDbConnProvider, context -> {
            LOGGER.debug(LambdaLogUtil.message("Creating a {}", TOKEN.getName()));
            final TokenRecord record = TOKEN_TO_RECORD_MAPPER.apply(token, context.newRecord(TOKEN));
            record.setFkAccountId(accountId);
            record.setFkTokenTypeId(tokenTypeId);
            record.store();

            final Token newToken = RECORD_TO_TOKEN_MAPPER.apply(record);
            newToken.setUserEmail(token.getUserEmail());
            newToken.setTokenType(token.getTokenType());
            return newToken;
        });
    }

    @Override
    public SearchResponse searchTokens(SearchRequest searchRequest) {
        // Create some vars to allow the rest of this method to be more succinct.
        int page = searchRequest.getPage();
        int limit = searchRequest.getLimit();
        String orderBy = searchRequest.getOrderBy();
        String orderDirection = searchRequest.getOrderDirection();
        Map<String, String> filters = searchRequest.getFilters();

        // Use a default if there's no order direction specified in the request
        if (orderDirection == null) {
            orderDirection = "asc";
        }

        // Special cases
        SortField<?>[] orderByField;
        if (orderBy != null && orderBy.equals("userEmail")) {
            // Why is this a special case? Because the property on the target table is 'email' but the param is 'user_email'
            // 'user_email' is a clearer param
            if (orderDirection.equals("asc")) {
                orderByField = new SortField[]{ACCOUNT.EMAIL.asc()};
            } else {
                orderByField = new SortField[]{ACCOUNT.EMAIL.desc()};
            }
        } else {
            orderByField = getOrderBy(orderBy, orderDirection);
        }

        final List<Condition> conditions = getConditions(filters);
        final int offset = limit * page;

        return JooqUtil.contextResult(authDbConnProvider, context -> {
            final List<Token> tokens = context
                    .select(
                            TOKEN.ID,
                            TOKEN.VERSION,
                            TOKEN.CREATE_TIME_MS,
                            TOKEN.UPDATE_TIME_MS,
                            TOKEN.CREATE_USER,
                            TOKEN.UPDATE_USER,
                            ACCOUNT.EMAIL,
                            TOKEN_TYPE.TYPE,
                            TOKEN.DATA,
                            TOKEN.EXPIRES_ON_MS,
                            TOKEN.COMMENTS,
                            TOKEN.ENABLED)
                    .from(TOKEN
                            .join(TOKEN_TYPE)
                            .on(TOKEN.FK_TOKEN_TYPE_ID.eq(TOKEN_TYPE.ID))
                            .join(ACCOUNT)
                            .on(TOKEN.FK_ACCOUNT_ID.eq(ACCOUNT.ID)))
                    .where(conditions)
                    .orderBy(orderByField)
                    .limit(limit)
                    .offset(offset)
                    .fetch()
                    .map(RECORD_TO_TOKEN_MAPPER::apply);


            // Finally we need to get the number of tokens so we can calculate the total number of pages
            final int count = context
                    .selectCount()
                    .from(TOKEN
                            .join(TOKEN_TYPE)
                            .on(TOKEN.FK_TOKEN_TYPE_ID.eq(TOKEN_TYPE.ID))
                            .join(ACCOUNT)
                            .on(TOKEN.FK_ACCOUNT_ID.eq(ACCOUNT.ID)))
                    .where(conditions)
                    .fetchOptional()
                    .map(Record1::value1)
                    .orElse(0);

            // We need to round up so we always have enough pages even if there's a remainder.
            int pages = (int) Math.ceil((double) count / limit);

            final SearchResponse searchResponse = new SearchResponse();
            searchResponse.setTokens(tokens);
            searchResponse.setTotalPages(pages);
            return searchResponse;
        });
    }

    @Override
    public String createEmailResetToken(String emailAddress, String clientId) throws NoSuchUserException {
        long timeToExpiryInSeconds = this.config.getMinutesUntilExpirationForEmailResetToken() * 60;

        return createToken(
                Token.TokenType.EMAIL_RESET,
                "authenticationResourceUser",
                Instant.now().plusSeconds(timeToExpiryInSeconds),
                emailAddress,
                clientId,
                true, "Created for password reset")
                .getData();
    }

    private Optional<Integer> getTokenTypeId(final String type) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(TOKEN_TYPE.ID)
                .from(TOKEN_TYPE)
                .where(TOKEN_TYPE.TYPE.eq(type))
                .fetchOptional()
                .map(r -> r.getValue(TOKEN_TYPE.ID)));
    }

//    @Override
//    public Token createIdToken(String idToken, String subject, long expiresOn) {
//        final Optional<Integer> optionalAccountId = accountDao.getId(subject);
//        final Integer accountId = optionalAccountId.orElseThrow(() ->
//                new NoSuchUserException("Cannot find user to associate with this API key!"));
//
//        final Optional<Integer> optionalTokenTypeId = getTokenTypeId(Token.TokenType.USER.getText().toLowerCase());
//        final Integer tokenTypeId = optionalTokenTypeId.orElseThrow(() ->
//                new RuntimeException("Unknown token type: " + Token.TokenType.USER.getText().toLowerCase()));
//
//        return JooqUtil.contextResult(authDbConnProvider, context -> context
//                .insertInto(TOKEN)
//                .set(TOKEN.FK_ACCOUNT_ID, accountId)
//                .set(TOKEN.FK_TOKEN_TYPE_ID, tokenTypeId)
//                .set(TOKEN.DATA, idToken)
//                .set(TOKEN.EXPIRES_ON_MS, expiresOn)
//                .set(TOKEN.CREATE_TIME_MS, System.currentTimeMillis())
//                .set(TOKEN.ENABLED, true)
//                .set(TOKEN.COMMENTS, "This is an OpenId idToken created by the Authentication Service.")
//                .returning(new Field[]{TOKEN.ID})
//                .fetchOne()
//                .into(Token.class));
//    }

//    public String createToken(String recipientUserEmail, String clientId) throws NoSuchUserException {
//        return createToken(
//                Token.TokenType.USER,
//                "authenticationResource",
//                recipientUserEmail,
//                clientId,
//                true,
//                "Created for username/password user")
//                .getToken();
//    }

    /**
     * Create a token for a specific user.
     */
    @Override
    public Token createToken(
            Token.TokenType tokenType,
            String issuingUserEmail,
            Instant expiryDateIfApiKey,
            String recipientUserEmail,
            String clientId,
            boolean isEnabled,
            String comment) throws NoSuchUserException {

        TokenBuilder tokenBuilder = tokenBuilderFactory
                .expiryDateForApiKeys(expiryDateIfApiKey)
                .newBuilder(tokenType)
                .clientId(clientId)
                .subject(recipientUserEmail);

        Instant actualExpiryDate = tokenBuilder.getExpiryDate();
        String idToken = tokenBuilder.build();

        final Optional<Integer> optionalAccountId = accountDao.getId(recipientUserEmail);
        final Integer accountId = optionalAccountId.orElseThrow(() ->
                new NoSuchUserException("Cannot find user to associate with this API key!"));

        final Optional<Integer> optionalTokenTypeId = getTokenTypeId(tokenType.getText().toLowerCase());
        final Integer tokenTypeId = optionalTokenTypeId.orElseThrow(() ->
                new RuntimeException("Unknown token type: " + tokenType.getText().toLowerCase()));

        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .insertInto(TOKEN)
                .set(TOKEN.FK_ACCOUNT_ID, accountId)
                .set(TOKEN.FK_TOKEN_TYPE_ID, tokenTypeId)
                .set(TOKEN.DATA, idToken)
                .set(TOKEN.EXPIRES_ON_MS, actualExpiryDate.toEpochMilli())
                .set(TOKEN.CREATE_TIME_MS, System.currentTimeMillis())
                .set(TOKEN.CREATE_USER, issuingUserEmail)
                .set(TOKEN.ENABLED, isEnabled)
                .set(TOKEN.COMMENTS, comment)
                .returning()
                .fetchOne()
                .map(RECORD_TO_TOKEN_MAPPER::apply));
    }

    @Override
    public int deleteAllTokensExceptAdmins() {
        final Integer adminUserId = JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(ACCOUNT.ID).from(ACCOUNT)
                .where(ACCOUNT.EMAIL.eq("admin"))
                .fetchOne()
                .map(r -> r.get(ACCOUNT.ID)));

        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .deleteFrom(TOKEN)
                .where(TOKEN.FK_ACCOUNT_ID.ne(adminUserId))
                .execute());
    }

    @Override
    public int deleteTokenById(int tokenId) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context.deleteFrom(TOKEN).where(TOKEN.ID.eq(tokenId)).execute());
    }

    @Override
    public int deleteTokenByTokenString(String token) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context.deleteFrom(TOKEN).where(TOKEN.DATA.eq(token)).execute());
    }

    @Override
    public Optional<Token> readById(int tokenId) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(
                        TOKEN.ID,
                        TOKEN.VERSION,
                        TOKEN.CREATE_TIME_MS,
                        TOKEN.UPDATE_TIME_MS,
                        TOKEN.CREATE_USER,
                        TOKEN.UPDATE_USER,
                        ACCOUNT.EMAIL,
                        TOKEN_TYPE.TYPE,
                        TOKEN.DATA,
                        TOKEN.EXPIRES_ON_MS,
                        TOKEN.COMMENTS,
                        TOKEN.ENABLED)
                .from(TOKEN)
                .join(TOKEN_TYPE).on(TOKEN.FK_TOKEN_TYPE_ID.eq(TOKEN_TYPE.ID))
                .join(ACCOUNT).on(TOKEN.FK_ACCOUNT_ID.eq(ACCOUNT.ID))
                .where(TOKEN.ID.eq(tokenId))
                .fetchOptional()
                .map(RECORD_TO_TOKEN_MAPPER));
    }


    @Override
    public Optional<Token> readByToken(String token) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(
                        TOKEN.ID,
                        TOKEN.VERSION,
                        TOKEN.CREATE_TIME_MS,
                        TOKEN.UPDATE_TIME_MS,
                        TOKEN.CREATE_USER,
                        TOKEN.UPDATE_USER,
                        ACCOUNT.EMAIL,
                        TOKEN_TYPE.TYPE,
                        TOKEN.DATA,
                        TOKEN.EXPIRES_ON_MS,
                        TOKEN.COMMENTS,
                        TOKEN.ENABLED)
                .from(TOKEN)
                .join(TOKEN_TYPE).on(TOKEN.FK_TOKEN_TYPE_ID.eq(TOKEN_TYPE.ID))
                .join(ACCOUNT).on(TOKEN.FK_ACCOUNT_ID.eq(ACCOUNT.ID))
                .where(TOKEN.DATA.eq(token))
                .fetchOptional()
                .map(RECORD_TO_TOKEN_MAPPER));
    }


    @Override
    public int enableOrDisableToken(int tokenId, boolean enabled, Account updatingAccount) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .update(TOKEN)
                .set(TOKEN.ENABLED, enabled)
                .set(TOKEN.UPDATE_TIME_MS, System.currentTimeMillis())
                .set(TOKEN.UPDATE_USER, updatingAccount.getEmail())
                .where(TOKEN.ID.eq((tokenId)))
                .execute());
    }

    /**
     * How do we match on dates? Must match exactly? Must match part of the date? What if the given date is invalid?
     * Is this what a user would want? Maybe they want greater than or less than? This would need additional UI
     * For now we can't sensible implement anything unless we have a better idea of requirements.
     */
    private static List<Condition> getConditions(Map<String, String> filters) {
        // We need to set up conditions
        List<Condition> conditions = new ArrayList<>();
        final String unsupportedFilterMessage = "Unsupported filter: ";
        final String unknownFilterMessage = "Unknown filter: ";
        if (filters != null) {
            for (String key : filters.keySet()) {
                Condition condition;
                switch (key) {
                    case "enabled":
                        condition = TOKEN.ENABLED.eq(Boolean.valueOf(filters.get(key)));
                        break;
                    case "expiresOn":
                    case "issuedOn":
                    case "updatedOn":
                    case "userId":
                        throw new UnsupportedFilterException(unsupportedFilterMessage + key);
                    case "userEmail":
                        condition = ACCOUNT.EMAIL.contains(filters.get(key));
                        break;
                    case "issuedByUser":
                        condition = TOKEN.CREATE_USER.eq(filters.get(key));
                        break;
                    case "token":
                        // It didn't initially make sense that one might want to filter on token, because it's encrypted.
                        // But if someone has a token copy/pasting some or all of it into the search might be the
                        // fastest way to find the token.
                        condition = TOKEN.DATA.contains(filters.get(key));
                        break;
                    case "tokenType":
                        condition = TOKEN_TYPE.TYPE.eq(filters.get(key).toLowerCase());
                        break;
                    case "updatedByUser":
                        condition = TOKEN.UPDATE_USER.eq(filters.get(key));
                        break;
                    default:
                        throw new UnsupportedFilterException(unknownFilterMessage + key);
                }

                conditions.add(condition);
            }
        }
        return conditions;
    }

    static SortField<?>[] getOrderBy(String orderBy, String orderDirection) {
        // We might be ordering by TOKEN or ACCOUNT or TOKEN_TYPE - we join and select on all
        SortField<?> orderByField = TOKEN.CREATE_TIME_MS.desc();
        if (orderBy != null) {
            switch (orderBy) {
                case "userEmail":
                    orderByField = orderDirection.equals("asc") ? ACCOUNT.EMAIL.asc() : ACCOUNT.EMAIL.desc();
                    break;
                case "enabled":
                    orderByField = orderDirection.equals("asc") ? TOKEN.ENABLED.asc() : TOKEN.ENABLED.desc();
                    break;
                case "tokenType":
                    orderByField = orderDirection.equals("asc") ? TOKEN_TYPE.TYPE.asc() : TOKEN_TYPE.TYPE.desc();
                    break;
                case "issuedOn":
                default:
                    orderByField = orderDirection.equals("asc") ? TOKEN.CREATE_TIME_MS.asc() : TOKEN.CREATE_TIME_MS.desc();
            }
        }
        return new SortField[]{orderByField};
    }

}
