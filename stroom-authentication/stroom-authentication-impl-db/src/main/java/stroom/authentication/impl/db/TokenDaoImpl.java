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
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record11;
import org.jooq.Result;
import org.jooq.SelectJoinStep;
import org.jooq.SelectSelectStep;
import org.jooq.SortField;
import org.jooq.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.token.TokenBuilder;
import stroom.authentication.token.TokenBuilderFactory;
import stroom.authentication.config.TokenConfig;
import stroom.authentication.exceptions.BadRequestException;
import stroom.authentication.exceptions.NoSuchUserException;
import stroom.authentication.exceptions.UnsupportedFilterException;
import stroom.authentication.token.SearchRequest;
import stroom.authentication.token.SearchResponse;
import stroom.authentication.token.Token;
import stroom.authentication.token.TokenDao;
import stroom.authentication.account.Account;
import stroom.db.util.JooqUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static stroom.authentication.impl.db.jooq.tables.Token.TOKEN;
import static stroom.authentication.impl.db.jooq.tables.TokenType.TOKEN_TYPE;
import static stroom.authentication.impl.db.jooq.tables.Account.ACCOUNT;

@Singleton
class TokenDaoImpl implements TokenDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenDaoImpl.class);
    private final TokenConfig config;
    private AuthDbConnProvider authDbConnProvider;

    @Inject
    private TokenBuilderFactory tokenBuilderFactory;

    @Inject
    TokenDaoImpl(TokenConfig config, AuthDbConnProvider authDbConnProvider) {
        this.config = config;
        this.authDbConnProvider = authDbConnProvider;
    }

    @Override
    public SearchResponse searchTokens(SearchRequest searchRequest) {
        // Create some vars to allow the rest of this method to be more succinct.
        int page = searchRequest.getPage();
        int limit = searchRequest.getLimit();
        String orderBy = searchRequest.getOrderBy();
        String orderDirection = searchRequest.getOrderDirection();
        Map<String, String> filters = searchRequest.getFilters();

        // We need these aliased tables because we're joining tokens to users twice.
        stroom.authentication.impl.db.jooq.tables.Account issuingAccount = ACCOUNT.as("issuingAccount");
        stroom.authentication.impl.db.jooq.tables.Account tokenOwnerAccount = ACCOUNT.as("tokenOwnerAccount");
        stroom.authentication.impl.db.jooq.tables.Account updatingAccount = ACCOUNT.as("updatingAccount");

        // Use a default if there's no order direction specified in the request
        if (orderDirection == null) {
            orderDirection = "asc";
        }

        Field userEmail = tokenOwnerAccount.EMAIL.as("userEmail");
        // Special cases
        Optional<SortField> orderByField;
        if (orderBy != null && orderBy.equals("userEmail")) {
            // Why is this a special case? Because the property on the target table is 'email' but the param is 'user_email'
            // 'user_email' is a clearer param
            if (orderDirection.equals("asc")) {
                orderByField = Optional.of(userEmail.asc());
            } else {
                orderByField = Optional.of(userEmail.desc());
            }
        } else {
            orderByField = TokenDaoImpl.getOrderBy(orderBy, orderDirection);
            if (!orderByField.isPresent()) {
                throw new BadRequestException("Invalid orderBy: " + orderBy);
            }
        }

        Optional<List<Condition>> conditions;
        conditions = getConditions(filters, issuingAccount, tokenOwnerAccount, updatingAccount);

        return JooqUtil.contextResult(authDbConnProvider, context -> {
            int offset = limit * page;
            SelectJoinStep<Record11<Integer, Boolean, Timestamp, String, Timestamp, String, String, String, String, Timestamp, Integer>> selectFrom =
                    TokenDaoImpl.getSelectFrom(context, issuingAccount, tokenOwnerAccount, updatingAccount, userEmail);

            Result<Record11<Integer, Boolean, Timestamp, String, Timestamp, String, String, String, String, Timestamp, Integer>> results =
                    selectFrom
                            .where(conditions.get())
                            .orderBy(orderByField.get())
                            .limit(limit)
                            .offset(offset)
                            .fetch();
            List<Token> tokens = results.into(Token.class);

            // Finally we need to get the number of tokens so we can calculate the total number of pages
            SelectSelectStep<Record1<Integer>> selectCount =
                    context.selectCount();
            SelectJoinStep<Record11<Integer, Boolean, Timestamp, String, Timestamp, String, String, String, String, Timestamp, Integer>>
                    fromCount = TokenDaoImpl.getFrom(selectCount, issuingAccount, tokenOwnerAccount, updatingAccount, userEmail);
            int count = fromCount
                    .where(conditions.get())
                    .fetchOne(0, int.class);
            // We need to round up so we always have enough pages even if there's a remainder.
            int pages = (int) Math.ceil((double) count / limit);

            SearchResponse searchResponse = new SearchResponse();
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
                .getToken();
    }

    @Override
    public Token createIdToken(String idToken, String subject, long expiresOn) {
        Record1<Integer> userRecord = JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(ACCOUNT.ID)
                .from(ACCOUNT)
                .where(ACCOUNT.EMAIL.eq(subject))
                .fetchOne());
        if (userRecord == null) {
            throw new NoSuchUserException("Cannot find user to associate with this token!");
        }
        int recipientUserId = userRecord.get(ACCOUNT.ID);

        int tokenTypeId = JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(TOKEN_TYPE.ID)
                .from(TOKEN_TYPE)
                .where(TOKEN_TYPE.TYPE.eq(Token.TokenType.USER.getText().toLowerCase()))
                .fetchOne()
                .get(TOKEN_TYPE.ID));

        Token tokenRecord = JooqUtil.contextResult(authDbConnProvider, context -> context
                .insertInto((Table) TOKEN)
                .set(TOKEN.FK_ACCOUNT_ID, recipientUserId)
                .set(TOKEN.FK_TOKEN_TYPE_ID, tokenTypeId)
                .set(TOKEN.DATA, idToken)
                .set(TOKEN.EXPIRES_ON_MS, expiresOn)
                .set(TOKEN.CREATE_TIME_MS, System.currentTimeMillis())
                .set(TOKEN.ENABLED, true)
                .set(TOKEN.COMMENTS, "This is an OpenId idToken created by the Authentication Service.")
                .returning(new Field[]{TOKEN.ID})
                .fetchOne()
                .into(Token.class));

        return tokenRecord;
    }

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

        Record1<Integer> userRecord = JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(ACCOUNT.ID)
                .from(ACCOUNT)
                .where(ACCOUNT.EMAIL.eq(recipientUserEmail))
                .fetchOne());
        if (userRecord == null) {
            throw new NoSuchUserException("Cannot find user to associate with this API key!");
        }
        int recipientUserId = userRecord.get(ACCOUNT.ID);

        TokenBuilder tokenBuilder = tokenBuilderFactory
                .expiryDateForApiKeys(expiryDateIfApiKey)
                .newBuilder(tokenType)
                .clientId(clientId)
                .subject(recipientUserEmail);

        Instant actualExpiryDate = tokenBuilder.getExpiryDate();
        String idToken = tokenBuilder.build();

        int issuingUserId = JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(ACCOUNT.ID)
                .from(ACCOUNT)
                .where(ACCOUNT.EMAIL.eq(issuingUserEmail))
                .fetchOne()
                .get(ACCOUNT.ID));

        int tokenTypeId = JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(TOKEN_TYPE.ID)
                .from(TOKEN_TYPE)
                .where(TOKEN_TYPE.TYPE.eq(tokenType.getText().toLowerCase()))
                .fetchOne()
                .get(TOKEN_TYPE.ID));

        Token tokenRecord = JooqUtil.contextResult(authDbConnProvider, context -> context
                .insertInto(TOKEN)
                .set(TOKEN.FK_ACCOUNT_ID, recipientUserId)
                .set(TOKEN.FK_TOKEN_TYPE_ID, tokenTypeId)
                .set(TOKEN.DATA, idToken)
                .set(TOKEN.EXPIRES_ON_MS, actualExpiryDate.toEpochMilli())
                .set(TOKEN.CREATE_TIME_MS, System.currentTimeMillis())
                .set(TOKEN.CREATE_USER, issuingUserEmail)
                .set(TOKEN.ENABLED, isEnabled)
                .set(TOKEN.COMMENTS, comment)
                .returning()
                .fetchOne()
                .into(Token.class));

        return tokenRecord;
    }

    @Override
    public void deleteAllTokensExceptAdmins() {
        Integer adminUserId = JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(ACCOUNT.ID).from(ACCOUNT)
                .where(ACCOUNT.EMAIL.eq("admin")).fetchOne().into(Integer.class));

        JooqUtil.context(authDbConnProvider, context -> context
                .deleteFrom(TOKEN)
                .where(TOKEN.FK_ACCOUNT_ID.ne(adminUserId))
                .execute());
    }

    @Override
    public void deleteTokenById(int tokenId) {
        JooqUtil.context(authDbConnProvider, context -> context.deleteFrom(TOKEN).where(TOKEN.ID.eq(tokenId)).execute());
    }

    @Override
    public void deleteTokenByTokenString(String token) {
        JooqUtil.context(authDbConnProvider, context -> context.deleteFrom(TOKEN).where(TOKEN.DATA.eq(token)).execute());
    }

    @Override
    public Optional<Token> readById(int tokenId) {
        // We need these aliased tables because we're joining tokens to users twice.
        stroom.authentication.impl.db.jooq.tables.Account issuingAccount = ACCOUNT.as("issuingAccount");
        stroom.authentication.impl.db.jooq.tables.Account tokenOwnerAccount = ACCOUNT.as("tokenOwnerAccount");
        stroom.authentication.impl.db.jooq.tables.Account updatingAccount = ACCOUNT.as("updatingAccount");

        return JooqUtil.contextResult(authDbConnProvider, context -> {
            Field userEmail = tokenOwnerAccount.EMAIL.as("user_email");
            SelectJoinStep<Record11<Integer, Boolean, Timestamp, String, Timestamp, String, String, String, String, Timestamp, Integer>> selectFrom =
                    getSelectFrom(context, issuingAccount, tokenOwnerAccount, updatingAccount, userEmail);

            Record11<Integer, Boolean, Timestamp, String, Timestamp, String, String, String, String, Timestamp, Integer> token =
                    selectFrom
                            .where(new Condition[]{TOKEN.ID.eq(tokenId)})
                            .fetchOne();
            if (token == null) {
                return Optional.empty();
            }

            return Optional.of(token.into(Token.class));
        });
    }


    @Override
    public Optional<Token> readByToken(String token) {
        // We need these aliased tables because we're joining tokens to users twice.
        stroom.authentication.impl.db.jooq.tables.Account tokenOwnerAccount = ACCOUNT.as("tokenOwnerAccount");
//        Field userEmail = tokenOwnerAccount.EMAIL.as("user_email");

        Record tokenResult = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(TOKEN)
                .where(new Condition[]{TOKEN.DATA.eq(token)})
                .fetchOne());

        if (tokenResult == null) {
            return Optional.empty();
        }
        return Optional.of(tokenResult.into(Token.class));
    }


    @Override
    public void enableOrDisableToken(int tokenId, boolean enabled, Account updatingAccount) {
        Object result = JooqUtil.contextResult(authDbConnProvider, context -> context
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
    private static Optional<List<Condition>> getConditions(Map<String, String> filters, stroom.authentication.impl.db.jooq.tables.Account issuingAccount,
                                                           stroom.authentication.impl.db.jooq.tables.Account tokenOwnerAccount, stroom.authentication.impl.db.jooq.tables.Account updatingAccount) {
        // We need to set up conditions
        List<Condition> conditions = new ArrayList<>();
        final String unsupportedFilterMessage = "Unsupported filter: ";
        final String unknownFilterMessage = "Unknown filter: ";
        if (filters != null) {
            for (String key : filters.keySet()) {
                Condition condition = null;
                switch (key) {
                    case "enabled":
                        condition = TOKEN.ENABLED.eq(Boolean.valueOf(filters.get(key)));
                        break;
                    case "expiresOn":
                        throw new UnsupportedFilterException(unsupportedFilterMessage + key);
                    case "userEmail":
                        condition = tokenOwnerAccount.EMAIL.contains(filters.get(key));
                        break;
                    case "issuedOn":
                        throw new UnsupportedFilterException(unsupportedFilterMessage + key);
                    case "issuedByUser":
                        condition = issuingAccount.EMAIL.contains(filters.get(key));
                        break;
                    case "token":
                        // It didn't initally make sense that one might want to filter on token, because it's encrypted.
                        // But if someone has a token copy/pasting some or all of it into the search might be the
                        // fastest way to find the token.
                        condition = TOKEN.DATA.contains(filters.get(key));
                        break;
                    case "tokenType":
                        condition = TOKEN_TYPE.TYPE.contains(filters.get(key));
                        break;
                    case "updatedByUser":
                        condition = updatingAccount.EMAIL.contains(filters.get(key));
                        break;
                    case "updatedOn":
                        throw new UnsupportedFilterException(unsupportedFilterMessage + key);
                    case "userId":
                        throw new UnsupportedFilterException(unsupportedFilterMessage + key);
                    default:
                        throw new UnsupportedFilterException(unknownFilterMessage + key);
                }

                conditions.add(condition);
            }
        }
        return Optional.of(conditions);
    }

    static SelectJoinStep<Record11<Integer, Boolean, Timestamp, String, Timestamp, String, String, String, String, Timestamp, Integer>>
    getSelectFrom(DSLContext database, stroom.authentication.impl.db.jooq.tables.Account issuingAccount, stroom.authentication.impl.db.jooq.tables.Account tokenOwnerAccount, stroom.authentication.impl.db.jooq.tables.Account updatingAccount, Field userEmail) {
        SelectSelectStep<Record11<Integer, Boolean, Timestamp, String, Timestamp, String, String, String, String, Timestamp, Integer>>
                select = getSelect(database, issuingAccount, tokenOwnerAccount, updatingAccount, userEmail);

        SelectJoinStep from = getFrom(select, issuingAccount, tokenOwnerAccount, updatingAccount, userEmail);
        return from;
    }

    static SelectSelectStep<Record11<Integer, Boolean, Timestamp, String, Timestamp, String, String, String, String, Timestamp, Integer>>
    getSelect(DSLContext database, stroom.authentication.impl.db.jooq.tables.Account issuingAccount, stroom.authentication.impl.db.jooq.tables.Account tokenOwnerAccount, stroom.authentication.impl.db.jooq.tables.Account updatingAccount, Field userEmail) {
        SelectSelectStep<Record11<Integer, Boolean, Timestamp, String, Timestamp, String, String, String, String, Timestamp, Integer>>
                select = database.select(
                TOKEN.ID.as("id"),
                TOKEN.ENABLED.as("enabled"),
                TOKEN.EXPIRES_ON_MS.as("expires_on"),
                userEmail,
                TOKEN.CREATE_TIME_MS.as("issued_on"),
                issuingAccount.EMAIL.as("issued_by_user"),
                TOKEN.DATA.as("token"),
                TOKEN_TYPE.TYPE.as("token_type"),
                updatingAccount.EMAIL.as("updated_by_user"),
                TOKEN.UPDATE_TIME_MS.as("updated_on"),
                TOKEN.FK_ACCOUNT_ID.as("user_id"));

        return select;
    }

    static SelectJoinStep
    getFrom(SelectSelectStep select,
            stroom.authentication.impl.db.jooq.tables.Account issuingAccount, stroom.authentication.impl.db.jooq.tables.Account tokenOwnerAccount, stroom.authentication.impl.db.jooq.tables.Account updatingAccount, Field userEmail) {
        SelectJoinStep<Record11<Integer, Boolean, Timestamp, String, Timestamp, String, String, String, String, Timestamp, Integer>>
                from = select.from(TOKEN
                .join(TOKEN_TYPE)
                .on(TOKEN.FK_TOKEN_TYPE_ID.eq(TOKEN_TYPE.ID))
//                .join(issuingAccount)
//                .on(TOKEN.CREATE_USER.eq(issuingAccount.ID))
                .join(tokenOwnerAccount)
                .on(TOKEN.FK_ACCOUNT_ID.eq(tokenOwnerAccount.ID))
//                .join(updatingAccount)
//                .on(TOKEN.CREATE_USER.eq(updatingAccount.ID))
        );

        return from;
    }

    static Optional<SortField> getOrderBy(String orderBy, String orderDirection) {
        // We might be ordering by TOKEN or ACCOUNT or TOKEN_TYPE - we join and select on all
        SortField orderByField;
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
        } else {
            // We don't have an orderBy so we'll use the default ordering
            orderByField = TOKEN.CREATE_TIME_MS.desc();
        }
        return Optional.of(orderByField);
    }

}
