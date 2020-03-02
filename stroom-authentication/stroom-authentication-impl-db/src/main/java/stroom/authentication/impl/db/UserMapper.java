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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import stroom.auth.db.tables.records.UsersRecord;
import stroom.authentication.resources.user.v1.User;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

public final class UserMapper {

    public static UsersRecord updateUserRecordWithUser(@NotNull User user, @NotNull UsersRecord usersRecord) {
        return updateUserRecordWithUser(user, usersRecord, Clock.systemDefaultZone());
    }

    @NotNull
    public static UsersRecord updateUserRecordWithUser(@NotNull User user, @NotNull UsersRecord usersRecord, Clock clock) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(usersRecord);

        HashMap userMap = new HashMap();

        if (user.getId() != null) userMap.put("id", user.getId());
        if (!Strings.isNullOrEmpty(user.getEmail())) userMap.put("email", user.getEmail());
        if (!Strings.isNullOrEmpty(user.getPasswordHash())) userMap.put("password_hash", user.getPasswordHash());
        // This will override the setting for getPasswordHash, above. If there's a hash it'll map that,
        // but if there's a password it'll update the hash.
        if (!Strings.isNullOrEmpty(user.getPassword())) userMap.put("password_hash", user.generatePasswordHash());
        if (user.getFirstName() != null) userMap.put("first_name", user.getFirstName());
        if (user.getLastName() != null) userMap.put("last_name", user.getLastName());
        if (user.getComments() != null) userMap.put("comments", user.getComments());
        if (user.getLoginCount() != null) userMap.put("login_count", user.getLoginCount());
        if (user.getLoginFailures() != null) userMap.put("login_failures", user.getLoginFailures());
        if (user.getLastLogin() != null) userMap.put("last_login", convertISO8601ToTimestamp(user.getLastLogin()));
        if (user.getCreatedOn() != null) userMap.put("created_on", convertISO8601ToTimestamp(user.getCreatedOn()));
        if (user.getCreatedByUser() != null) userMap.put("created_by_user", user.getCreatedByUser());
        if (user.getUpdatedOn() != null) userMap.put("updated_on", convertISO8601ToTimestamp(user.getUpdatedOn()));
        if (user.getUpdatedByUser() != null) userMap.put("updated_by_user", user.getUpdatedByUser());

        userMap.put("never_expires", user.getNeverExpires());
        userMap.put("force_password_change", user.isForcePasswordChange());

        // This is last because if we're going from locked to enabled then we need to reset the login failures.
        // And in this case we'll want to override any other setting for login_failures.
        if (user.getState() != null) {
            // Is this user's state becoming enabled?
            if (!usersRecord.getState().equals(User.UserState.ENABLED.getStateText())
                    && user.getState().equalsIgnoreCase(User.UserState.ENABLED.getStateText())) {
                userMap.put("login_failures", 0);
                userMap.put("last_login", clock.instant());
                userMap.put("reactivated_date", clock.instant());
            }
            userMap.put("state", user.getState());
        }

        usersRecord.from(userMap);
        return usersRecord;
    }

    public static UsersRecord map(User user) {
        UsersRecord usersRecord = new UsersRecord();
        usersRecord.setForcePasswordChange(user.isForcePasswordChange());
        usersRecord.setComments(user.getComments());
        usersRecord.setCreatedByUser(user.getCreatedByUser());
        usersRecord.setCreatedOn(convertISO8601ToTimestamp(user.getCreatedOn()));
        usersRecord.setEmail(user.getEmail());
        usersRecord.setFirstName(user.getFirstName());
        usersRecord.setId(user.getId());
        usersRecord.setLastLogin(convertISO8601ToTimestamp(user.getLastLogin()));
        usersRecord.setLastName(user.getLastName());
        usersRecord.setLoginCount(user.getLoginCount());
        usersRecord.setLoginFailures(user.getLoginFailures());
        usersRecord.setNeverExpires(user.getNeverExpires());
        usersRecord.setPasswordHash(user.generatePasswordHash());
        usersRecord.setReactivatedDate(convertISO8601ToTimestamp(user.getReactivatedDate()));
        usersRecord.setState(user.getState());
        usersRecord.setState(user.getState());
        usersRecord.setUpdatedByUser(user.getUpdatedByUser());
        usersRecord.setUpdatedOn(convertISO8601ToTimestamp(user.getUpdatedOn()));
        return usersRecord;
    }

    public static User map(UsersRecord usersRecord) {
        User user = new User();
        user.setEmail(usersRecord.getEmail());
        user.setState(usersRecord.getState());
        user.setComments(usersRecord.getComments());
        user.setId(usersRecord.getId());
        user.setFirstName(usersRecord.getFirstName());
        user.setLastName(usersRecord.getLastName());
        user.setNeverExpires(usersRecord.getNeverExpires());
        user.setUpdatedByUser(usersRecord.getUpdatedByUser());
        if (usersRecord.getUpdatedOn() != null) {
            user.setUpdatedOn(toIso(usersRecord.getUpdatedOn()));
        }
        user.setCreatedByUser(usersRecord.getCreatedByUser());
        if (usersRecord.getCreatedOn() != null) {
            user.setCreatedOn(toIso(usersRecord.getCreatedOn()));
        }
        if (usersRecord.getLastLogin() != null) {
            user.setLastLogin(toIso(usersRecord.getLastLogin()));
        }
        if (usersRecord.getReactivatedDate() != null) {
            user.setReactivatedDate(toIso(usersRecord.getReactivatedDate()));
        }
        user.setLoginCount(usersRecord.getLoginCount());
        user.setLoginFailures(usersRecord.getLoginFailures());
        user.setForcePasswordChange(usersRecord.getForcePasswordChange());
        return user;
    }


    public static String toIso(Timestamp timestamp) {
        return timestamp.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public static Timestamp convertISO8601ToTimestamp(@Nullable String dateString) {
        if (dateString != null) {
            long millis = LocalDateTime.parse(dateString).toInstant(ZoneOffset.UTC).toEpochMilli();
            return new Timestamp(millis);
        } else return null;
    }

}
