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
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.account.AccountResource;
import stroom.security.identity.account.SearchAccountRequest;
import stroom.security.identity.authenticate.CredentialValidationResult;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.db.jooq.tables.records.AccountRecord;
import stroom.security.identity.exceptions.NoSuchUserException;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.security.shared.User;
import stroom.util.collections.ResultPageCollector;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;

import com.google.common.base.Strings;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.TableField;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Map.entry;

@Singleton
class AccountDaoImpl implements AccountDao {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AccountDaoImpl.class);

    private static final Function<Record, Account> RECORD_TO_ACCOUNT_MAPPER = record -> {
        final Account account = new Account();
        account.setId(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ID));
        account.setVersion(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.VERSION));
        account.setCreateTimeMs(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.CREATE_TIME_MS));
        account.setUpdateTimeMs(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.UPDATE_TIME_MS));
        account.setCreateUser(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.CREATE_USER));
        account.setUpdateUser(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.UPDATE_USER));
        account.setUserId(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID));
        account.setEmail(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.EMAIL));
        account.setFirstName(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.FIRST_NAME));
        account.setLastName(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LAST_NAME));
        account.setComments(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.COMMENTS));
        account.setLoginCount(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOGIN_COUNT));
        account.setLoginFailures(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOGIN_FAILURES));
        account.setLastLoginMs(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LAST_LOGIN_MS));
        account.setReactivatedMs(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.REACTIVATED_MS));
        account.setForcePasswordChange(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.FORCE_PASSWORD_CHANGE));
        account.setNeverExpires(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.NEVER_EXPIRES));
        account.setEnabled(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ENABLED));
        account.setInactive(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.INACTIVE));
        account.setLocked(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOCKED));
        account.setProcessingAccount(record.get(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.PROCESSING_ACCOUNT));
        return account;
    };

    private static final BiFunction<Account, AccountRecord, AccountRecord> ACCOUNT_TO_RECORD_MAPPER = (account, record) -> {
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

    private static final Map<String, Field<?>> FIELD_MAP = Map.ofEntries(
            entry("id", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ID),
            entry("version", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.VERSION),
            entry("createTimeMs", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.CREATE_TIME_MS),
            entry("updateTimeMs", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.UPDATE_TIME_MS),
            entry("createUser", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.CREATE_USER),
            entry("updateUser", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.UPDATE_USER),
            entry("userId", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID),
            entry("email", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.EMAIL),
            entry("firstName", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.FIRST_NAME),
            entry("lastName", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LAST_NAME),
            entry("comments", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.COMMENTS),
            entry("loginCount", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOGIN_COUNT),
            entry("loginFailures", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOGIN_FAILURES),
            entry("lastLoginMs", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LAST_LOGIN_MS),
            entry("reactivatedMs", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.REACTIVATED_MS),
            entry("forcePasswordChange", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.FORCE_PASSWORD_CHANGE),
            entry("neverExpires", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.NEVER_EXPIRES),
            entry("enabled", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ENABLED),
            entry("inactive", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.INACTIVE),
            entry("locked", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOCKED),
            entry("processingAccount", stroom.security.identity.db.jooq.tables.Account.ACCOUNT.PROCESSING_ACCOUNT));

    private static final FilterFieldMappers<Account> FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(
                    AccountResource.FIELD_DEF_USER_ID,
                    Account::getUserId),
            FilterFieldMapper.of(
                    AccountResource.FIELD_DEF_EMAIL,
                    Account::getEmail),
            FilterFieldMapper.of(
                    AccountResource.FIELD_DEF_FIRST_NAME,
                    Account::getFirstName),
            FilterFieldMapper.of(
                    AccountResource.FIELD_DEF_LAST_NAME,
                    Account::getLastName));

    private final IdentityConfig config;
    private final AuthDbConnProvider authDbConnProvider;
    private final GenericDao<AccountRecord, Account, Integer> genericDao;

    @Inject
    public AccountDaoImpl(final IdentityConfig config,
                          final AuthDbConnProvider authDbConnProvider) {
        this.config = config;
        this.authDbConnProvider = authDbConnProvider;

        genericDao = new GenericDao<>(stroom.security.identity.db.jooq.tables.Account.ACCOUNT, stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ID, Account.class, authDbConnProvider);
        genericDao.setObjectToRecordMapper(ACCOUNT_TO_RECORD_MAPPER);
        genericDao.setRecordToObjectMapper(RECORD_TO_ACCOUNT_MAPPER);
    }

    @Override
    public ResultPage<Account> list() {
        final TableField<AccountRecord, String> orderByUserIdField = stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID;
        final List<Account> list = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                .where(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.PROCESSING_ACCOUNT.isFalse())
                .orderBy(orderByUserIdField)
                .fetch()
                .map(RECORD_TO_ACCOUNT_MAPPER::apply));
        return ResultPage.createUnboundedList(list);
    }

    @Override
    public ResultPage<Account> search(final SearchAccountRequest request) {
        final Condition condition = createCondition(request);

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, request);

        return JooqUtil.contextResult(authDbConnProvider, context -> {
            if (request.getQuickFilter() == null || request.getQuickFilter().length() == 0) {
                final List<Account> list = context
                        .selectFrom(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                        .where(condition)
                        .orderBy(orderFields)
                        .offset(JooqUtil.getOffset(request.getPageRequest()))
                        .limit(JooqUtil.getLimit(request.getPageRequest(), true))
                        .fetch()
                        .map(RECORD_TO_ACCOUNT_MAPPER::apply);

                // Finally we need to get the number of tokens so we can calculate the total number of pages
                final int count = context
                        .selectCount()
                        .from(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                        .where(condition)
                        .fetchOptional()
                        .map(Record1::value1)
                        .orElse(0);

                return ResultPage.createCriterialBasedList(list, request, (long) count);

            } else {
                // Create the predicate for the current filter value
                final Predicate<Account> fuzzyMatchPredicate = QuickFilterPredicateFactory.createFuzzyMatchPredicate(
                        request.getQuickFilter(), FIELD_MAPPERS);

                try (final Stream<AccountRecord> stream = context
                        .selectFrom(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                        .where(condition)
                        .orderBy(orderFields)
                        .stream()) {

                    return stream
                            .map(RECORD_TO_ACCOUNT_MAPPER)
                            .filter(fuzzyMatchPredicate)
                            .collect(ResultPageCollector.create(request.getPageRequest()))
                            .build();
                }
            }
        });
    }

    @Override
    public Account create(final Account account, final String password) {
        final String passwordHash = hashPassword(password);
        return JooqUtil.contextResult(authDbConnProvider, context -> {
            LOGGER.debug(() -> LogUtil.message("Creating a {}",
                    stroom.security.identity.db.jooq.tables.Account.ACCOUNT.getName()));
            final AccountRecord record = ACCOUNT_TO_RECORD_MAPPER.apply(account, context.newRecord(stroom.security.identity.db.jooq.tables.Account.ACCOUNT));
            record.setPasswordHash(passwordHash);
            record.store();
            return RECORD_TO_ACCOUNT_MAPPER.apply(record);
        });
    }

    @Override
    public void recordSuccessfulLogin(final String userId) {
        // We reset the failed login count if we have a successful login
        JooqUtil.context(authDbConnProvider, context -> context
                .update(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                .set(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOGIN_FAILURES, 0)
                .set(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.REACTIVATED_MS, (Long) null)
                .set(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOGIN_COUNT, stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOGIN_COUNT.plus(1))
                .set(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LAST_LOGIN_MS, System.currentTimeMillis())
                .where(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID.eq(userId))
                .execute());
    }

    @Override
    public CredentialValidationResult validateCredentials(final String userId, final String password) {
        if (Strings.isNullOrEmpty(userId)
                || Strings.isNullOrEmpty(password)) {
            return new CredentialValidationResult(false, true, false, false, false, false);
        }

        Optional<AccountRecord> optionalRecord = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                .where(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID.eq(userId))
                .fetchOptional());

        // Create the admin account if it doesn't exist.
        if (optionalRecord.isEmpty() && User.ADMIN_USER_NAME.equals(userId)) {
            final long now = System.currentTimeMillis();
            final Account account = new Account();
            account.setUserId(User.ADMIN_USER_NAME);
            account.setNeverExpires(true);
            account.setForcePasswordChange(true);
            account.setCreateTimeMs(now);
            account.setCreateUser("INTERNAL_PROCESSING_USER");
            account.setUpdateTimeMs(now);
            account.setUpdateUser("INTERNAL_PROCESSING_USER");
            account.setEnabled(true);
            create(account, User.ADMIN_USER_NAME);

            optionalRecord = JooqUtil.contextResult(authDbConnProvider, context -> context
                    .selectFrom(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                    .where(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID.eq(userId))
                    .fetchOptional());
        }

        if (optionalRecord.isEmpty()) {
            LOGGER.debug("Request to log in with invalid user id: " + userId);
            return new CredentialValidationResult(false, true, false, false, false, false);
        }

        final AccountRecord record = optionalRecord.get();
        boolean isPasswordCorrect = PasswordHashUtil.checkPassword(password, record.getPasswordHash());
        boolean isDisabled = !record.getEnabled();
        boolean isInactive = record.getInactive();
        boolean isLocked = record.getLocked();
        boolean isProcessingAccount = record.getProcessingAccount();

        return new CredentialValidationResult(isPasswordCorrect, false, isLocked, isDisabled, isInactive, isProcessingAccount);
    }

    @Override
    public boolean incrementLoginFailures(final String userId) {
        boolean locked = false;

        if (config.getFailedLoginLockThreshold() != null) {
            JooqUtil.context(authDbConnProvider, context -> context
                    .update(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
//                    .set(DSL.row(ACCOUNT.LOGIN_FAILURES, ACCOUNT.LOGIN_FAILURES),
//                            DSL.row(DSL.select(ACCOUNT.LOGIN_FAILURES.plus(1),
//                                    DSL.field(ACCOUNT.LOGIN_FAILURES.plus(2).ge(config.getFailedLoginLockThreshold())).fr).plus(1))
//                    .set(ACCOUNT.LOCKED,
//                            context.select(DSL.count())
//                                    .from(ACCOUNT)
//                                    .where(ACCOUNT.EMAIL.eq(email).and(ACCOUNT.LOGIN_FAILURES.plus(1).ge(config.getFailedLoginLockThreshold()))
//                          ))

                    .set(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOGIN_FAILURES, stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOGIN_FAILURES.plus(1))
                    .set(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOCKED, DSL.field(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOGIN_FAILURES.ge(config.getFailedLoginLockThreshold())))
                    .where(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID.eq(userId))
                    .execute());

            // Query the field back to find out if we locked.
            locked = JooqUtil.contextResult(authDbConnProvider, context -> context
                    .select(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOCKED)
                    .from(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                    .where(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID.eq(userId))
                    .fetchOptional()
                    .map(Record1::value1)
                    .orElse(false));
        } else {
            JooqUtil.context(authDbConnProvider, context -> context
                    .update(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                    .set(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOGIN_FAILURES, stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LOGIN_FAILURES.plus(1))
                    .where(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID.eq(userId))
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
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                .where(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID.eq(userId))
                .fetchOptional()
                .map(RECORD_TO_ACCOUNT_MAPPER));
    }

    @Override
    public Optional<Integer> getId(final String userId) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ID)
                .from(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                .where(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID.eq(userId))
                .fetchOptional()
                .map(r -> r.getValue(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.ID)));
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

        final int count = JooqUtil.contextResult(authDbConnProvider, context -> context
                .update(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                .set(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.PASSWORD_HASH, newPasswordHash)
                .set(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.PASSWORD_LAST_CHANGED_MS, System.currentTimeMillis())
                .set(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.FORCE_PASSWORD_CHANGE, false)
                .where(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID.eq(userId))
                .execute());

        if (count == 0) {
            throw new NoSuchUserException("Cannot change this password because this user does not exist!");
        }
    }

    @Override
    public Boolean needsPasswordChange(final String userId,
                                       final Duration mandatoryPasswordChangeDuration,
                                       final boolean forcePasswordChangeOnFirstLogin) {
        Objects.requireNonNull(userId, "userId must not be null");

        final AccountRecord user = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                .where(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.USER_ID.eq(userId))
                .fetchOne());

        if (user == null) {
            throw new NoSuchUserException(
                    "Cannot check if this user needs a password change because this user does not exist!");
        }

        final long passwordLastChangedMs = user.getPasswordLastChangedMs() == null ?
                user.getCreateTimeMs() :
                user.getPasswordLastChangedMs();

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
        } else return false;
    }

    @Override
    public int deactivateNewInactiveUsers(final Duration neverUsedAccountDeactivationThreshold) {
        final long activityThreshold = Instant.now()
                .minus(neverUsedAccountDeactivationThreshold)
                .toEpochMilli();

        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .update(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                .set(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.INACTIVE, true)
                .where(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.CREATE_TIME_MS.lessOrEqual(activityThreshold))
                // We are only going to deactivate active accounts
                .and(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.INACTIVE.isFalse())
                // A 'new' user is one who has never logged in.
                .and(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LAST_LOGIN_MS.isNull())
                // We don't want to disable all accounts
                .and(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.NEVER_EXPIRES.isFalse())
                // We don't want to disable accounts that have been recently reactivated.
                .and(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.REACTIVATED_MS.isNull().or(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.REACTIVATED_MS.lessThan(activityThreshold)))
                .execute());
    }

    @Override
    public int deactivateInactiveUsers(final Duration unusedAccountDeactivationThreshold) {
        final long activityThreshold = Instant.now()
                .minus(unusedAccountDeactivationThreshold)
                .toEpochMilli();

        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .update(stroom.security.identity.db.jooq.tables.Account.ACCOUNT)
                .set(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.INACTIVE, true)
                .where(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.CREATE_TIME_MS.lessOrEqual(activityThreshold))
                // We are only going to deactivate active accounts
                .and(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.INACTIVE.isFalse())
                // Choose users that have logged in but not for a while.
                .and(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LAST_LOGIN_MS.isNotNull())
                .and(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.LAST_LOGIN_MS.lessOrEqual(activityThreshold))
                // We don't want to disable all accounts
                .and(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.NEVER_EXPIRES.isFalse())
                // We don't want to disable accounts that have been recently reactivated.
                .and(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.REACTIVATED_MS.isNull().or(stroom.security.identity.db.jooq.tables.Account.ACCOUNT.REACTIVATED_MS.lessThan(activityThreshold)))
                .execute());
    }

    private Condition createCondition(final SearchAccountRequest request) {
        Condition condition = stroom.security.identity.db.jooq.tables.Account.ACCOUNT.PROCESSING_ACCOUNT.isFalse();
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
