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

import com.google.common.base.Strings;
import event.logging.ObjectOutcome;
import org.apache.commons.lang3.Validate;
import org.jooq.*;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.LoginResult;
import stroom.authentication.config.AuthenticationConfig;
import stroom.authentication.exceptions.BadRequestException;
import stroom.authentication.exceptions.NoSuchUserException;
import stroom.authentication.impl.db.jooq.tables.records.AccountRecord;
import stroom.authentication.resources.user.v1.User;
import stroom.authentication.dao.UserDao;
import stroom.db.util.JooqUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static stroom.authentication.impl.db.jooq.tables.Account.ACCOUNT;

@Singleton
class UserDaoImpl implements UserDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserDaoImpl.class);

    private AuthDbConnProvider authDbConnProvider;
    private AuthenticationConfig config;
    private Clock clock;

    @Inject
    public UserDaoImpl(AuthenticationConfig config, AuthDbConnProvider authDbConnProvider) {
        this.config = config;
        this.authDbConnProvider = authDbConnProvider;
        this.clock = Clock.systemDefaultZone();
    }

    @Override
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public int create(User newUser, String creatingUsername) {
        newUser.setCreatedOn(UserMapper.toIso(Timestamp.from(Instant.now(clock))));
        newUser.setCreatedByUser(creatingUsername);
        newUser.setLoginCount(0);
        AccountRecord usersRecord = UserMapper.map(newUser);
        int id = JooqUtil.contextResult(authDbConnProvider, context -> {
            AccountRecord createdUser = context.newRecord(ACCOUNT, usersRecord);
            createdUser.store();
            return createdUser.getId();
        });
        return id;
    }

    @Override
    public void recordSuccessfulLogin(String email) {
        AccountRecord user = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(new Condition[]{ACCOUNT.EMAIL.eq(email)})
                .fetchOne());

        // We reset the failed login count if we have a successful login
        user.setLoginFailures(0);
        user.setReactivatedDate(null);
        user.setLoginCount(user.getLoginCount() + 1);
        user.setLastLogin(UserMapper.convertISO8601ToTimestamp(LocalDateTime.now(clock).toString()));
        JooqUtil.context(authDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(user)
                .where(new Condition[]{ACCOUNT.EMAIL.eq(user.getEmail())}).execute());
    }

//    @Override
    public LoginResult areCredentialsValid(String email, String password) {
        if (Strings.isNullOrEmpty(email)
                || Strings.isNullOrEmpty(password)) {
            throw new BadRequestException("Please provide both email and password");
        }

        final AccountRecord user = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(new Condition[]{ACCOUNT.EMAIL.eq(email)})
                .fetchOne());

        if (user == null) {
            LOGGER.debug("Request to log in with invalid username: " + email);
            return LoginResult.USER_DOES_NOT_EXIST;
        } else {
            boolean isPasswordCorrect = BCrypt.checkpw(password, user.getPasswordHash());
            boolean isDisabled = user.getState().equals(User.UserState.DISABLED.getStateText());
            boolean isInactive = user.getState().equals(User.UserState.INACTIVE.getStateText());
            boolean isLocked = user.getState().equals(User.UserState.LOCKED.getStateText());

            if (isLocked) {
                LOGGER.debug("Account {} tried to log in but it is locked.", email);
                return isPasswordCorrect ? LoginResult.LOCKED_GOOD_CREDENTIALS : LoginResult.LOCKED_BAD_CREDENTIALS;
            } else if (isDisabled) {
                LOGGER.debug("Account {} tried to log in but it is disabled.", email);
                return isPasswordCorrect ? LoginResult.DISABLED_GOOD_CREDENTIALS : LoginResult.DISABLED_BAD_CREDENTIALS;
            } else if (isInactive) {
                LOGGER.debug("Account {} tried to log in but it is inactive.", email);
                return isPasswordCorrect ? LoginResult.INACTIVE_GOOD_CREDENTIALS : LoginResult.INACTIVE_BAD_CREDENTIALS;
            } else {
                return isPasswordCorrect ? LoginResult.GOOD_CREDENTIALS : LoginResult.BAD_CREDENTIALS;
            }
        }
    }

    @Override
    public boolean incrementLoginFailures(String email) {
        AccountRecord user = (AccountRecord) JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom((Table) ACCOUNT)
                .where(new Condition[]{ACCOUNT.EMAIL.eq(email)})
                .fetchOne());

        // If the password is wrong we need to increment the failed login count,
        // check if we need to locked the account, and save.
        if (user.getLoginFailures() != null) {
            user.setLoginFailures(user.getLoginFailures() + 1);
        } else {
            user.setLoginFailures(1);
        }

        boolean shouldLock = user.getLoginFailures() >= this.config.getFailedLoginLockThreshold();

        if (shouldLock) {
            user.setState(User.UserState.LOCKED.getStateText());
        }

        JooqUtil.context(authDbConnProvider, context -> context
                .update((Table) ACCOUNT)
                .set(user)
                .where(new Condition[]{ACCOUNT.EMAIL.eq(email)}).execute());

        if (shouldLock) {
            LOGGER.debug("Account {} has had too many failed access attempts and is locked", email);
        }

        return shouldLock;
    }

    @Override
    public Optional<User> get(String email) {
        Optional<AccountRecord> userQuery = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(ACCOUNT.EMAIL.eq(email)).fetchOptional());

        return userQuery.map(UserMapper::map);
    }

    @Override
    public void update(User user) {
        AccountRecord usersRecord = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(ACCOUNT.EMAIL.eq(user.getEmail())).fetchSingle());

        AccountRecord updatedAccountRecord = UserMapper.updateUserRecordWithUser(user, usersRecord);

        JooqUtil.contextResult(authDbConnProvider, context -> context
            .update((Table) ACCOUNT)
               .set(updatedAccountRecord)
                .where(new Condition[]{ACCOUNT.ID.eq(user.getId())}).execute());
    }

    @Override
    public void delete(int id) {
        JooqUtil.context(authDbConnProvider, context -> context
                .deleteFrom((Table) ACCOUNT)
                .where(new Condition[]{ACCOUNT.ID.eq(id)}).execute());
    }

    @Override
    public Optional<User> get(int id) {
        Optional<AccountRecord> userQuery = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(ACCOUNT.ID.eq(id)).fetchOptional());

        return userQuery.map(usersRecord -> UserMapper.map(usersRecord));
    }

        @Override
        public String getAll() {
        TableField orderByEmailField = ACCOUNT.EMAIL;
        String usersAsJson = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .orderBy(orderByEmailField)
                .fetch()
                .formatJSON((new JSONFormat())
                        .header(false)
                        .recordFormat(JSONFormat.RecordFormat.OBJECT)));

        ObjectOutcome objectOutcome = new ObjectOutcome();
        event.logging.Object object = new event.logging.Object();
        object.setName("GetAllAccounts");
        objectOutcome.getObjects().add(object);
        return usersAsJson;
    }

    @Override
    public void changePassword(String email, String newPassword) {
        AccountRecord account = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(new Condition[]{ACCOUNT.EMAIL.eq(email)})
                .fetchOne());

        if (account == null) {
            throw new NoSuchUserException("Cannot change this password because this user does not exist!");
        }

        String newPasswordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        account.setPasswordHash(newPasswordHash);
        account.setPasswordLastChanged(Timestamp.from(clock.instant()));
        account.setForcePasswordChange(false);

        JooqUtil.context(authDbConnProvider, context -> context.update((Table) ACCOUNT)
                .set(account)
                .where(new Condition[]{ACCOUNT.EMAIL.eq(email)})
                .execute());
    }

    @Override
    public Boolean needsPasswordChange(String email, Duration mandatoryPasswordChangeDuration, boolean forcePasswordChangeOnFirstLogin) {
        Validate.notNull(email, "email must not be null");

        AccountRecord user = (AccountRecord) JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom((Table) ACCOUNT)
                .where(new Condition[]{ACCOUNT.EMAIL.eq(email)})
                .fetchOne());

        if (user == null) {
            throw new NoSuchUserException("Cannot check if this user needs a password change because this user does not exist!");
        }

        LocalDateTime passwordLastChanged = user.getPasswordLastChanged() == null ?
                user.getCreatedOn().toLocalDateTime() :
                user.getPasswordLastChanged().toLocalDateTime();
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(clock), ZoneId.systemDefault());
        Duration durationSinceLastPasswordChange = Duration.ofMinutes(passwordLastChanged.until(now, ChronoUnit.MINUTES));

        boolean thresholdBreached = durationSinceLastPasswordChange.compareTo(mandatoryPasswordChangeDuration) > 0;
        boolean isFirstLogin = user.getPasswordLastChanged() == null;

        if (thresholdBreached || (forcePasswordChangeOnFirstLogin && isFirstLogin) || user.getForcePasswordChange()) {
            LOGGER.debug("User {} needs a password change.", email);
            return true;
        } else return false;
    }

    @Override
    public int deactivateNewInactiveUsers(Duration neverUsedAccountDeactivationThreshold) {
        Timestamp activityThreshold = convertThresholdToTimestamp(neverUsedAccountDeactivationThreshold);

        Result<AccountRecord> candidatesForDeactivating = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(ACCOUNT.CREATED_ON.lessOrEqual(activityThreshold))
                // We are only going to deactivate enabled accounts
                .and(ACCOUNT.STATE.eq(User.UserState.ENABLED.getStateText()))
                // A 'new' user is one who has never logged in.
                .and(ACCOUNT.LAST_LOGIN.isNull())
                // We don't want to disable all accounts
                .and(ACCOUNT.NEVER_EXPIRES.ne(true)).fetch());

        List<Integer> usersToDeactivate = candidatesForDeactivating.stream()
                .filter(usersRecord ->
                        usersRecord.getReactivatedDate() == null
                                || usersRecord.getReactivatedDate().before(activityThreshold))
                .map(usersRecord -> usersRecord.getId())
                .collect(Collectors.toList());

        JooqUtil.contextResult(authDbConnProvider, context -> context.
                update(ACCOUNT).set(ACCOUNT.STATE, User.UserState.INACTIVE.getStateText())
                .where(ACCOUNT.ID.in(usersToDeactivate))
                .execute());

        return usersToDeactivate.size();
    }

    @Override
    public int deactivateInactiveUsers(Duration unusedAccountDeactivationThreshold) {
        Timestamp activityThreshold = convertThresholdToTimestamp(unusedAccountDeactivationThreshold);

        Result<AccountRecord> candidatesForDeactivating = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(ACCOUNT.LAST_LOGIN.lessOrEqual(activityThreshold))
                // If we have a reactivated date we'll use that instead of the created_on date.
                // We are only going to deactivate enabled accounts
                .and(ACCOUNT.STATE.eq(User.UserState.ENABLED.getStateText()))
                // We don't want to disable admin because that could lock the users out of the system
                .and(ACCOUNT.NEVER_EXPIRES.ne(true)).fetch());

        List<Integer> usersToDeactivate = candidatesForDeactivating.stream()
                .filter(usersRecord ->
                        usersRecord.getReactivatedDate() == null
                                || usersRecord.getReactivatedDate().before(activityThreshold))
                .map(usersRecord -> usersRecord.getId())
                .collect(Collectors.toList());

        JooqUtil.context(authDbConnProvider, context -> context.
                update(ACCOUNT).set(ACCOUNT.STATE, User.UserState.INACTIVE.getStateText())
                .where(ACCOUNT.ID.in(usersToDeactivate))
                .execute());

        return usersToDeactivate.size();
    }

    @Override
    public Result<Record13<Integer, String, String, String, String, String, Integer, Integer, Timestamp, Timestamp, String, Timestamp, String>> searchUsersForDisplay(String email){
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(ACCOUNT.ID,
                        ACCOUNT.EMAIL,
                        ACCOUNT.FIRST_NAME,
                        ACCOUNT.LAST_NAME,
                        ACCOUNT.COMMENTS,
                        ACCOUNT.STATE,
                        ACCOUNT.LOGIN_FAILURES,
                        ACCOUNT.LOGIN_COUNT,
                        ACCOUNT.LAST_LOGIN,
                        ACCOUNT.UPDATED_ON,
                        ACCOUNT.UPDATED_BY_USER,
                        ACCOUNT.CREATED_ON,
                        ACCOUNT.CREATED_BY_USER)
                .from(ACCOUNT)
                .where(new Condition[]{ACCOUNT.EMAIL.contains(email)})
                .fetch());
    }

    @Override
    public boolean exists(String id) {
        AccountRecord result = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(ACCOUNT)
                .where(ACCOUNT.EMAIL.eq(id))
                .fetchOne());
        return result != null;
    }

    private Timestamp convertThresholdToTimestamp(Duration duration) {
        Instant now = Instant.now(clock);
        Instant thresholdInstant = now.minus(duration);
        return Timestamp.from(thresholdInstant);
    }
}
