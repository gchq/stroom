/*
 * Copyright 2017 Crown Copyright
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

package stroom.authentication;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import stroom.authentication.impl.db.UserMapper;
import stroom.authentication.impl.db.jooq.tables.records.AccountRecord;
import stroom.authentication.resources.user.v1.User;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.authentication.resources.user.v1.User.UserState.DISABLED;
import static stroom.authentication.resources.user.v1.User.UserState.ENABLED;
import static stroom.authentication.resources.user.v1.User.UserState.INACTIVE;

public final class AccountMapperTest {
    @Test
    public final void testMappings() {
        AccountRecord record = new AccountRecord();
        record.setId(1);
        record.setEmail("email");
        record.setPasswordHash("hash");
        record.setState(ENABLED.getStateText());
        record.setFirstName("first name");
        record.setLastName("last name");
        record.setComments("comments");
        record.setLoginFailures(2);
        record.setLoginCount(4);
        record.setLastLogin(new Timestamp(System.currentTimeMillis()));
        record.setUpdatedOn(new Timestamp(System.currentTimeMillis()));
        record.setUpdatedByUser("updating user");
        record.setCreatedOn(new Timestamp(System.currentTimeMillis()));
        record.setCreatedByUser("creating user");
        record.setNeverExpires(true);
        record.setReactivatedDate(new Timestamp(System.currentTimeMillis()));
        record.setForcePasswordChange(true);

        User user = getUsers().getRight();

        AccountRecord updatedRecord = UserMapper.updateUserRecordWithUser(user, record);
        assertThat(updatedRecord.getId()).isEqualTo(2);
        assertThat(updatedRecord.getEmail()).isEqualTo("new email");
        assertThat(updatedRecord.getPasswordHash()).isEqualTo("new hash");
        assertThat(updatedRecord.getState()).isEqualTo(DISABLED.getStateText());
        assertThat(updatedRecord.getFirstName()).isEqualTo("new first name");
        assertThat(updatedRecord.getLastName()).isEqualTo("new last name");
        assertThat(updatedRecord.getComments()).isEqualTo("new comments");
        assertThat(updatedRecord.getLoginFailures()).isEqualTo(3);
        assertThat(updatedRecord.getLoginCount()).isEqualTo(5);
        assertThat(updatedRecord.getLastLogin()).isEqualTo(UserMapper.convertISO8601ToTimestamp("2017-01-01T00:00:00"));
        assertThat(updatedRecord.getUpdatedOn()).isEqualTo(UserMapper.convertISO8601ToTimestamp("2017-01-02T00:00:00"));
        assertThat(updatedRecord.getUpdatedByUser()).isEqualTo("New updating user");
        assertThat(updatedRecord.getCreatedOn()).isEqualTo(UserMapper.convertISO8601ToTimestamp("2017-01-03T00:00:00"));
        assertThat(updatedRecord.getCreatedByUser()).isEqualTo("New creating user");
        assertThat(updatedRecord.getNeverExpires()).isEqualTo(false);
        assertThat(updatedRecord.getForcePasswordChange()).isEqualTo(true);
        // NB: We don't need to map reactivate_date because it's set by this method anyway.
//        assertThat(updatedRecord.getReactivatedDate()).isEqualTo("2018-01-01T10:10:10");
    }

    @Test
    public final void userToRecord() {
        Pair<AccountRecord, User> users = getUsers();
        AccountRecord mapped = UserMapper.map(users.getRight());
        AccountRecord orig = users.getLeft();
        assertThat(mapped.getId()).isEqualTo(orig.getId());
        assertThat(mapped.getReactivatedDate()).isEqualTo(orig.getReactivatedDate());
        assertThat(mapped.getState()).isEqualTo(orig.getState());
        assertThat(mapped.getCreatedOn()).isEqualTo(orig.getCreatedOn());
        assertThat(mapped.getUpdatedOn()).isEqualTo(orig.getUpdatedOn());
        assertThat(mapped.getLoginFailures()).isEqualTo(orig.getLoginFailures());
        assertThat(mapped.getEmail()).isEqualTo(orig.getEmail());
        assertThat(mapped.getLastLogin()).isEqualTo(orig.getLastLogin());
        assertThat(mapped.getLoginCount()).isEqualTo(orig.getLoginCount());
        assertThat(mapped.getPasswordLastChanged()).isEqualTo(orig.getPasswordLastChanged());
        assertThat(mapped.getComments()).isEqualTo(orig.getComments());
        assertThat(mapped.getCreatedByUser()).isEqualTo(orig.getCreatedByUser());
        assertThat(mapped.getFirstName()).isEqualTo(orig.getFirstName());
        assertThat(mapped.getLastName()).isEqualTo(orig.getLastName());
        assertThat(mapped.getNeverExpires()).isEqualTo(orig.getNeverExpires());
        // NB: We don't assert that the password has is equal because this is generated. We just assert it's not null.
        assertThat(mapped.getPasswordHash()).isNotNull();
        assertThat(mapped.getUpdatedByUser()).isEqualTo(orig.getUpdatedByUser());
        assertThat(mapped.getForcePasswordChange()).isEqualTo(orig.getForcePasswordChange());
    }

    @Test
    public final void recordToUser() {
        Pair<AccountRecord, User> users = getUsers();
        User mapped = UserMapper.map(users.getLeft());
        User orig = users.getRight();
        assertThat(mapped.getId()).isEqualTo(orig.getId());
        assertThat(mapped.getReactivatedDate()).isEqualTo(orig.getReactivatedDate());
        assertThat(mapped.getState()).isEqualTo(orig.getState());
        assertThat(mapped.getCreatedOn()).isEqualTo(orig.getCreatedOn());
        assertThat(mapped.getUpdatedOn()).isEqualTo(orig.getUpdatedOn());
        assertThat(mapped.getLoginFailures()).isEqualTo(orig.getLoginFailures());
        assertThat(mapped.getEmail()).isEqualTo(orig.getEmail());
        assertThat(mapped.getLastLogin()).isEqualTo(orig.getLastLogin());
        assertThat(mapped.getLoginCount()).isEqualTo(orig.getLoginCount());
        //TODO Add get password last change to the POJO
//        assertThat(mapped.getPasswordLastChanged()).isEqualTo(orig.get());
        assertThat(mapped.getComments()).isEqualTo(orig.getComments());
        assertThat(mapped.getCreatedByUser()).isEqualTo(orig.getCreatedByUser());
        assertThat(mapped.getFirstName()).isEqualTo(orig.getFirstName());
        assertThat(mapped.getLastName()).isEqualTo(orig.getLastName());
        assertThat(mapped.getNeverExpires()).isEqualTo(orig.getNeverExpires());
        // NB: We don't need to check password hash mapping
//        assertThat(mapped.getPasswordHash()).isNotNull();
        assertThat(mapped.getUpdatedByUser()).isEqualTo(orig.getUpdatedByUser());
        assertThat(mapped.isForcePasswordChange()).isEqualTo(orig.isForcePasswordChange());
    }

    @Test
    public final void testBecomingEnabledFromDisabled() {
        Pair<AccountRecord, User> users = getUsers();

        users.getLeft().setState(DISABLED.getStateText());
        users.getRight().setState(ENABLED.getStateText());

        Instant now = Instant.now();
        AccountRecord mapped = UserMapper.updateUserRecordWithUser(users.getRight(), users.getLeft(), Clock.fixed(now, ZoneId.systemDefault()));
        assertThat(mapped.getState()).isEqualTo(ENABLED.getStateText());
        assertThat(mapped.getLastLogin().toInstant().getEpochSecond()).isEqualTo(now.getEpochSecond());
        assertThat(mapped.getLoginFailures()).isEqualTo(0);
    }

    @Test
    public final void testBecomingEnabledFromInactive() {
        Pair<AccountRecord, User> users = getUsers();

        users.getLeft().setState(INACTIVE.getStateText());
        users.getRight().setState(ENABLED.getStateText());

        Instant now = Instant.now();
        AccountRecord mapped = UserMapper.updateUserRecordWithUser(users.getRight(), users.getLeft(), Clock.fixed(now, ZoneId.systemDefault()));
        assertThat(mapped.getState()).isEqualTo(ENABLED.getStateText());
        assertThat(mapped.getLastLogin().toInstant().getEpochSecond()).isEqualTo(now.getEpochSecond());
        assertThat(mapped.getLoginFailures()).isEqualTo(0);
    }

    @Test
    public final void updateReminder() {
        // This test will fail if you add a property to the Users class.
        // To make it pass you'll need to updated the count, below.
        // It exists to remind you to add a mapping in UserMapper.
        // The count is one more than the number in UserRecord, because it has a 'password' property.
        int currentNumberOfPropertiesOnUser = 18;
        assertThat(User.class.getDeclaredFields().length).isEqualTo(currentNumberOfPropertiesOnUser);
    }

    public static final Pair<AccountRecord, User> getUsers() {
        User user = new User();
        AccountRecord record = new AccountRecord();

        user.setId(2);
        record.setId(2);

        user.setEmail("new email");
        record.setEmail("new email");

        user.setPasswordHash("new hash");
        record.setPasswordHash(user.generatePasswordHash());

        user.setState(DISABLED.getStateText());
        record.setState(DISABLED.getStateText());

        user.setFirstName("new first name");
        record.setFirstName("new first name");

        user.setLastName("new last name");
        record.setLastName("new last name");

        user.setComments("new comments");
        record.setComments("new comments");

        user.setLoginFailures(3);
        record.setLoginFailures(3);

        user.setLoginCount(5);
        record.setLoginCount(5);

        user.setLastLogin("2017-01-01T00:00:00");
        record.setLastLogin(isoToTimestamp("2017-01-01T00:00:00.000"));

        user.setUpdatedOn("2017-01-02T00:00:00");
        record.setUpdatedOn(isoToTimestamp("2017-01-02T00:00:00"));

        user.setUpdatedByUser("New updating user");
        record.setUpdatedByUser("New updating user");

        user.setCreatedOn("2017-01-03T00:00:00");
        record.setCreatedOn(isoToTimestamp("2017-01-03T00:00:00"));

        user.setCreatedByUser("New creating user");
        record.setCreatedByUser("New creating user");

        user.setNeverExpires(false);
        record.setNeverExpires(false);

        user.setReactivatedDate("2019-01-01T10:10:10");
        record.setReactivatedDate(isoToTimestamp("2019-01-01T10:10:10"));

        user.setForcePasswordChange(true);
        record.setForcePasswordChange(true);

        return new ImmutablePair(record, user);
    }

    private static Timestamp isoToTimestamp(String iso8601) {
        return Timestamp.from(LocalDateTime.parse(iso8601).toInstant(ZoneOffset.UTC));
    }
}
