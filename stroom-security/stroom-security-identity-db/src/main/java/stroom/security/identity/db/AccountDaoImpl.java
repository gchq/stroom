/*
 * Copyright 2017-2024 Crown Copyright
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

package stroom.security.identity.db;

import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.authenticate.CredentialValidationResult;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.db.jooq.tables.records.AccountRecord;
import stroom.security.identity.exceptions.NoSuchUserException;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountResultPage;
import stroom.security.identity.shared.FindAccountRequest;
import stroom.security.shared.User;
import stroom.util.NullSafe;
import stroom.util.ResultPageFactory;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.PageResponse;
import stroom.util.shared.string.CIKey;

import com.google.common.base.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.TableField;
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static stroom.security.identity.db.jooq.tables.Account.ACCOUNT;

@Singleton
class AccountDaoImpl implements AccountDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AccountDaoImpl.class);

    /**
     * Create a derived field that represents the account state.
     */
    private static final Field<String> ACCOUNT_STATUS = DSL
            .when(ACCOUNT.LOCKED.eq(true), "Locked")
            .when(ACCOUNT.INACTIVE.eq(true), "Inactive")
            .when(ACCOUNT.ENABLED.eq(true), "Enabled")
            .otherwise("Disabled")
            .as(DSL.field("status", String.class));

    private static final Function<Record, Account> RECORD_TO_ACCOUNT_MAPPER = record -> {
        final Account account = new Account();
        account.setId(record.get(ACCOUNT.ID));
        account.setVersion(record.get(ACCOUNT.VERSION));
        account.setCreateTimeMs(record.get(ACCOUNT.CREATE_TIME_MS));
        account.setUpdateTimeMs(record.get(ACCOUNT.UPDATE_TIME_MS));
        account.setCreateUser(record.get(ACCOUNT.CREATE_USER));
        account.setUpdateUser(record.get(ACCOUNT.UPDATE_USER));
        account.setUserId(record.get(ACCOUNT.USER_ID));
        account.setEmail(record.get(ACCOUNT.EMAIL));
        account.setFirstName(record.get(ACCOUNT.FIRST_NAME));
        account.setLastName(record.get(ACCOUNT.LAST_NAME));
        account.setComments(record.get(ACCOUNT.COMMENTS));
        account.setLoginCount(record.get(ACCOUNT.LOGIN_COUNT));
        account.setLoginFailures(record.get(ACCOUNT.LOGIN_FAILURES));
        account.setLastLoginMs(record.get(ACCOUNT.LAST_LOGIN_MS));
        account.setReactivatedMs(record.get(ACCOUNT.REACTIVATED_MS));
        account.setForcePasswordChange(
                record.get(ACCOUNT.FORCE_PASSWORD_CHANGE));
        account.setNeverExpires(record.get(ACCOUNT.NEVER_EXPIRES));
        account.setEnabled(record.get(ACCOUNT.ENABLED));
        account.setInactive(record.get(ACCOUNT.INACTIVE));
        account.setLocked(record.get(ACCOUNT.LOCKED));
        account.setProcessingAccount(
                record.get(ACCOUNT.PROCESSING_ACCOUNT));
        return account;
    };

    private static final BiFunction<Account, AccountRecord, AccountRecord> ACCOUNT_TO_RECORD_MAPPER =
            (account, record) -> {
                record.setId(account.getId());
                record.setVersion(account.getVersion());
                record.setCreateTimeMs(account.getCreateTimeMs());
                record.setUpdateTimeMs(account.getUpdateTimeMs());
                record.setCreateUser(account.getCreateUser());
                record.setUpdateUser(account.getUpdateUser());
                record.setUserId(account.getUserId());
                record.setEmail(account.getEmail());
                record.setFirstName(account.getFirstName());
                record.setLastName(account.getLastName());
                record.setComments(account.getComments());
                record.setLoginCount(account.getLoginCount());
                record.setLoginFailures(account.getLoginFailures());
                record.setLastLoginMs(account.getLastLoginMs());
                record.setReactivatedMs(account.getReactivatedMs());
                record.setForcePasswordChange(account.isForcePasswordChange());
                record.setNeverExpires(account.isNeverExpires());
                record.setEnabled(account.isEnabled());
                record.setInactive(account.isInactive());
                record.setLocked(account.isLocked());
                record.setProcessingAccount(account.isProcessingAccount());

                return record;
            };

    protected static final String FIELD_NAME_USER_ID = "userId";
    protected static final String FIELD_NAME_EMAIL = "email";
    protected static final String FIELD_NAME_STATUS = "status";
    protected static final String FIELD_NAME_LAST_LOGIN_MS = "lastLoginMs";
    protected static final String FIELD_NAME_LOGIN_FAILURES = "loginFailures";
    protected static final String FIELD_NAME_COMMENTS = "comments";
    private static final Map<CIKey, Field<?>> FIELD_MAP = CIKey.mapOfEntries(
            entry("id", ACCOUNT.ID),
            entry("version", ACCOUNT.VERSION),
            entry("createTimeMs", ACCOUNT.CREATE_TIME_MS),
            entry("updateTimeMs", ACCOUNT.UPDATE_TIME_MS),
            entry("createUser", ACCOUNT.CREATE_USER),
            entry("updateUser", ACCOUNT.UPDATE_USER),
            entry(FIELD_NAME_USER_ID, ACCOUNT.USER_ID),
            entry(FIELD_NAME_EMAIL, ACCOUNT.EMAIL),
            entry("firstName", ACCOUNT.FIRST_NAME),
            entry("lastName", ACCOUNT.LAST_NAME),
            entry(FIELD_NAME_COMMENTS, ACCOUNT.COMMENTS),
            entry("loginCount", ACCOUNT.LOGIN_COUNT),
            entry(FIELD_NAME_LOGIN_FAILURES, ACCOUNT.LOGIN_FAILURES),
            entry(FIELD_NAME_LAST_LOGIN_MS, ACCOUNT.LAST_LOGIN_MS),
            entry("reactivatedMs", ACCOUNT.REACTIVATED_MS),
            entry("forcePasswordChange", ACCOUNT.FORCE_PASSWORD_CHANGE),
            entry("neverExpires", ACCOUNT.NEVER_EXPIRES),
            entry("enabled", ACCOUNT.ENABLED),
            entry("inactive", ACCOUNT.INACTIVE),
            entry("locked", ACCOUNT.LOCKED),
            entry("processingAccount", ACCOUNT.PROCESSING_ACCOUNT),
            entry(FIELD_NAME_STATUS, ACCOUNT_STATUS));

    private static final Map<String, Comparator<Account>> FIELD_COMPARATORS = Map.of(
            FIELD_NAME_USER_ID,
            CompareUtil.getNullSafeCaseInsensitiveComparator(Account::getUserId),
            FIELD_NAME_EMAIL,
            CompareUtil.getNullSafeCaseInsensitiveComparator(Account::getEmail),
            FIELD_NAME_STATUS,
            CompareUtil.getNullSafeCaseInsensitiveComparator(Account::getStatus),
            FIELD_NAME_LAST_LOGIN_MS,
            CompareUtil.getNullSafeComparator(Account::getLastLoginMs), // nullable
            FIELD_NAME_LOGIN_FAILURES,
            Comparator.comparingInt(Account::getLoginFailures), // not null
            FIELD_NAME_COMMENTS,
            CompareUtil.getNullSafeCaseInsensitiveComparator(Account::getComments));

    private final Provider<IdentityConfig> identityConfigProvider;
    private final IdentityDbConnProvider identityDbConnProvider;
    private final GenericDao<AccountRecord, Account, Integer> genericDao;

    @Inject
    public AccountDaoImpl(final Provider<IdentityConfig> identityConfigProvider,
                          final IdentityDbConnProvider identityDbConnProvider) {
        this.identityConfigProvider = identityConfigProvider;
        this.identityDbConnProvider = identityDbConnProvider;
        genericDao = new GenericDao<>(
                identityDbConnProvider,
                ACCOUNT,
                ACCOUNT.ID,
                ACCOUNT_TO_RECORD_MAPPER,
                RECORD_TO_ACCOUNT_MAPPER);
    }

    @Override
    public AccountResultPage list() {
        final TableField<AccountRecord, String> orderByUserIdField =
                ACCOUNT.USER_ID;
        final List<Account> list = JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .selectFrom(ACCOUNT)
                        .where(ACCOUNT.PROCESSING_ACCOUNT.isFalse())
                        .orderBy(orderByUserIdField)
                        .fetch())
                .map(RECORD_TO_ACCOUNT_MAPPER::apply);
        return ResultPageFactory.createUnboundedList(list, (accounts, pageResponse) ->
                new AccountResultPage(accounts, pageResponse, null));
    }

    @Override
    public AccountResultPage search(final FindAccountRequest request) {
        final Condition condition = createCondition(request);

        // Sort on user_id if no sort supplied
        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, request, ACCOUNT.USER_ID);
        final String fullyQualifyFilterInput = QuickFilterPredicateFactory.fullyQualifyInput(
                request.getQuickFilter(),
                AccountDao.FIELD_MAPPERS);

        return JooqUtil.contextResult(identityDbConnProvider, context -> {
            if (request.getQuickFilter() == null || request.getQuickFilter().length() == 0) {
                // Get the number of tokens so we can calculate the total number of pages
                final int count = context
                        .selectCount()
                        .from(ACCOUNT)
                        .where(condition)
                        .fetchOptional()
                        .map(Record1::value1)
                        .orElse(0);

                final int limit = JooqUtil.getLimit(request.getPageRequest(), false);
                final int offset = JooqUtil.getOffset(request.getPageRequest(), limit, count);

                final List<Account> list = context
                        .select(ACCOUNT.asterisk(),
                                ACCOUNT_STATUS)
                        .from(ACCOUNT)
                        .where(condition)
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit)
                        .fetch()
                        .map(RECORD_TO_ACCOUNT_MAPPER::apply);

                final PageResponse pageResponse = new PageResponse(
                        offset,
                        list.size(),
                        (long) count,
                        true);
                return new AccountResultPage(
                        list,
                        pageResponse,
                        fullyQualifyFilterInput);

            } else {
                try (final Stream<Record> stream = context
                        .select(ACCOUNT.asterisk(),
                                ACCOUNT_STATUS)
                        .from(ACCOUNT)
                        .where(condition)
                        .orderBy(orderFields)
                        .stream()) {

                    final Comparator<Account> comparator = buildComparator(request).orElse(null);

                    return QuickFilterPredicateFactory.filterStream(
                                    request.getQuickFilter(),
                                    AccountDao.FIELD_MAPPERS,
                                    stream.map(RECORD_TO_ACCOUNT_MAPPER),
                                    comparator)
                            .collect(AccountResultPage.collector(
                                    request.getPageRequest(),
                                    (accounts, pageResponse) ->
                                            new AccountResultPage(accounts, pageResponse, fullyQualifyFilterInput)));
                }
            }
        });
    }

    private Optional<Comparator<Account>> buildComparator(final FindAccountRequest request) {
        if (NullSafe.hasItems(request, FindAccountRequest::getSortList)) {
            return Optional.of(CompareUtil.buildCriteriaComparator(FIELD_COMPARATORS, request));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Account create(final Account account, final String password) {
        return create(account, password, false);
    }

    @Override
    public Account tryCreate(final Account account, final String password) {
        return create(account, password, true);
    }

    private Account create(final Account account,
                           final String password,
                           final boolean ignoreDuplicate) {
        final String passwordHash = hashPassword(password);
        final AccountRecord record = ACCOUNT_TO_RECORD_MAPPER.apply(
                account, ACCOUNT.newRecord());
        record.setPasswordHash(passwordHash);
        final AccountRecord accountRecord = ignoreDuplicate
                ? JooqUtil.tryCreate(identityDbConnProvider, record, ACCOUNT.USER_ID)
                : JooqUtil.create(identityDbConnProvider, record);
        return RECORD_TO_ACCOUNT_MAPPER.apply(accountRecord);
    }

    @Override
    public void recordSuccessfulLogin(final String userId) {
        // We reset the failed login count if we have a successful login
        JooqUtil.context(identityDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.LOGIN_FAILURES, 0)
                .set(ACCOUNT.REACTIVATED_MS, (Long) null)
                .set(ACCOUNT.LOGIN_COUNT,
                        ACCOUNT.LOGIN_COUNT.plus(1))
                .set(ACCOUNT.LAST_LOGIN_MS, System.currentTimeMillis())
                .where(ACCOUNT.USER_ID.eq(userId))
                .execute());
    }

    @Override
    public CredentialValidationResult validateCredentials(final String userId, final String password) {
        if (Strings.isNullOrEmpty(userId)
                || Strings.isNullOrEmpty(password)) {
            return new CredentialValidationResult(false, true, false, false, false, false);
        }

        Optional<AccountRecord> optionalRecord = JooqUtil.contextResult(identityDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(ACCOUNT.USER_ID.eq(userId))
                .fetchOptional());

        // Create the admin account if it doesn't exist.
        if (optionalRecord.isEmpty() && User.ADMIN_USER_SUBJECT_ID.equals(userId)) {
            final long now = System.currentTimeMillis();
            final Account account = new Account();
            account.setUserId(User.ADMIN_USER_SUBJECT_ID);
            account.setNeverExpires(true);
            account.setForcePasswordChange(true);
            account.setCreateTimeMs(now);
            account.setCreateUser("INTERNAL_PROCESSING_USER");
            account.setUpdateTimeMs(now);
            account.setUpdateUser("INTERNAL_PROCESSING_USER");
            account.setEnabled(true);
            tryCreate(account, User.ADMIN_USER_SUBJECT_ID);

            optionalRecord = JooqUtil.contextResult(identityDbConnProvider, context -> context
                    .selectFrom(ACCOUNT)
                    .where(ACCOUNT.USER_ID.eq(userId))
                    .fetchOptional());
        }

        if (optionalRecord.isEmpty()) {
            LOGGER.debug("Request to log in with invalid user id: " + userId);
            return new CredentialValidationResult(
                    false,
                    true,
                    false,
                    false,
                    false,
                    false);
        }

        final AccountRecord record = optionalRecord.get();
        boolean isPasswordCorrect = PasswordHashUtil.checkPassword(password, record.getPasswordHash());
        boolean isDisabled = !record.getEnabled();
        boolean isInactive = record.getInactive();
        boolean isLocked = record.getLocked();
        boolean isProcessingAccount = record.getProcessingAccount();

        return new CredentialValidationResult(
                isPasswordCorrect, false, isLocked, isDisabled, isInactive, isProcessingAccount);
    }

    @Override
    public boolean incrementLoginFailures(final String userId) {
        boolean locked = false;

        final IdentityConfig identityConfig = identityConfigProvider.get();
        if (identityConfig.getFailedLoginLockThreshold() != null) {
            JooqUtil.context(identityDbConnProvider, context -> context
                    .update(ACCOUNT)
//                    .set(DSL.row(ACCOUNT.LOGIN_FAILURES, ACCOUNT.LOGIN_FAILURES),
//                            DSL.row(DSL.select(ACCOUNT.LOGIN_FAILURES.plus(1),
//                                    DSL.field(ACCOUNT.LOGIN_FAILURES.plus(2)
//                                    .ge(config.getFailedLoginLockThreshold())).fr).plus(1))
//                    .set(ACCOUNT.LOCKED,
//                            context.select(DSL.count())
//                                    .from(ACCOUNT)
//                                    .where(ACCOUNT.EMAIL.eq(email)
//                                    .and(ACCOUNT.LOGIN_FAILURES.plus(1).ge(config.getFailedLoginLockThreshold()))
//                          ))

                    .set(ACCOUNT.LOGIN_FAILURES,
                            ACCOUNT.LOGIN_FAILURES.plus(1))
                    .set(ACCOUNT.LOCKED,
                            DSL.field(ACCOUNT.LOGIN_FAILURES
                                    .ge(identityConfig.getFailedLoginLockThreshold())))
                    .where(ACCOUNT.USER_ID.eq(userId))
                    .execute());

            // Query the field back to find out if we locked.
            locked = JooqUtil.contextResult(identityDbConnProvider, context -> context
                            .select(ACCOUNT.LOCKED)
                            .from(ACCOUNT)
                            .where(ACCOUNT.USER_ID.eq(userId))
                            .fetchOptional())
                    .map(Record1::value1)
                    .orElse(false);
        } else {
            JooqUtil.context(identityDbConnProvider, context -> context
                    .update(ACCOUNT)
                    .set(ACCOUNT.LOGIN_FAILURES,
                            ACCOUNT.LOGIN_FAILURES.plus(1))
                    .where(ACCOUNT.USER_ID.eq(userId))
                    .execute());
        }


//
//        boolean locked = false;
//        if (config.getFailedLoginLockThreshold() != null) {
//            // Set the locked field if we have exceeded threshold.
//            JooqUtil.context(authDbConnProvider, context -> context
//                    .update(ACCOUNT)
//                    .set(ACCOUNT.LOCKED, true)
//                    .where(ACCOUNT.EMAIL.eq(email))
//                    .and(ACCOUNT.LOGIN_FAILURES.greaterOrEqual(config.getFailedLoginLockThreshold()))
//                    .execute());
//
//            // Query the field back to find out if we locked.
//            locked = JooqUtil.contextResult(authDbConnProvider, context -> context
//                    .select(ACCOUNT.LOCKED)
//                    .from(ACCOUNT)
//                    .where(ACCOUNT.EMAIL.eq(email))
//                    .fetchOptional()
//                    .map(Record1::value1)
//                    .orElse(false));
//        }

        if (locked) {
            LOGGER.debug("Account {} has had too many failed access attempts and is locked", userId);
        }

        return locked;
    }

    @Override
    public Optional<Account> get(final String userId) {
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .selectFrom(ACCOUNT)
                        .where(ACCOUNT.USER_ID.eq(userId))
                        .fetchOptional())
                .map(RECORD_TO_ACCOUNT_MAPPER);
    }

    @Override
    public Optional<Integer> getId(final String userId) {
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .select(ACCOUNT.ID)
                        .from(ACCOUNT)
                        .where(ACCOUNT.USER_ID.eq(userId))
                        .fetchOptional())
                .map(r -> r.getValue(ACCOUNT.ID));
    }

    @Override
    public void update(final Account account) {
        genericDao.update(account);

//        AccountRecord usersRecord = JooqUtil.contextResult(authDbConnProvider, context -> context
//                .selectFrom(ACCOUNT)
//                .where(ACCOUNT.EMAIL.eq(account.getEmail())).fetchSingle());
//
//        UserMapper.mapToRecord(account, usersRecord);
//
//        JooqUtil.contextResult(authDbConnProvider, context -> context
//                .update(ACCOUNT)
//                .set(usersRecord)
//                .where(ACCOUNT.ID.eq(account.getId())}.execute());
    }

    @Override
    public void delete(final int id) {
        genericDao.delete(id);

//        JooqUtil.context(authDbConnProvider, context -> context
//                .deleteFrom(ACCOUNT)
//                .where(ACCOUNT.ID.eq(id)).execute());
    }

    @Override
    public Optional<Account> get(int id) {
        return genericDao.fetch(id);

//        Optional<AccountRecord> userQuery = JooqUtil.contextResult(authDbConnProvider, context -> context
//                .selectFrom(ACCOUNT)
//                .where(ACCOUNT.ID.eq(id)).fetchOptional());
//
//        return userQuery.map(record -> {
//            final Account account = new Account();
//            UserMapper.mapFromRecord(record, account);
//            return account;
//        });
    }


    @Override
    public void changePassword(final String userId, final String newPassword) {
        final String newPasswordHash = PasswordHashUtil.hash(newPassword);

        final int count = JooqUtil.contextResult(identityDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.PASSWORD_HASH, newPasswordHash)
                .set(ACCOUNT.PASSWORD_LAST_CHANGED_MS,
                        System.currentTimeMillis())
                .set(ACCOUNT.FORCE_PASSWORD_CHANGE, false)
                .where(ACCOUNT.USER_ID.eq(userId))
                .execute());

        if (count == 0) {
            throw new NoSuchUserException("Cannot change this password because this user does not exist!");
        }
    }

    @Override
    public void resetPassword(final String userId, final String newPassword) {
        final String newPasswordHash = PasswordHashUtil.hash(newPassword);

        final int count = JooqUtil.contextResult(identityDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.PASSWORD_HASH, newPasswordHash)
                .set(ACCOUNT.PASSWORD_LAST_CHANGED_MS,
                        System.currentTimeMillis())
                .set(ACCOUNT.FORCE_PASSWORD_CHANGE, false)
                .set(ACCOUNT.LOCKED, false)
                .set(ACCOUNT.INACTIVE, false)
                .set(ACCOUNT.ENABLED, true)
                .set(ACCOUNT.LOGIN_FAILURES, 0)
                .where(ACCOUNT.USER_ID.eq(userId))
                .execute());

        if (count == 0) {
            throw new NoSuchUserException("Cannot reset this password because this user does not exist!");
        }
    }

    @Override
    public Boolean needsPasswordChange(final String userId,
                                       final Duration mandatoryPasswordChangeDuration,
                                       final boolean forcePasswordChangeOnFirstLogin) {
        Objects.requireNonNull(userId, "userId must not be null");

        final AccountRecord user = JooqUtil.contextResult(identityDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(ACCOUNT.USER_ID.eq(userId))
                .fetchOne());

        if (user == null) {
            throw new NoSuchUserException(
                    "Cannot check if this user needs a password change because this user does not exist!");
        }

        final long passwordLastChangedMs = user.getPasswordLastChangedMs() == null
                ? user.getCreateTimeMs()
                : user.getPasswordLastChangedMs();

        final Duration durationSinceLastPasswordChange = Duration.between(
                Instant.ofEpochMilli(passwordLastChangedMs),
                Instant.now());

        final boolean thresholdBreached = durationSinceLastPasswordChange
                .compareTo(mandatoryPasswordChangeDuration) > 0;

        final boolean isFirstLogin = user.getPasswordLastChangedMs() == null;

        if (thresholdBreached
                || (forcePasswordChangeOnFirstLogin && isFirstLogin)
                || user.getForcePasswordChange()) {
            LOGGER.debug("User {} needs a password change.", userId);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int deactivateNewInactiveUsers(final Duration neverUsedAccountDeactivationThreshold) {
        final long activityThreshold = Instant.now()
                .minus(neverUsedAccountDeactivationThreshold)
                .toEpochMilli();

        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.INACTIVE, true)
                .where(ACCOUNT.CREATE_TIME_MS
                        .lessOrEqual(activityThreshold))
                // We are only going to deactivate active accounts
                .and(ACCOUNT.INACTIVE.isFalse())
                // A 'new' user is one who has never logged in.
                .and(ACCOUNT.LAST_LOGIN_MS.isNull())
                // We don't want to disable all accounts
                .and(ACCOUNT.NEVER_EXPIRES.isFalse())
                // We don't want to disable accounts that have been recently reactivated.
                .and(ACCOUNT.REACTIVATED_MS.isNull()
                        .or(ACCOUNT.REACTIVATED_MS
                                .lessThan(activityThreshold)))
                .execute());
    }

    @Override
    public int deactivateInactiveUsers(final Duration unusedAccountDeactivationThreshold) {
        final long activityThreshold = Instant.now()
                .minus(unusedAccountDeactivationThreshold)
                .toEpochMilli();

        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.INACTIVE, true)
                .where(ACCOUNT.CREATE_TIME_MS
                        .lessOrEqual(activityThreshold))
                // We are only going to deactivate active accounts
                .and(ACCOUNT.INACTIVE.isFalse())
                // Choose users that have logged in but not for a while.
                .and(ACCOUNT.LAST_LOGIN_MS.isNotNull())
                .and(ACCOUNT.LAST_LOGIN_MS
                        .lessOrEqual(activityThreshold))
                // We don't want to disable all accounts
                .and(ACCOUNT.NEVER_EXPIRES.isFalse())
                // We don't want to disable accounts that have been recently reactivated.
                .and(ACCOUNT.REACTIVATED_MS.isNull()
                        .or(ACCOUNT.REACTIVATED_MS
                                .lessThan(activityThreshold)))
                .execute());
    }

    private Condition createCondition(final FindAccountRequest request) {
        Condition condition = ACCOUNT.PROCESSING_ACCOUNT.isFalse();
//        if (request.getQuickFilter() != null) {
//            condition = condition.and(ACCOUNT.USER_ID.contains(request.getQuickFilter()));
//        }
        return condition;
    }

    private String hashPassword(final String password) {
        if (password == null) {
            return null;
        }
        return PasswordHashUtil.hash(password);
    }
}
