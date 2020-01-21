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

package stroom.auth.daos;

import com.google.common.base.Strings;
import org.apache.commons.lang3.Validate;
import org.jooq.Condition;
import org.jooq.Result;
import org.jooq.Table;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.auth.AuthDbConnProvider;
import stroom.auth.config.AuthenticationConfig;
import stroom.auth.exceptions.BadRequestException;
import stroom.auth.exceptions.NoSuchUserException;
import stroom.auth.resources.user.v1.User;
import stroom.auth.db.Tables;
import stroom.auth.db.tables.records.UsersRecord;
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

import static stroom.auth.db.Tables.USERS;

@Singleton
public class UserDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserDao.class);

    private AuthDbConnProvider authDbConnProvider;
    private AuthenticationConfig config;
    private Clock clock;

    @Inject
    public UserDao(AuthenticationConfig config, AuthDbConnProvider authDbConnProvider) {
        this.config = config;
        this.authDbConnProvider = authDbConnProvider;
        this.clock = Clock.systemDefaultZone();
    }

    public void setClock(Clock clock){
        this.clock = clock;
    }

    public int create(User newUser, String creatingUsername){
        newUser.setCreatedOn(UserMapper.toIso(Timestamp.from(Instant.now(clock))));
        newUser.setCreatedByUser(creatingUsername);
        newUser.setLoginCount(0);
        UsersRecord usersRecord = UserMapper.map(newUser);
        UsersRecord createdUser = JooqUtil.contextResult(authDbConnProvider, context -> context.newRecord(USERS, usersRecord));
        createdUser.store();
        return createdUser.getId();
    }

    public void recordSuccessfulLogin(String email) {
        UsersRecord user = (UsersRecord)JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom((Table) USERS)
                .where(new Condition[]{USERS.EMAIL.eq(email)})
                .fetchOne());

        // We reset the failed login count if we have a successful login
        user.setLoginFailures(0);
        user.setReactivatedDate(null);
        user.setLoginCount(user.getLoginCount() + 1);
        user.setLastLogin(UserMapper.convertISO8601ToTimestamp(LocalDateTime.now(clock).toString()));
        JooqUtil.context(authDbConnProvider, context -> context
                .update((Table) USERS)
                .set(user)
                .where(new Condition[]{USERS.EMAIL.eq(user.getEmail())}).execute());
    }

    public LoginResult areCredentialsValid(String email, String password) {
        if (Strings.isNullOrEmpty(email)
                || Strings.isNullOrEmpty(password)) {
            throw new BadRequestException("Please provide both email and password");
        }

        UsersRecord user = (UsersRecord) JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom((Table) USERS)
                .where(new Condition[]{USERS.EMAIL.eq(email)})
                .fetchOne());

        if (user == null) {
            LOGGER.debug("Request to log in with invalid username: " + email);
            return LoginResult.USER_DOES_NOT_EXIST;
        }
        else {
            boolean isPasswordCorrect = BCrypt.checkpw(password, user.getPasswordHash());
            boolean isDisabled = user.getState().equals(User.UserState.DISABLED.getStateText());
            boolean isInactive = user.getState().equals(User.UserState.INACTIVE.getStateText());
            boolean isLocked = user.getState().equals(User.UserState.LOCKED.getStateText());

            if (isLocked) {
                LOGGER.debug("Account {} tried to log in but it is locked.", email);
                return isPasswordCorrect ? LoginResult.LOCKED_GOOD_CREDENTIALS : LoginResult.LOCKED_BAD_CREDENTIALS;
            }
            else if (isDisabled) {
                LOGGER.debug("Account {} tried to log in but it is disabled.", email);
                return isPasswordCorrect ? LoginResult.DISABLED_GOOD_CREDENTIALS : LoginResult.DISABLED_BAD_CREDENTIALS;
            }
            else if (isInactive) {
                LOGGER.debug("Account {} tried to log in but it is inactive.", email);
                return isPasswordCorrect ? LoginResult.INACTIVE_GOOD_CREDENTIALS : LoginResult.INACTIVE_BAD_CREDENTIALS;
            }
            else {
                return isPasswordCorrect ? LoginResult.GOOD_CREDENTIALS : LoginResult.BAD_CREDENTIALS;
            }
        }
    }

    public boolean incrementLoginFailures(String email) {
        UsersRecord user = (UsersRecord) JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom((Table) USERS)
                .where(new Condition[]{USERS.EMAIL.eq(email)})
                .fetchOne());

        // If the password is wrong we need to increment the failed login count,
        // check if we need to locked the account, and save.
        if(user.getLoginFailures() != null) {
            user.setLoginFailures(user.getLoginFailures() + 1);
        }
        else {
            user.setLoginFailures(1);
        }

        boolean shouldLock = user.getLoginFailures() >= this.config.getFailedLoginLockThreshold();

        if (shouldLock) {
            user.setState(User.UserState.LOCKED.getStateText());
        }

        JooqUtil.context(authDbConnProvider, context -> context
                .update((Table) USERS)
                .set(user)
                .where(new Condition[]{USERS.EMAIL.eq(email)}).execute());

        if (shouldLock) {
            LOGGER.debug("Account {} has had too many failed access attempts and is locked", email);
        }

        return shouldLock;
    }

    public Optional<User> get(String email) {
        Optional<UsersRecord> userQuery = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(USERS)
                .where(USERS.EMAIL.eq(email)).fetchOptional());

        return userQuery.map(usersRecord -> UserMapper.map(usersRecord));
    }

    public void changePassword(String email, String newPassword) {
        UsersRecord user = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(USERS)
                .where(new Condition[]{USERS.EMAIL.eq(email)})
                .fetchOne());

        if(user == null){
            throw new NoSuchUserException("Cannot change this password because this user does not exist!");
        }

        String newPasswordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        user.setPasswordHash(newPasswordHash);
        user.setPasswordLastChanged(Timestamp.from(clock.instant()));
        user.setForcePasswordChange(false);

        JooqUtil.context(authDbConnProvider, context -> context.update((Table) USERS)
                .set(user)
                .where(new Condition[]{USERS.EMAIL.eq(email)})
                .execute());
    }

    public Boolean needsPasswordChange(String email, Duration mandatoryPasswordChangeDuration, boolean forcePasswordChangeOnFirstLogin) {
        Validate.notNull(email, "email must not be null");

        UsersRecord user = (UsersRecord) JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom((Table) USERS)
                .where(new Condition[]{USERS.EMAIL.eq(email)})
                .fetchOne());

        if(user == null){
            throw new NoSuchUserException("Cannot check if this user needs a password change because this user does not exist!");
        }

        LocalDateTime passwordLastChanged = user.getPasswordLastChanged() == null ?
                user.getCreatedOn().toLocalDateTime() :
                user.getPasswordLastChanged().toLocalDateTime();
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(clock), ZoneId.systemDefault());
        Duration durationSinceLastPasswordChange = Duration.ofMinutes(passwordLastChanged.until(now, ChronoUnit.MINUTES));

        boolean thresholdBreached = durationSinceLastPasswordChange.compareTo(mandatoryPasswordChangeDuration) > 0;
        boolean isFirstLogin = user.getPasswordLastChanged() == null;

        if(thresholdBreached || (forcePasswordChangeOnFirstLogin && isFirstLogin) || user.getForcePasswordChange()){
            LOGGER.debug("User {} needs a password change.", email);
            return true;
        } else return false;
    }

    public int deactivateNewInactiveUsers(Duration neverUsedAccountDeactivationThreshold){
        Timestamp activityThreshold = convertThresholdToTimestamp(neverUsedAccountDeactivationThreshold);

        Result<UsersRecord> candidatesForDeactivating = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(USERS)
                .where(USERS.CREATED_ON.lessOrEqual(activityThreshold))
                // We are only going to deactivate enabled accounts
                .and(USERS.STATE.eq(User.UserState.ENABLED.getStateText()))
                // A 'new' user is one who has never logged in.
                .and(USERS.LAST_LOGIN.isNull())
                // We don't want to disable all accounts
                .and(USERS.NEVER_EXPIRES.ne(true)).fetch());

        List<Integer> usersToDeactivate = candidatesForDeactivating.stream()
                .filter(usersRecord ->
                        usersRecord.getReactivatedDate() == null
                        || usersRecord.getReactivatedDate().before(activityThreshold))
                .map(usersRecord -> usersRecord.getId())
                .collect(Collectors.toList());

        JooqUtil.contextResult(authDbConnProvider, context -> context.
                update(USERS).set(USERS.STATE, User.UserState.INACTIVE.getStateText())
                .where(USERS.ID.in(usersToDeactivate))
                .execute());

        return usersToDeactivate.size();
    }

    public int deactivateInactiveUsers(Duration unusedAccountDeactivationThreshold){
        Timestamp activityThreshold = convertThresholdToTimestamp(unusedAccountDeactivationThreshold);

        Result<UsersRecord> candidatesForDeactivating = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(USERS)
                .where(USERS.LAST_LOGIN.lessOrEqual(activityThreshold))
                // If we have a reactivated date we'll use that instead of the created_on date.
                // We are only going to deactivate enabled accounts
                .and(USERS.STATE.eq(User.UserState.ENABLED.getStateText()))
                // We don't want to disable admin because that could lock the users out of the system
                .and(USERS.NEVER_EXPIRES.ne(true)).fetch());

        List<Integer> usersToDeactivate = candidatesForDeactivating.stream()
                .filter(usersRecord ->
                        usersRecord.getReactivatedDate() == null
                        || usersRecord.getReactivatedDate().before(activityThreshold))
                .map(usersRecord -> usersRecord.getId())
                .collect(Collectors.toList());

        JooqUtil.context(authDbConnProvider, context -> context.
                update(USERS).set(USERS.STATE, User.UserState.INACTIVE.getStateText())
                .where(USERS.ID.in(usersToDeactivate))
                .execute());

        return usersToDeactivate.size();
    }

    public boolean exists(String id) {
        UsersRecord result = JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(Tables.USERS)
                .where(Tables.USERS.EMAIL.eq(id))
                .fetchOne());
        return result != null;
    }

    private Timestamp convertThresholdToTimestamp(Duration duration){
        Instant now = Instant.now(clock);
        Instant thresholdInstant = now.minus(duration);
        return Timestamp.from(thresholdInstant);
    }

    public enum LoginResult {
        GOOD_CREDENTIALS,
        BAD_CREDENTIALS,
        LOCKED_BAD_CREDENTIALS,
        LOCKED_GOOD_CREDENTIALS,
        DISABLED_BAD_CREDENTIALS,
        DISABLED_GOOD_CREDENTIALS,
        USER_DOES_NOT_EXIST,
        INACTIVE_GOOD_CREDENTIALS,
        INACTIVE_BAD_CREDENTIALS;
    }

}
