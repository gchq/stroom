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

package stroom.security.identity.db;

import org.junit.jupiter.api.Disabled;

@Disabled
public final class AccountMapperTest {
//    @Test
//    public final void testMappings() {
//        final AccountRecord record = new AccountRecord();
//        record.setId(1);
//        record.setEmail("email");
//        record.setPasswordHash("hash");
//        record.setEnabled(true);
//        record.setFirstName("first name");
//        record.setLastName("last name");
//        record.setComments("comments");
//        record.setLoginFailures(2);
//        record.setLoginCount(4);
//        record.setLastLoginMs(System.currentTimeMillis());
//        record.setUpdateTimeMs(System.currentTimeMillis());
//        record.setUpdateUser("updating user");
//        record.setCreateTimeMs(System.currentTimeMillis());
//        record.setCreateUser("creating user");
//        record.setNeverExpires(true);
//        record.setReactivatedMs(System.currentTimeMillis());
//        record.setForcePasswordChange(true);
//
//        final Account account = createAccount();
//
//
//        final AccountRecord updatedRecord = new AccountRecord();
//        UserMapper.mapToRecord(account, updatedRecord);
//
//
////        if (user.getState() != null) {
////            // Is this user's state becoming enabled?
////            if (!usersRecord.getState().equals(User.UserState.ENABLED.getStateText())
////                    && user.getState().equalsIgnoreCase(User.UserState.ENABLED.getStateText())) {
////                userMap.put("login_failures", 0);
////                userMap.put("last_login", clock.instant());
////                userMap.put("reactivated_date", clock.instant());
////            }
////            userMap.put("state", user.getState());
////        }
//
//
//        assertThat(updatedRecord.getId()).isEqualTo(2);
//        assertThat(updatedRecord.getEmail()).isEqualTo("new email");
//        assertThat(updatedRecord.getPasswordHash()).isEqualTo("new hash");
//        assertThat(updatedRecord.getEnabled()).isFalse();
//        assertThat(updatedRecord.getFirstName()).isEqualTo("new first name");
//        assertThat(updatedRecord.getLastName()).isEqualTo("new last name");
//        assertThat(updatedRecord.getComments()).isEqualTo("new comments");
//        assertThat(updatedRecord.getLoginFailures()).isEqualTo(3);
//        assertThat(updatedRecord.getLoginCount()).isEqualTo(5);
//        assertThat(updatedRecord.getLastLogin())
//        .isEqualTo(UserMapper.convertISO8601ToTimestamp("2017-01-01T00:00:00"));
//        assertThat(updatedRecord.getUpdatedOn())
//        .isEqualTo(UserMapper.convertISO8601ToTimestamp("2017-01-02T00:00:00"));
//        assertThat(updatedRecord.getUpdatedByUser()).isEqualTo("New updating user");
//        assertThat(updatedRecord.getCreatedOn())
//        .isEqualTo(UserMapper.convertISO8601ToTimestamp("2017-01-03T00:00:00"));
//        assertThat(updatedRecord.getCreatedByUser()).isEqualTo("New creating user");
//        assertThat(updatedRecord.getNeverExpires()).isEqualTo(false);
//        assertThat(updatedRecord.getForcePasswordChange()).isEqualTo(true);
//        // NB: We don't need to map reactivate_date because it's set by this method anyway.
////        assertThat(updatedRecord.getReactivatedDate()).isEqualTo("2018-01-01T10:10:10");
//    }
//
//    @Test
//    public final void userToRecord() {
//        final Account account = createAccount();
//        final AccountRecord orig = createRecord();
//
////        final Pair<AccountRecord, Account> users = getUsers();
//        final AccountRecord mapped = new AccountRecord();
//        UserMapper.mapToRecord(account, mapped);
////        AccountRecord orig = users.getLeft();
//        assertThat(mapped.getId()).isEqualTo(orig.getId());
//        assertThat(mapped.getReactivatedDate()).isEqualTo(orig.getReactivatedDate());
//        assertThat(mapped.getEnabled()).isEqualTo(orig.getEnabled());
//        assertThat(mapped.getInactive()).isEqualTo(orig.getInactive());
//        assertThat(mapped.getLocked()).isEqualTo(orig.getLocked());
//        assertThat(mapped.getCreatedOn()).isEqualTo(orig.getCreatedOn());
//        assertThat(mapped.getUpdatedOn()).isEqualTo(orig.getUpdatedOn());
//        assertThat(mapped.getLoginFailures()).isEqualTo(orig.getLoginFailures());
//        assertThat(mapped.getEmail()).isEqualTo(orig.getEmail());
//        assertThat(mapped.getLastLogin()).isEqualTo(orig.getLastLogin());
//        assertThat(mapped.getLoginCount()).isEqualTo(orig.getLoginCount());
//        assertThat(mapped.getPasswordLastChanged()).isEqualTo(orig.getPasswordLastChanged());
//        assertThat(mapped.getComments()).isEqualTo(orig.getComments());
//        assertThat(mapped.getCreatedByUser()).isEqualTo(orig.getCreatedByUser());
//        assertThat(mapped.getFirstName()).isEqualTo(orig.getFirstName());
//        assertThat(mapped.getLastName()).isEqualTo(orig.getLastName());
//        assertThat(mapped.getNeverExpires()).isEqualTo(orig.getNeverExpires());
//        // NB: We don't assert that the password has is equal because
//        this is generated. We just assert it's not null.
//        assertThat(mapped.getPasswordHash()).isNotNull();
//        assertThat(mapped.getUpdatedByUser()).isEqualTo(orig.getUpdatedByUser());
//        assertThat(mapped.getForcePasswordChange()).isEqualTo(orig.getForcePasswordChange());
//    }
//
//    @Test
//    public final void recordToUser() {
//        final Account orig = createAccount();
//        final AccountRecord accountRecord = createRecord();
//
////        final Pair<AccountRecord, Account> users = getUsers();
//
//        final Account mapped = new Account();
//        UserMapper.mapFromRecord(accountRecord, mapped);
//        assertThat(mapped.getId()).isEqualTo(orig.getId());
//        assertThat(mapped.getReactivatedDate()).isEqualTo(orig.getReactivatedDate());
//        assertThat(mapped.isEnabled()).isEqualTo(orig.isEnabled());
//        assertThat(mapped.isInactive()).isEqualTo(orig.isInactive());
//        assertThat(mapped.isLocked()).isEqualTo(orig.isLocked());
//        assertThat(mapped.getCreatedOn()).isEqualTo(orig.getCreatedOn());
//        assertThat(mapped.getUpdatedOn()).isEqualTo(orig.getUpdatedOn());
//        assertThat(mapped.getLoginFailures()).isEqualTo(orig.getLoginFailures());
//        assertThat(mapped.getEmail()).isEqualTo(orig.getEmail());
//        assertThat(mapped.getLastLogin()).isEqualTo(orig.getLastLogin());
//        assertThat(mapped.getLoginCount()).isEqualTo(orig.getLoginCount());
//        //TODO Add get password last change to the POJO
////        assertThat(mapped.getPasswordLastChanged()).isEqualTo(orig.get());
//        assertThat(mapped.getComments()).isEqualTo(orig.getComments());
//        assertThat(mapped.getCreatedByUser()).isEqualTo(orig.getCreatedByUser());
//        assertThat(mapped.getFirstName()).isEqualTo(orig.getFirstName());
//        assertThat(mapped.getLastName()).isEqualTo(orig.getLastName());
//        assertThat(mapped.getNeverExpires()).isEqualTo(orig.getNeverExpires());
//        // NB: We don't need to check password hash mapping
////        assertThat(mapped.getPasswordHash()).isNotNull();
//        assertThat(mapped.getUpdatedByUser()).isEqualTo(orig.getUpdatedByUser());
//        assertThat(mapped.isForcePasswordChange()).isEqualTo(orig.isForcePasswordChange());
//    }
//
//    @Test
//    public final void testBecomingEnabledFromDisabled() {
//        final Account account = createAccount();
//        final AccountRecord accountRecord = createRecord();
//
////        Pair<AccountRecord, Account> users = getUsers();
//
//        accountRecord.setEnabled(false);
//        account.setEnabled(true);
//
//        Instant now = Instant.now();
//        final AccountRecord mapped = new AccountRecord();
//        UserMapper.mapToRecord(account, mapped);
////        AccountRecord mapped = UserMapper.updateUserRecordWithUser(
// users.getRight(), users.getLeft(), Clock.fixed(now, ZoneId.systemDefault()));
//        assertThat(mapped.getEnabled()).isTrue();
//        assertThat(mapped.getLastLogin().toInstant().getEpochSecond()).isEqualTo(now.getEpochSecond());
//        assertThat(mapped.getLoginFailures()).isEqualTo(0);
//    }
//
//    @Test
//    public final void testBecomingEnabledFromInactive() {
//        final Account account = createAccount();
//        final AccountRecord accountRecord = createRecord();
//
//        accountRecord.setInactive(true);
//        account.setEnabled(true);
//
//        Instant now = Instant.now();
//        final AccountRecord mapped = new AccountRecord();
//        UserMapper.mapToRecord(account, mapped);//users.getLeft(), Clock.fixed(now, ZoneId.systemDefault()));
//        assertThat(mapped.getEnabled()).isTrue();
//        assertThat(mapped.getLastLogin().toInstant().getEpochSecond()).isEqualTo(now.getEpochSecond());
//        assertThat(mapped.getLoginFailures()).isEqualTo(0);
//    }
//
//    @Test
//    public final void updateReminder() {
//        // This test will fail if you add a property to the Users class.
//        // To make it pass you'll need to updated the count, below.
//        // It exists to remind you to add a mapping in UserMapper.
//        // The count is one more than the number in UserRecord, because it has a 'password' property.
//        int currentNumberOfPropertiesOnUser = 18;
//        assertThat(Account.class.getDeclaredFields().length).isEqualTo(currentNumberOfPropertiesOnUser);
//    }
//
//    private Account createAccount() {
//        final Account account = new Account();
//        account.setId(2);
//        account.setEmail("new email");
//        account.setPasswordHash("new hash");
//        account.setEnabled(false);
//        account.setFirstName("new first name");
//        account.setLastName("new last name");
//        account.setComments("new comments");
//        account.setLoginFailures(3);
//        account.setLoginCount(5);
//        account.setLastLogin("2017-01-01T00:00:00");
//        account.setUpdatedOn("2017-01-02T00:00:00");
//        account.setUpdatedByUser("New updating user");
//        account.setCreatedOn("2017-01-03T00:00:00");
//        account.setCreatedByUser("New creating user");
//        account.setNeverExpires(false);
//        account.setReactivatedDate("2019-01-01T10:10:10");
//        account.setForcePasswordChange(true);
//        return account;
//    }
//
//    private AccountRecord createRecord() {
//        final AccountRecord record = new AccountRecord();
//        record.setId(2);
//        record.setEmail("new email");
//        record.setPasswordHash("new hash");
//        record.setEnabled(false);
//        record.setFirstName("new first name");
//        record.setLastName("new last name");
//        record.setComments("new comments");
//        record.setLoginFailures(3);
//        record.setLoginCount(5);
//        record.setLastLogin(isoToTimestamp("2017-01-01T00:00:00.000"));
//        record.setUpdatedOn(isoToTimestamp("2017-01-02T00:00:00"));
//        record.setUpdatedByUser("New updating user");
//        record.setCreatedOn(isoToTimestamp("2017-01-03T00:00:00"));
//        record.setCreatedByUser("New creating user");
//        record.setNeverExpires(false);
//        record.setReactivatedDate(isoToTimestamp("2019-01-01T10:10:10"));
//        record.setForcePasswordChange(true);
//        return record;
//    }
//
//    private static Timestamp isoToTimestamp(String iso8601) {
//        return Timestamp.from(LocalDateTime.parse(iso8601).toInstant(ZoneOffset.UTC));
//    }
}
