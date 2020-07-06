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

import stroom.authentication.account.Account;
import stroom.authentication.account.AccountDao;
import stroom.authentication.account.SearchAccountRequest;
import stroom.authentication.authenticate.CredentialValidationResult;
import stroom.authentication.config.AuthenticationConfig;
import stroom.authentication.exceptions.NoSuchUserException;
import stroom.authentication.impl.db.jooq.tables.records.AccountRecord;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import com.google.common.base.Strings;
import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.TableField;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static stroom.authentication.impl.db.jooq.tables.Account.ACCOUNT;

@Singleton
class AccountDaoImpl implements AccountDao {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AccountDaoImpl.class);

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
        account.setForcePasswordChange(record.get(ACCOUNT.FORCE_PASSWORD_CHANGE));
        account.setNeverExpires(record.get(ACCOUNT.NEVER_EXPIRES));
        account.setEnabled(record.get(ACCOUNT.ENABLED));
        account.setInactive(record.get(ACCOUNT.INACTIVE));
        account.setLocked(record.get(ACCOUNT.LOCKED));
        account.setProcessingAccount(record.get(ACCOUNT.PROCESSING_ACCOUNT));
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

    private final AuthenticationConfig config;
    private final AuthDbConnProvider authDbConnProvider;
    private final GenericDao<AccountRecord, Account, Integer> genericDao;

    @Inject
    public AccountDaoImpl(final AuthenticationConfig config,
                          final AuthDbConnProvider authDbConnProvider) {
        this.config = config;
        this.authDbConnProvider = authDbConnProvider;

        genericDao = new GenericDao<>(ACCOUNT, ACCOUNT.ID, Account.class, authDbConnProvider);
        genericDao.setObjectToRecordMapper(ACCOUNT_TO_RECORD_MAPPER);
        genericDao.setRecordToObjectMapper(RECORD_TO_ACCOUNT_MAPPER);
    }

    @Override
    public Account create(final Account account, final String password) {
        return JooqUtil.contextResult(authDbConnProvider, context -> {
            LOGGER.debug(LambdaLogUtil.message("Creating a {}", ACCOUNT.getName()));
            final AccountRecord record = ACCOUNT_TO_RECORD_MAPPER.apply(account, context.newRecord(ACCOUNT));
            record.setPasswordHash(PasswordHashUtil.hash(password));
            record.store();
            return RECORD_TO_ACCOUNT_MAPPER.apply(record);
        });
    }

    @Override
    public void recordSuccessfulLogin(final String userId) {
        // We reset the failed login count if we have a successful login
        JooqUtil.context(authDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.LOGIN_FAILURES, 0)
                .set(ACCOUNT.REACTIVATED_MS, (Long) null)
                .set(ACCOUNT.LOGIN_COUNT, ACCOUNT.LOGIN_COUNT.plus(1))
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

        final Optional<AccountRecord> optionalRecord = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(ACCOUNT.USER_ID.eq(userId))
                .fetchOptional());

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
                    .update(ACCOUNT)
//                    .set(DSL.row(ACCOUNT.LOGIN_FAILURES, ACCOUNT.LOGIN_FAILURES),
//                            DSL.row(DSL.select(ACCOUNT.LOGIN_FAILURES.plus(1),
//                                    DSL.field(ACCOUNT.LOGIN_FAILURES.plus(2).ge(config.getFailedLoginLockThreshold())).fr).plus(1))
//                    .set(ACCOUNT.LOCKED,
//                            context.select(DSL.count())
//                                    .from(ACCOUNT)
//                                    .where(ACCOUNT.EMAIL.eq(email).and(ACCOUNT.LOGIN_FAILURES.plus(1).ge(config.getFailedLoginLockThreshold()))
//                          ))

                    .set(ACCOUNT.LOGIN_FAILURES, ACCOUNT.LOGIN_FAILURES.plus(1))
                    .set(ACCOUNT.LOCKED, DSL.field(ACCOUNT.LOGIN_FAILURES.ge(config.getFailedLoginLockThreshold())))
                    .where(ACCOUNT.USER_ID.eq(userId))
                    .execute());

            // Query the field back to find out if we locked.
            locked = JooqUtil.contextResult(authDbConnProvider, context -> context
                    .select(ACCOUNT.LOCKED)
                    .from(ACCOUNT)
                    .where(ACCOUNT.USER_ID.eq(userId))
                    .fetchOptional()
                    .map(Record1::value1)
                    .orElse(false));
        } else {
            JooqUtil.context(authDbConnProvider, context -> context
                    .update(ACCOUNT)
                    .set(ACCOUNT.LOGIN_FAILURES, ACCOUNT.LOGIN_FAILURES.plus(1))
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
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(ACCOUNT.USER_ID.eq(userId))
                .fetchOptional()
                .map(RECORD_TO_ACCOUNT_MAPPER));
    }

    @Override
    public Optional<Integer> getId(final String userId) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(ACCOUNT.ID)
                .from(ACCOUNT)
                .where(ACCOUNT.USER_ID.eq(userId))
                .fetchOptional()
                .map(r -> r.getValue(ACCOUNT.ID)));
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
    public ResultPage<Account> list() {
        final TableField<AccountRecord, String> orderByUserIdField = ACCOUNT.USER_ID;
        final List<Account> list = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(ACCOUNT.PROCESSING_ACCOUNT.isFalse())
                .orderBy(orderByUserIdField)
                .fetch()
                .map(RECORD_TO_ACCOUNT_MAPPER::apply));
        return ResultPage.createUnboundedList(list);
    }

    @Override
    public void changePassword(final String userId, final String newPassword) {
        final String newPasswordHash = PasswordHashUtil.hash(newPassword);

        final int count = JooqUtil.contextResult(authDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.PASSWORD_HASH, newPasswordHash)
                .set(ACCOUNT.PASSWORD_LAST_CHANGED_MS, System.currentTimeMillis())
                .set(ACCOUNT.FORCE_PASSWORD_CHANGE, false)
                .where(ACCOUNT.USER_ID.eq(userId))
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
                .selectFrom(ACCOUNT)
                .where(ACCOUNT.USER_ID.eq(userId))
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
                .update(ACCOUNT)
                .set(ACCOUNT.INACTIVE, true)
                .where(ACCOUNT.CREATE_TIME_MS.lessOrEqual(activityThreshold))
                // We are only going to deactivate active accounts
                .and(ACCOUNT.INACTIVE.isFalse())
                // A 'new' user is one who has never logged in.
                .and(ACCOUNT.LAST_LOGIN_MS.isNull())
                // We don't want to disable all accounts
                .and(ACCOUNT.NEVER_EXPIRES.isFalse())
                // We don't want to disable accounts that have been recently reactivated.
                .and(ACCOUNT.REACTIVATED_MS.isNull().or(ACCOUNT.REACTIVATED_MS.lessThan(activityThreshold)))
                .execute());
    }

    @Override
    public int deactivateInactiveUsers(final Duration unusedAccountDeactivationThreshold) {
        final long activityThreshold = Instant.now()
                .minus(unusedAccountDeactivationThreshold)
                .toEpochMilli();

        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.INACTIVE, true)
                .where(ACCOUNT.CREATE_TIME_MS.lessOrEqual(activityThreshold))
                // We are only going to deactivate active accounts
                .and(ACCOUNT.INACTIVE.isFalse())
                // Choose users that have logged in but not for a while.
                .and(ACCOUNT.LAST_LOGIN_MS.isNotNull())
                .and(ACCOUNT.LAST_LOGIN_MS.lessOrEqual(activityThreshold))
                // We don't want to disable all accounts
                .and(ACCOUNT.NEVER_EXPIRES.isFalse())
                // We don't want to disable accounts that have been recently reactivated.
                .and(ACCOUNT.REACTIVATED_MS.isNull().or(ACCOUNT.REACTIVATED_MS.lessThan(activityThreshold)))
                .execute());
    }

    @Override
    public ResultPage<Account> searchUsersForDisplay(final SearchAccountRequest request) {
        final Condition condition = createCondition(request);
        final List<Account> list = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(condition)
                .offset(JooqUtil.getOffset(request.getPageRequest()))
                .limit(JooqUtil.getLimit(request.getPageRequest(), true))
                .fetch()
                .map(RECORD_TO_ACCOUNT_MAPPER::apply));
        return ResultPage.createCriterialBasedList(list, request);
    }

    private Condition createCondition(final SearchAccountRequest request) {
        Condition condition = ACCOUNT.PROCESSING_ACCOUNT.isFalse();
        if (request.getQuickFilter() != null) {
            condition = condition.and(ACCOUNT.USER_ID.contains(request.getQuickFilter()));
        }
        return condition;
    }
}
