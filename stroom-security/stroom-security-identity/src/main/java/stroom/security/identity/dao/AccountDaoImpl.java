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

package stroom.security.identity.dao;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.account.ResetToken;
import stroom.security.identity.authenticate.CredentialValidationResult;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.db.IdentityDbConnProvider;
import stroom.security.identity.db.jooq.tables.records.AccountRecord;
import stroom.security.identity.exceptions.NoSuchUserException;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountAction;
import stroom.security.identity.shared.AccountChange;
import stroom.security.identity.shared.AccountFields;
import stroom.security.identity.shared.AccountResultPage;
import stroom.security.identity.shared.FindAccountRequest;
import stroom.util.ResultPageFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static stroom.security.identity.db.jooq.tables.Account.ACCOUNT;

@Singleton
public class AccountDaoImpl implements AccountDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AccountDaoImpl.class);

    private Account toAccount(final Record record) {
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
        account.setFailureCount(record.get(ACCOUNT.FAILURE_COUNT));
        account.setLastLoginMs(record.get(ACCOUNT.LAST_LOGIN_MS));
        account.setReactivatedMs(record.get(ACCOUNT.REACTIVATED_MS));
        account.setForcePasswordChange(
                record.get(ACCOUNT.FORCE_PASSWORD_CHANGE));
        account.setNeverExpires(record.get(ACCOUNT.NEVER_EXPIRES));
        account.setEnabled(record.get(ACCOUNT.ENABLED));
        account.setInactive(record.get(ACCOUNT.INACTIVE));
        account.setFailureLockedMs(record.get(ACCOUNT.FAILURE_LOCKED_MS));
        // The table records when the lock was applied; callers want to know when it ends. Working that out
        // here means a change to the configured duration takes effect on locks already in force, and keeps
        // every reader - including the browser, which cannot see configuration - on one answer.
        account.setFailureLockedUntilMs(lockEndsAt(record.get(ACCOUNT.FAILURE_LOCKED_MS)));
        return account;
    }

    /**
     * When a lock applied at the given time is due to end, or null if it is not due to end at all.
     * <p>
     * A configured duration of zero means a lock does not lapse. Null is how the shared account object
     * already represents that, so the two agree without either needing to know about the setting.
     * </p>
     */
    private Long lockEndsAt(final Long lockedMs) {
        if (lockedMs == null) {
            return null;
        }
        final long durationMs = lockDurationMs();
        return durationMs > 0
                ? lockedMs + durationMs
                : null;
    }

    private long lockDurationMs() {
        return identityConfigProvider.get().getFailedLoginLockDuration().toMillis();
    }

    private static final Map<String, Field<?>> FIELD_MAP = Map.ofEntries(
            Map.entry("id", ACCOUNT.ID),
            Map.entry("version", ACCOUNT.VERSION),
            Map.entry("createTimeMs", ACCOUNT.CREATE_TIME_MS),
            Map.entry("updateTimeMs", ACCOUNT.UPDATE_TIME_MS),
            Map.entry("createUser", ACCOUNT.CREATE_USER),
            Map.entry("updateUser", ACCOUNT.UPDATE_USER),
            Map.entry(AccountFields.FIELD_NAME_USER_ID, ACCOUNT.USER_ID),
            Map.entry(AccountFields.FIELD_NAME_EMAIL, ACCOUNT.EMAIL),
            Map.entry(AccountFields.FIELD_NAME_FIRST_NAME, ACCOUNT.FIRST_NAME),
            Map.entry(AccountFields.FIELD_NAME_LAST_NAME, ACCOUNT.LAST_NAME),
            Map.entry(AccountFields.FIELD_NAME_COMMENTS, ACCOUNT.COMMENTS),
            Map.entry("loginCount", ACCOUNT.LOGIN_COUNT),
            Map.entry(AccountFields.FIELD_NAME_FAILURE_COUNT, ACCOUNT.FAILURE_COUNT),
            Map.entry(AccountFields.FIELD_NAME_LAST_LOGIN_MS, ACCOUNT.LAST_LOGIN_MS),
            Map.entry("reactivatedMs", ACCOUNT.REACTIVATED_MS),
            Map.entry("forcePasswordChange", ACCOUNT.FORCE_PASSWORD_CHANGE),
            Map.entry("neverExpires", ACCOUNT.NEVER_EXPIRES),
            Map.entry(AccountFields.FIELD_NAME_ENABLED, ACCOUNT.ENABLED),
            Map.entry(AccountFields.FIELD_NAME_INACTIVE, ACCOUNT.INACTIVE),
            Map.entry(AccountFields.FIELD_NAME_LOCKED, ACCOUNT.FAILURE_LOCKED_MS));

    private final Provider<IdentityConfig> identityConfigProvider;
    private final IdentityDbConnProvider identityDbConnProvider;
    private final ExpressionMapperFactory expressionMapperFactory;

    @Inject
    public AccountDaoImpl(final Provider<IdentityConfig> identityConfigProvider,
                          final IdentityDbConnProvider identityDbConnProvider,
                          final ExpressionMapperFactory expressionMapperFactory) {
        this.identityConfigProvider = identityConfigProvider;
        this.identityDbConnProvider = identityDbConnProvider;
        this.expressionMapperFactory = expressionMapperFactory;
    }

    /**
     * Built per search rather than once, because the lock filter has to compare an expiry against the
     * current time. Held as a field it would compare against whenever the DAO happened to be constructed.
     * Registering a handful of handlers is nothing beside the query that follows.
     */
    private ExpressionMapper expressionMapper(final long nowMs) {
        return expressionMapperFactory.create()
                .map(AccountFields.FIELD_USER_ID, ACCOUNT.USER_ID, String::valueOf)
                .map(AccountFields.FIELD_FIRST_NAME, ACCOUNT.FIRST_NAME, String::valueOf)
                .map(AccountFields.FIELD_LAST_NAME, ACCOUNT.LAST_NAME, String::valueOf)
                .map(AccountFields.FIELD_EMAIL, ACCOUNT.EMAIL, String::valueOf)
                .map(AccountFields.FIELD_COMMENTS, ACCOUNT.COMMENTS, String::valueOf)
                .map(AccountFields.FIELD_ENABLED, ACCOUNT.ENABLED, Boolean::valueOf)
                .map(AccountFields.FIELD_INACTIVE, ACCOUNT.INACTIVE, Boolean::valueOf)
                .map(AccountFields.FIELD_LOCKED, lockInForce(nowMs), Boolean::valueOf);
    }

    /**
     * Whether a failure lock is actually barring the account, rather than merely recorded against it.
     * <p>
     * The SQL half of the rule that {@code Account.isLocked()} applies in Java. Filtering has to agree with
     * what the screen shows, and the stored flag outlives the lock because it is only cleared lazily, on the
     * next sign in attempt.
     * </p>
     */
    private Field<Boolean> lockInForce(final long nowMs) {
        final long durationMs = lockDurationMs();
        if (durationMs <= 0) {
            // No lock lapses, so having been locked at all is being locked now.
            return DSL.field(ACCOUNT.FAILURE_LOCKED_MS.isNotNull());
        }
        return DSL.field(ACCOUNT.FAILURE_LOCKED_MS.isNotNull()
                .and(ACCOUNT.FAILURE_LOCKED_MS.plus(durationMs).gt(nowMs)));
    }

    @Override
    public AccountResultPage list() {
        final TableField<AccountRecord, String> orderByUserIdField =
                ACCOUNT.USER_ID;
        final List<Account> list = JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .selectFrom(ACCOUNT)
                        .orderBy(orderByUserIdField)
                        .fetch())
                .map(this::toAccount);
        return ResultPageFactory.createUnboundedList(list, AccountResultPage::new);
    }

    @Override
    public ResultPage<Account> search(final FindAccountRequest request) {
        final Condition filterConditions = NullSafe.getOrElseGet(
                expressionMapper(System.currentTimeMillis()),
                mapper -> mapper.apply(request.getExpression()),
                DSL::trueCondition);
        // Sort on user_id if no sort supplied
        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, request, ACCOUNT.USER_ID);
        final int limit = JooqUtil.getLimit(request.getPageRequest(), true);
        final int offset = JooqUtil.getOffset(request.getPageRequest());

        return JooqUtil.contextResult(identityDbConnProvider, context -> {
            final List<Account> accounts = context
                    .select(ACCOUNT.asterisk())
                    .from(ACCOUNT)
                    .where(filterConditions)
                    .orderBy(orderFields)
                    .offset(offset)
                    .limit(limit)
                    .fetch()
                    .map(this::toAccount);
            return ResultPage.createCriterialBasedList(accounts, request);
        });
    }

    @Override
    public Account create(final Account account, final String password) {
        // Everything written is listed here, so that what a new account starts out as is something you
        // can read rather than infer. Deliberately absent, and so left as the column defaults:
        //   id                        assigned by the database
        //   password_last_changed_ms  null until the password is first changed, which
        //                             getPasswordLastChangedMs reads as 'never, so use the create time'
        //   reset_token_hash          no password reset has been asked for yet
        //   reset_token_expiry_ms     ditto
        //   reset_email_requested_ms  ditto
        final Integer id;
        try {
            id = JooqUtil.contextResult(identityDbConnProvider, context -> context
                    .insertInto(ACCOUNT)
                    .set(ACCOUNT.VERSION, 1)
                    .set(ACCOUNT.CREATE_TIME_MS, account.getCreateTimeMs())
                    .set(ACCOUNT.CREATE_USER, account.getCreateUser())
                    .set(ACCOUNT.UPDATE_TIME_MS, account.getUpdateTimeMs())
                    .set(ACCOUNT.UPDATE_USER, account.getUpdateUser())
                    .set(ACCOUNT.USER_ID, account.getUserId())
                    .set(ACCOUNT.EMAIL, emailOrNull(account.getEmail()))
                    .set(ACCOUNT.PASSWORD_HASH, hashPassword(password))
                    .set(ACCOUNT.FIRST_NAME, account.getFirstName())
                    .set(ACCOUNT.LAST_NAME, account.getLastName())
                    .set(ACCOUNT.COMMENTS, account.getComments())
                    .set(ACCOUNT.FAILURE_COUNT, account.getFailureCount())
                    // Written by an administrator's edit as well as by the login path: making an account
                    // active again stamps this so the maintenance job does not immediately deactivate it
                    // in the window before the next successful login.
                    .set(ACCOUNT.REACTIVATED_MS, account.getReactivatedMs())
                    .set(ACCOUNT.FORCE_PASSWORD_CHANGE, account.isForcePasswordChange())
                    .set(ACCOUNT.NEVER_EXPIRES, account.isNeverExpires())
                    .set(ACCOUNT.ENABLED, account.isEnabled())
                    .set(ACCOUNT.INACTIVE, account.isInactive())
                    .set(ACCOUNT.FAILURE_LOCKED_MS, account.getFailureLockedMs())
                    .returning(ACCOUNT.ID)
                    .fetchOne(ACCOUNT.ID));
        } catch (final RuntimeException e) {
            throw describeIfDuplicate(account, e);
        }

        // Read back rather than echoing what we were given, so the caller sees what was actually stored.
        return get(id).orElseThrow(() ->
                new RuntimeException("Account " + account.getUserId() + " could not be read back after "
                                     + "being created"));
    }

    @Override
    public void recordSuccessfulLogin(final String userId) {
        // A successful sign in ends the lockout, so all three lock columns are cleared together.
        //
        // The flag has to be cleared alongside the expiry, not left behind it. A set flag with no expiry is
        // how a lock that never lapses is stored, so clearing only the expiry would turn a lock that had
        // already lapsed into a permanent one. That is reachable: a certificate sign in is admitted once the
        // window has passed, without the lazy auto-unlock above having run.
        JooqUtil.context(identityDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.VERSION, ACCOUNT.VERSION.plus(1))
                .set(ACCOUNT.FAILURE_COUNT, 0)
                .set(ACCOUNT.FAILURE_LOCKED_MS, (Long) null)
                .set(ACCOUNT.REACTIVATED_MS, (Long) null)
                .set(ACCOUNT.LOGIN_COUNT,
                        ACCOUNT.LOGIN_COUNT.plus(1))
                .set(ACCOUNT.LAST_LOGIN_MS, System.currentTimeMillis())
                .where(ACCOUNT.USER_ID.eq(userId))
                .execute());
    }

    @Override
    public void reactivateAccount(final String userId) {
        // Only the inactive flag is cleared. We deliberately leave LOCKED, ENABLED and the password
        // alone as reactivation is not a substitute for unlocking or re-enabling an account.
        // REACTIVATED_MS is set for the same reason that AccountServiceImpl.update sets it when an
        // administrator makes an account active, i.e. to stop the Account Maintenance job immediately
        // deactivating the account again in the window before the caller records a successful login
        // and LAST_LOGIN_MS takes over that job.
        final int count = JooqUtil.contextResult(identityDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.VERSION, ACCOUNT.VERSION.plus(1))
                .set(ACCOUNT.INACTIVE, false)
                .set(ACCOUNT.REACTIVATED_MS, System.currentTimeMillis())
                .where(ACCOUNT.USER_ID.eq(userId))
                .execute());

        if (count == 0) {
            throw new NoSuchUserException("Cannot reactivate this account because this user does not exist!");
        }
    }

    @Override
    public CredentialValidationResult validateCredentials(final String userId, final String password) {
        if (Strings.isNullOrEmpty(userId)
            || Strings.isNullOrEmpty(password)) {
            return new CredentialValidationResult(
                    false, true, false, false, false);
        }

        // Clear a lock whose window has passed, so the account is usable again on this attempt. A lock is
        // only released here once the configured duration has run from the moment it was applied, so with
        // a duration of zero nothing is ever cleared here and a lock needs an administrator.
        // Cluster-safe, since the row is the shared state.
        final long durationMs = lockDurationMs();
        if (durationMs > 0) {
            JooqUtil.context(identityDbConnProvider, context -> context
                    .update(ACCOUNT)
                    .set(ACCOUNT.VERSION, ACCOUNT.VERSION.plus(1))
                    .set(ACCOUNT.FAILURE_COUNT, 0)
                    .set(ACCOUNT.FAILURE_LOCKED_MS, (Long) null)
                    .where(ACCOUNT.USER_ID.eq(userId))
                    .and(ACCOUNT.FAILURE_LOCKED_MS.isNotNull())
                    .and(ACCOUNT.FAILURE_LOCKED_MS.plus(durationMs).le(System.currentTimeMillis()))
                    .execute());
        }

        // Is this is a login by the default local 'admin' account, then that should have already been created
        // by AdminAccountBootstrap
        final Optional<AccountRecord> optRecord = JooqUtil.contextResult(identityDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(ACCOUNT.USER_ID.eq(userId))
                .fetchOptional());

        if (optRecord.isEmpty()) {
            LOGGER.debug("Request to log in with invalid user id: {}", userId);
            // Spend the same time a real bcrypt verify would, so the credential check takes the same time
            // whether or not the account exists.
            PasswordHashUtil.fakeCheck(password);
            return new CredentialValidationResult(
                    false,
                    true,
                    false,
                    false,
                    false);
        }

        final AccountRecord record = optRecord.get();
        final boolean isDisabled = !record.getEnabled();
        final boolean isInactive = record.getInactive();
        // A lock time survives here only if the auto-unlock above did not clear it, which means either the
        // lock has not run its course or no lock lapses at all. Everywhere else must use
        // Account.isLocked(), which applies the configured duration.
        final boolean isLocked = record.getFailureLockedMs() != null;

        if (isDisabled || isLocked) {
            // The password is not checked at all. Neither state can be talked out of with a correct
            // password, so there is nothing to learn from checking - and anything that knows whether the
            // password was right is something that can be made to tell. That matters most while an account
            // is locked, because failures are deliberately not counted then, so a check here would be one
            // that costs an attacker nothing.
            //
            // Still spend the time a real verify would, for the same reason the unknown-account path above
            // does: otherwise a fast refusal reports the account's state to anyone who asks.
            PasswordHashUtil.fakeCheck(password);
            return new CredentialValidationResult(false, false, isLocked, isDisabled, isInactive);
        }

        // An inactive account is checked, unlike the two above, because a correct password is precisely
        // what earns it reactivation. Guessing at one is bounded by the lockout in the ordinary way, and
        // once that locks the account the branch above takes over.
        final boolean isPasswordCorrect = PasswordHashUtil.checkPassword(password, record.getPasswordHash());

        return new CredentialValidationResult(
                isPasswordCorrect, false, isLocked, isDisabled, isInactive);
    }

    @Override
    public boolean incrementLoginFailures(final String userId) {
        boolean locked = false;

        final IdentityConfig identityConfig = identityConfigProvider.get();
        final Integer threshold = identityConfig.getFailedLoginLockThreshold();
        if (threshold != null) {
            final long lockedAt = System.currentTimeMillis();
            JooqUtil.context(identityDbConnProvider, context -> context
                    .update(ACCOUNT)
                    .set(ACCOUNT.VERSION, ACCOUNT.VERSION.plus(1))
                    // Stamp the lock time FIRST so it reads the pre-update count (MySQL evaluates SET left
                    // to right). Only a failure that newly locks an account stamps it, and an account
                    // already locked keeps the time it was applied, so continued guessing cannot push the
                    // end of the lock further out.
                    //
                    // A failed sign in may only ever add a lock, never clear one, and that now holds by
                    // construction rather than by expression: the only value this can write is a time where
                    // there was none, so it cannot release a lock however far below the threshold the count
                    // happens to be.
                    .set(ACCOUNT.FAILURE_LOCKED_MS,
                            DSL.when(ACCOUNT.FAILURE_LOCKED_MS.isNull()
                                            .and(ACCOUNT.FAILURE_COUNT.plus(1).ge(threshold)),
                                    DSL.val(lockedAt))
                                    .otherwise(ACCOUNT.FAILURE_LOCKED_MS))
                    .set(ACCOUNT.FAILURE_COUNT,
                            ACCOUNT.FAILURE_COUNT.plus(1))
                    .where(ACCOUNT.USER_ID.eq(userId))
                    .execute());

            // Read back whether the account is now barred, rather than only whether a lock exists: with a
            // zero duration they are the same, but otherwise a lock that has already lapsed is not one.
            locked = JooqUtil.contextResult(identityDbConnProvider, context -> context
                            .select(lockInForce(System.currentTimeMillis()))
                            .from(ACCOUNT)
                            .where(ACCOUNT.USER_ID.eq(userId))
                            .fetchOptional())
                    .map(Record1::value1)
                    .orElse(false);
        } else {
            JooqUtil.context(identityDbConnProvider, context -> context
                    .update(ACCOUNT)
                    .set(ACCOUNT.FAILURE_COUNT,
                            ACCOUNT.FAILURE_COUNT.plus(1))
                    .where(ACCOUNT.USER_ID.eq(userId))
                    .execute());
        }

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
                .map(this::toAccount);
    }

    @Override
    public Optional<Account> getByEmail(final String email) {
        if (Strings.isNullOrEmpty(email)) {
            return Optional.empty();
        }

        final List<Account> accounts = JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .selectFrom(ACCOUNT)
                        .where(ACCOUNT.EMAIL.eq(email))
                        .fetch())
                .map(this::toAccount);

        if (accounts.size() > 1) {
            // A unique index makes this impossible, but refuse to guess rather than reset an arbitrary
            // one of them if that index is ever missing.
            LOGGER.error("Found {} accounts with the email address {}. Email addresses must be unique.",
                    accounts.size(), email);
            return Optional.empty();
        }

        return accounts.stream().findFirst();
    }


    @Override
    public void applyChange(final int accountId,
                            final AccountChange change,
                            final String updateUser,
                            final long updateTimeMs) {
        // Only what the change actually asks for is written. Columns the change says nothing about are left
        // alone, so a save cannot revert a writer it never knew about - which is why this does not test the
        // account's version. Guarding on the version would buy nothing here and would cost a great deal:
        // failed logins and successful ones both write to the account, so an account being hammered or one
        // used by an automated client would fail every administrator save, denying the lock and disable
        // controls exactly when they are wanted.
        //
        // The password and the password reset state are never written here either. They are owned by the
        // methods that exist for them, so an update leaves them intact.
        final Map<Field<?>, Object> values = new LinkedHashMap<>();
        values.put(ACCOUNT.VERSION, ACCOUNT.VERSION.plus(1));
        values.put(ACCOUNT.UPDATE_USER, updateUser);
        values.put(ACCOUNT.UPDATE_TIME_MS, updateTimeMs);

        putIfSpecified(values, ACCOUNT.USER_ID, change.getUserId());
        // An email address the change does mention but leaves blank is an address the administrator has
        // cleared, so it is written, but written as no address rather than as an empty string.
        if (change.getEmail() != null) {
            values.put(ACCOUNT.EMAIL, emailOrNull(change.getEmail()));
        }
        putIfSpecified(values, ACCOUNT.FIRST_NAME, change.getFirstName());
        putIfSpecified(values, ACCOUNT.LAST_NAME, change.getLastName());
        putIfSpecified(values, ACCOUNT.COMMENTS, change.getComments());
        putIfSpecified(values, ACCOUNT.NEVER_EXPIRES, change.getNeverExpires());
        putIfSpecified(values, ACCOUNT.FORCE_PASSWORD_CHANGE, change.getForcePasswordChange());

        putActions(values, change, updateTimeMs);

        final int count;
        try {
            count = JooqUtil.contextResult(identityDbConnProvider, context -> context
                    .update(ACCOUNT)
                    .set(values)
                    .where(ACCOUNT.ID.eq(accountId))
                    .execute());
        } catch (final RuntimeException e) {
            throw describeIfDuplicate(accountId, change, e);
        }

        if (count == 0) {
            throw new NoSuchUserException("Account with id = " + accountId + " does not exist");
        }
    }

    /**
     * A blank email address is no email address at all, and is stored as null. Stored as an empty string it
     * would be a value like any other: the unique index on the column would then refuse the second account
     * left without an address, where any number of them may have none. Every write of the column goes
     * through here, so a blank one cannot reach the database whichever route it arrives by.
     */
    private static String emailOrNull(final String email) {
        return NullSafe.isBlankString(email)
                ? null
                : email;
    }

    private static void putIfSpecified(final Map<Field<?>, Object> values,
                                       final Field<?> field,
                                       final Object value) {
        // Null means the change does not mention this column. An empty string is a value, and will clear it.
        if (value != null) {
            values.put(field, value);
        }
    }

    /**
     * Turns the state an administrator asked for into the columns that express it. Each action is absolute,
     * so applying one twice leaves the same result and applying one never depends on what the account looked
     * like when the request was made.
     */
    private static void putActions(final Map<Field<?>, Object> values,
                                   final AccountChange change,
                                   final long updateTimeMs) {
        if (change.hasAction(AccountAction.UNLOCK)) {
            values.put(ACCOUNT.FAILURE_LOCKED_MS, null);
            // Unlocking has to clear the count. Left at or above the lock threshold, the very next failed
            // attempt re-locks the account and the unlock achieves nothing.
            values.put(ACCOUNT.FAILURE_COUNT, 0);
        }
        if (change.hasAction(AccountAction.ENABLE)) {
            values.put(ACCOUNT.ENABLED, true);
        }
        if (change.hasAction(AccountAction.DISABLE)) {
            values.put(ACCOUNT.ENABLED, false);
        }
        if (change.hasAction(AccountAction.REACTIVATE)) {
            values.put(ACCOUNT.INACTIVE, false);
        }

        // Making an account usable again stamps the reactivation time so the account maintenance job does
        // not immediately make it inactive again in the window before the next successful sign in, after
        // which LAST_LOGIN_MS takes over as the measure of inactivity.
        //
        // Deliberately not stamped by UNLOCK. A lock has no bearing on whether an account is being used, so
        // unlocking one would otherwise extend the dormancy grace period for an unrelated reason.
        if (change.hasAction(AccountAction.ENABLE) || change.hasAction(AccountAction.REACTIVATE)) {
            values.put(ACCOUNT.REACTIVATED_MS, updateTimeMs);
        }
    }

    @Override
    public void delete(final int id) {
        JooqUtil.context(identityDbConnProvider, context -> context
                .deleteFrom(ACCOUNT)
                .where(ACCOUNT.ID.eq(id))
                .execute());
    }

    @Override
    public Optional<Account> get(final int id) {
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .selectFrom(ACCOUNT)
                        .where(ACCOUNT.ID.eq(id))
                        .fetchOptional())
                .map(this::toAccount);
    }


    @Override
    public void changePassword(final String userId,
                               final String newPassword,
                               final boolean forcePasswordChange) {
        final String newPasswordHash = PasswordHashUtil.hash(newPassword);

        final int count = JooqUtil.contextResult(identityDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.PASSWORD_HASH, newPasswordHash)
                .set(ACCOUNT.PASSWORD_LAST_CHANGED_MS,
                        System.currentTimeMillis())
                .set(ACCOUNT.FORCE_PASSWORD_CHANGE, forcePasswordChange)
                // Any password change invalidates an outstanding reset link.
                .set(ACCOUNT.RESET_TOKEN_HASH, (String) null)
                .set(ACCOUNT.RESET_TOKEN_EXPIRY_MS, (Long) null)
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
                .set(ACCOUNT.FAILURE_LOCKED_MS, (Long) null)
                .set(ACCOUNT.INACTIVE, false)
                .set(ACCOUNT.ENABLED, true)
                .set(ACCOUNT.FAILURE_COUNT, 0)
                // Any password change invalidates an outstanding reset link.
                .set(ACCOUNT.RESET_TOKEN_HASH, (String) null)
                .set(ACCOUNT.RESET_TOKEN_EXPIRY_MS, (Long) null)
                .where(ACCOUNT.USER_ID.eq(userId))
                .execute());

        if (count == 0) {
            throw new NoSuchUserException("Cannot reset this password because this user does not exist!");
        }
    }

    @Override
    public boolean unlockAndSetPassword(final String userId,
                                        final String newPassword,
                                        final String expectedTokenHash) {
        if (Strings.isNullOrEmpty(expectedTokenHash)) {
            return false;
        }

        final String newPasswordHash = PasswordHashUtil.hash(newPassword);

        // INACTIVE is deliberately not cleared here, unlike resetPassword. Proving control of the
        // account's email address is enough to clear a lock caused by failed logins, but an inactive
        // account may only be made active again by an actual successful authentication.
        //
        // Matching on the token hash as well as the user makes this the point at which a reset link is
        // consumed. Two requests using the same link cannot both succeed because the first clears the
        // hash, so the second matches no rows.
        final int count = JooqUtil.contextResult(identityDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.PASSWORD_HASH, newPasswordHash)
                .set(ACCOUNT.PASSWORD_LAST_CHANGED_MS,
                        System.currentTimeMillis())
                .set(ACCOUNT.FORCE_PASSWORD_CHANGE, false)
                .set(ACCOUNT.FAILURE_LOCKED_MS, (Long) null)
                .set(ACCOUNT.FAILURE_COUNT, 0)
                .set(ACCOUNT.RESET_TOKEN_HASH, (String) null)
                .set(ACCOUNT.RESET_TOKEN_EXPIRY_MS, (Long) null)
                .where(ACCOUNT.USER_ID.eq(userId))
                .and(ACCOUNT.RESET_TOKEN_HASH.eq(expectedTokenHash))
                .execute());

        return count > 0;
    }

    @Override
    public Optional<Long> getPasswordLastChangedMs(final String userId) {
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .select(ACCOUNT.PASSWORD_LAST_CHANGED_MS, ACCOUNT.CREATE_TIME_MS)
                        .from(ACCOUNT)
                        .where(ACCOUNT.USER_ID.eq(userId))
                        .fetchOptional())
                // A password that has never been changed has no changed time, so fall back to the
                // account create time in the same way that needsPasswordChange does.
                .map(record -> Objects.requireNonNullElse(
                        record.get(ACCOUNT.PASSWORD_LAST_CHANGED_MS),
                        record.get(ACCOUNT.CREATE_TIME_MS)));
    }

    @Override
    public boolean tryRecordResetEmailRequest(final String userId,
                                              final long requestTimeMs,
                                              final long earliestPreviousRequestMs) {
        // One conditional update rather than a read then a write, so that concurrent requests, including
        // ones on other nodes, cannot all decide they are allowed.
        final int count = JooqUtil.contextResult(identityDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.RESET_EMAIL_REQUESTED_MS, requestTimeMs)
                .where(ACCOUNT.USER_ID.eq(userId))
                .and(ACCOUNT.RESET_EMAIL_REQUESTED_MS.isNull()
                        .or(ACCOUNT.RESET_EMAIL_REQUESTED_MS.le(earliestPreviousRequestMs)))
                .execute());

        return count > 0;
    }

    @Override
    public void setPasswordResetToken(final String userId, final ResetToken resetToken) {
        final int count = JooqUtil.contextResult(identityDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.RESET_TOKEN_HASH, resetToken.hash())
                .set(ACCOUNT.RESET_TOKEN_EXPIRY_MS, resetToken.expiryMs())
                .where(ACCOUNT.USER_ID.eq(userId))
                .execute());

        if (count == 0) {
            throw new NoSuchUserException("Cannot issue a reset token because this user does not exist!");
        }
    }

    @Override
    public Optional<ResetToken> getPasswordResetToken(final String userId) {
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .select(ACCOUNT.RESET_TOKEN_HASH, ACCOUNT.RESET_TOKEN_EXPIRY_MS)
                        .from(ACCOUNT)
                        .where(ACCOUNT.USER_ID.eq(userId))
                        .fetchOptional())
                // No hash means no link is outstanding, even if the account row exists.
                .filter(record -> record.get(ACCOUNT.RESET_TOKEN_HASH) != null)
                .map(record -> new ResetToken(
                        record.get(ACCOUNT.RESET_TOKEN_HASH),
                        // A hash is only ever written together with an expiry; treat a missing one as
                        // already expired so it fails closed.
                        Objects.requireNonNullElse(record.get(ACCOUNT.RESET_TOKEN_EXPIRY_MS), 0L)));
    }

    @Override
    public boolean needsPasswordChange(final String userId,
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

    /**
     * Callers are expected to check for a clash before writing, but two of them can pass that check at
     * the same time and only one will get the row. The unique indexes are what actually decide, so turn
     * what they throw into the same message the caller would have given, rather than letting a raw
     * 'Duplicate entry ... for key ...' reach a user.
     * <p>
     * Which index was hit is worked out by looking, rather than by reading the database's message, which
     * is not ours to depend on.
     * </p>
     *
     * @return The exception to throw, which is the original if it was not a duplicate key.
     */
    private RuntimeException describeIfDuplicate(final Account account, final RuntimeException e) {
        return describeIfDuplicate(account.getId(), account.getUserId(), account.getEmail(), e);
    }

    private RuntimeException describeIfDuplicate(final int accountId,
                                                 final AccountChange change,
                                                 final RuntimeException e) {
        return describeIfDuplicate(accountId, change.getUserId(), change.getEmail(), e);
    }

    private RuntimeException describeIfDuplicate(final Integer accountId,
                                                 final String userId,
                                                 final String email,
                                                 final RuntimeException e) {
        if (!JooqUtil.isDuplicateKeyException(e)) {
            return e;
        }

        // An account being updated is allowed to keep its own user id and email address.
        if (!Strings.isNullOrEmpty(email)
            && isUsedByAnotherAccount(getByEmail(email), accountId)) {
            return new RuntimeException(
                    "The email address '" + email + "' is already used by another account", e);
        }
        if (!Strings.isNullOrEmpty(userId)
            && isUsedByAnotherAccount(get(userId), accountId)) {
            return new RuntimeException(
                    "The user id '" + userId + "' is already used by another account", e);
        }

        return e;
    }

    private boolean isUsedByAnotherAccount(final Optional<Account> optExisting, final Integer accountId) {
        return optExisting
                .filter(existing -> !Objects.equals(existing.getId(), accountId))
                .isPresent();
    }

    private String hashPassword(final String password) {
        if (password == null) {
            return null;
        }
        return PasswordHashUtil.hash(password);
    }
}
