package stroom.authentication.impl.db;

import junit.framework.TestCase;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.Database_IT;
import stroom.authentication.account.Account;
import stroom.authentication.account.AccountDao;
import stroom.config.common.CommonDbConfig;
import stroom.config.common.DbConfig;
import stroom.test.common.util.db.DbTestUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore("Temporarily ignore for auth migration")
public class AccountDao_IT extends Database_IT {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountDao_IT.class);

    @Test
    public void testNewButInactiveUserIsDisabled() {
        try (Connection conn = getConnection()) {
            // GIVEN...
            AccountDao accountDao = getUserDao(conn);

            final String user01 = UUID.randomUUID().toString();
            final String user02 = UUID.randomUUID().toString();
            final String user03 = UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(accountDao, user01);

            // Create a user who would be disabled if they hadn't logged in already
            createUserAccount(accountDao, user02);
            accountDao.recordSuccessfulLogin(user02);

            // Advance the clock and create a test user who shouldn't be disabled
//            setClockToDaysFromNow(accountDao, 10);
            createUserAccount(accountDao, user03);

            // WHEN...
//            setClockToDaysFromNow(accountDao, 31);
            int numberOfDisabledUsers = accountDao.deactivateNewInactiveUsers(Duration.parse("P30D"));

            // THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(accountDao.get(user01).get().isInactive()).isTrue();
            assertThat(accountDao.get(user02).get().isEnabled()).isTrue();
            assertThat(accountDao.get(user03).get().isEnabled()).isTrue();

        } catch (SQLException e) {
            e.printStackTrace();
            TestCase.fail();
        }
    }

    @Test
    public void testReactivatedDateIsUsedInsteadOfLastLoginForNewUsers() {
        try (Connection conn = getConnection()) {
            // GIVEN...
            AccountDao accountDao = getUserDao(conn);

            final String user01 = UUID.randomUUID().toString();
            final String user02 = UUID.randomUUID().toString();
            final String user03 = UUID.randomUUID().toString();
            final String user04 = UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(accountDao, user01);

            // Create a user who would be disabled except their reactivated_date has been set to more recently
            createUserAccount(accountDao, user04, false, true, false, false,
                    ZonedDateTime.now().plusDays(10).toInstant().toEpochMilli());

            // Create a user who would be disabled if they hadn't logged in already
            createUserAccount(accountDao, user02);
            accountDao.recordSuccessfulLogin(user02);

            // Advance the clock and create a test user who shouldn't be disabled
//            setClockToDaysFromNow(accountDao, 10);
            createUserAccount(accountDao, user03);

            // WHEN...
//            setClockToDaysFromNow(accountDao, 31);
            int numberOfDisabledUsers = accountDao.deactivateNewInactiveUsers(Duration.parse("P30D"));

            // THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(accountDao.get(user01).get().isEnabled()).isTrue();
            assertThat(accountDao.get(user02).get().isEnabled()).isTrue();
            assertThat(accountDao.get(user03).get().isEnabled()).isTrue();
            assertThat(accountDao.get(user04).get().isEnabled()).isTrue();

        } catch (SQLException e) {
            e.printStackTrace();
            TestCase.fail();
        }
    }

    @Test
    public void testReactivatedDateIsUsedInsteadOfLastLogin() {
        try (Connection conn = getConnection()) {
            // GIVEN...
            AccountDao accountDao = getUserDao(conn);

            final String user01 = "user01_" + UUID.randomUUID().toString();
            final String user02 = "user02_" + UUID.randomUUID().toString();
            final String user03 = "user03_" + UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(accountDao, user01);
            accountDao.recordSuccessfulLogin(user01);

//            // Create a user who would be disabled except their reactivated_date has been set to more recently
            createUserAccount(accountDao, user03, false, true, false, false,
                    ZonedDateTime.now().plusDays(40).toInstant().toEpochMilli());

            // Advance the clock and create a test user who shouldn't be disabled
//            setClockToDaysFromNow(accountDao, 40);
            createUserAccount(accountDao, user02);
            accountDao.recordSuccessfulLogin(user02);

            // WHEN...
//            setClockToDaysFromNow(accountDao, 91);
            int numberOfDisabledUsers = accountDao.deactivateInactiveUsers(Duration.parse("P90D"));

            // THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(accountDao.get(user01).get().isInactive()).isTrue();
            assertThat(accountDao.get(user02).get().isEnabled()).isTrue();
            assertThat(accountDao.get(user03).get().isEnabled()).isTrue();

        } catch (SQLException e) {
            e.printStackTrace();
            TestCase.fail();
        }
    }

    @Test
    public void testInactiveUserIsDeactivated() {
        try (Connection conn = getConnection()) {
            // GIVEN...
            AccountDao accountDao = getUserDao(conn);

            final String user01 = UUID.randomUUID().toString();
            final String user02 = UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(accountDao, user01);
            accountDao.recordSuccessfulLogin(user01);

            // Advance the clock and create a test user who shouldn't be disabled
//            setClockToDaysFromNow(accountDao, 10);
            createUserAccount(accountDao, user02);
            accountDao.recordSuccessfulLogin(user02);

            // WHEN...
//            setClockToDaysFromNow(accountDao, 91);
            int numberOfDisabledUsers = accountDao.deactivateInactiveUsers(Duration.parse("P90D"));

            // THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(accountDao.get(user01).get().isInactive()).isTrue();
            assertThat(accountDao.get(user02).get().isEnabled()).isTrue();

            // ALSO WHEN...
//            setClockToDaysFromNow(accountDao, 200);
            numberOfDisabledUsers = accountDao.deactivateInactiveUsers(Duration.parse("P90D"));

            //ALSO THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(accountDao.get(user02).get().isInactive()).isTrue();

        } catch (SQLException e) {
            e.printStackTrace();
            TestCase.fail();
        }
    }

    @Test
    public void testLockedUserIsNeverMadeInactive() {
        try (Connection conn = getConnection()) {
            // GIVEN...
            AccountDao accountDao = getUserDao(conn);

            final String user01 = UUID.randomUUID().toString();
            final String user02 = UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(accountDao, user01);
            accountDao.recordSuccessfulLogin(user01);

            createUserAccount(accountDao, user02, false, false, false, true);

            // WHEN...
//            setClockToDaysFromNow(accountDao, 91);
            int numberOfDisabledUsers = accountDao.deactivateInactiveUsers(Duration.parse("P90D"));

            // THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(accountDao.get(user01).get().isInactive()).isTrue();
            assertThat(accountDao.get(user02).get().isLocked()).isTrue();

        } catch (SQLException e) {
            e.printStackTrace();
            TestCase.fail();
        }
    }

    @Test
    public void testDisabledUserIsNeverMadeInactive() {
        try (Connection conn = getConnection()) {
            // GIVEN...
            AccountDao accountDao = getUserDao(conn);

            final String user01 = UUID.randomUUID().toString();
            final String user02 = UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(accountDao, user01);
            accountDao.recordSuccessfulLogin(user01);

            createUserAccount(accountDao, user02, false, false, false, false);

            // WHEN...
//            setClockToDaysFromNow(accountDao, 91);
            int numberOfDisabledUsers = accountDao.deactivateInactiveUsers(Duration.parse("P90D"));

            // THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(accountDao.get(user01).get().isInactive()).isTrue();
            assertThat(accountDao.get(user02).get().isEnabled()).isFalse();


        } catch (SQLException e) {
            e.printStackTrace();
            TestCase.fail();
        }
    }

    @Test
    public void testNeverExpiresUser() {
        try (Connection conn = getConnection()) {
            // GIVEN...
            AccountDao accountDao = getUserDao(conn);

            final String user01 = UUID.randomUUID().toString();
            final String user02 = UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(accountDao, user01, false);
            accountDao.recordSuccessfulLogin(user01);

            // Create a test user who should be disabled were it not for the never expire option
            createUserAccount(accountDao, user02, true);
            accountDao.recordSuccessfulLogin(user02);

            // WHEN...
//            setClockToDaysFromNow(accountDao, 91);
            int numberOfDisabledUsers = accountDao.deactivateInactiveUsers(Duration.parse("P90D"));

            // THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(accountDao.get(user01).get().isInactive()).isTrue();
            assertThat(accountDao.get(user02).get().isEnabled()).isTrue();
        } catch (SQLException e) {
            e.printStackTrace();
            TestCase.fail();
        }
    }

    @Test
    public void testNeedsPasswordChange() {
        try (Connection conn = getConnection()) {
            // GIVEN...
            AccountDao accountDao = getUserDao(conn);

            final String user01 = UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(accountDao, user01);
            accountDao.recordSuccessfulLogin(user01);

            // WHEN...
//            setClockToDaysFromNow(accountDao, 90);

            // THEN...
            // Simple
            Boolean shouldNotNeedChange = accountDao.needsPasswordChange(user01, Duration.parse("PT1M"), true);
            assertThat(shouldNotNeedChange).isTrue();

            Boolean shouldNeedChange = accountDao.needsPasswordChange(user01, Duration.parse("PT200M"), true);
            // True because they've not had a password change.
            assertThat(shouldNeedChange).isTrue();

            // Boundary cases
            Boolean shouldNotNeedChangeBoundaryCase = accountDao.needsPasswordChange(user01, Duration.parse("P90D"), true);
            assertThat(shouldNotNeedChangeBoundaryCase).isTrue();

            accountDao.changePassword(user01, "new password");
            shouldNeedChange = accountDao.needsPasswordChange(user01, Duration.parse("PT200M"), true);
            assertThat(shouldNeedChange).isFalse();

            Boolean shouldNeedChangeBoundaryCase = accountDao.needsPasswordChange(user01, Duration.parse("PT91M"), true);
            assertThat(shouldNeedChangeBoundaryCase).isFalse();
        } catch (SQLException e) {
            e.printStackTrace();
            TestCase.fail();
        }
    }

    private static void createUserAccount(AccountDao accountDao, String userId) {
        createUserAccount(accountDao, userId, false, true, false, false);
    }

    private static void createUserAccount(AccountDao accountDao, String userId, boolean neverExpires) {
        createUserAccount(accountDao, userId, neverExpires, true, false, false);
    }

    private static void createUserAccount(AccountDao accountDao,
                                          String userId,
                                          boolean neverExpires,
                                          boolean enabled,
                                          boolean inactive,
                                          boolean locked) {
        createUserAccount(accountDao, userId, neverExpires, enabled, inactive, locked, null);
    }

    private static void createUserAccount(AccountDao accountDao,
                                          String userId,
                                          boolean neverExpires,
                                          boolean enabled,
                                          boolean inactive,
                                          boolean locked,
                                          Long reactivatedDate) {
        final Account account = new Account();
        account.setCreateTimeMs(System.currentTimeMillis());
        account.setCreateUser("UserDao_IT");
        account.setLoginCount(0);
        account.setUserId(userId);
        account.setEnabled(enabled);
        account.setInactive(inactive);
        account.setLocked(locked);
        account.setNeverExpires(neverExpires);
        account.setReactivatedMs(reactivatedDate);
        accountDao.create(account, "test");
        final Account newAccount = accountDao.get(userId).get();
        assertThat(newAccount.isEnabled()).isEqualTo(enabled);
        assertThat(newAccount.isInactive()).isEqualTo(inactive);
        assertThat(newAccount.isLocked()).isEqualTo(locked);
    }

    private AccountDao getUserDao(Connection conn) {
        // We don't care about most config for this test, so we'll pass in null
        AccountDao accountDao = new AccountDaoImpl(null, new AuthDbModule.DataSourceImpl(DbTestUtil.createTestDataSource(new CommonDbConfig())));
        // We're doing tests against elapsed time so we need to be able to move the clock.
        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        return accountDao;
    }

//    private static void setClockToDaysFromNow(AccountDao accountDao, int days){
//        Instant futureInstant = Instant.now().plus(Period.ofDays(days));
//        accountDao.setClock(Clock.fixed(futureInstant, ZoneId.systemDefault()));
//    }
}
