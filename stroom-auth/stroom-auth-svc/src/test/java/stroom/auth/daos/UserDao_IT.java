package stroom.auth.daos;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.Ignore;
import org.junit.Test;
import stroom.auth.resources.user.v1.User;
import stroom.auth.resources.support.Database_IT;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;

import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static stroom.auth.resources.user.v1.User.UserState.ENABLED;
import static stroom.auth.resources.user.v1.User.UserState.LOCKED;
import static stroom.auth.resources.user.v1.User.UserState.DISABLED;
import static stroom.auth.resources.user.v1.User.UserState.INACTIVE;

@Ignore("Temporarily ignore for auth migration")
public class UserDao_IT extends Database_IT {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(UserDao_IT.class);

    @Test
    public void testNewButInactiveUserIsDisabled(){
        try (Connection conn = getConnection()) {
            // GIVEN...
            UserDao userDao = getUserDao(conn);

            final String user01 = UUID.randomUUID().toString();
            final String user02 = UUID.randomUUID().toString();
            final String user03 = UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(userDao, user01);

            // Create a user who would be disabled if they hadn't logged in already
            createUserAccount(userDao, user02);
            userDao.recordSuccessfulLogin(user02);

            // Advance the clock and create a test user who shouldn't be disabled
            setClockToDaysFromNow(userDao, 10);
            createUserAccount(userDao, user03);

            // WHEN...
            setClockToDaysFromNow(userDao, 31);
            int numberOfDisabledUsers = userDao.deactivateNewInactiveUsers(Duration.parse("P30D"));

            // THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(userDao.get(user01).get().getState()).isEqualTo(INACTIVE.getStateText());
            assertThat(userDao.get(user02).get().getState()).isEqualTo(ENABLED.getStateText());
            assertThat(userDao.get(user03).get().getState()).isEqualTo(ENABLED.getStateText());

        } catch (SQLException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testReactivatedDateIsUsedInsteadOfLastLoginForNewUsers(){
        try (Connection conn = getConnection()) {
            // GIVEN...
            UserDao userDao = getUserDao(conn);

            final String user01 = UUID.randomUUID().toString();
            final String user02 = UUID.randomUUID().toString();
            final String user03 = UUID.randomUUID().toString();
            final String user04 = UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(userDao, user01);

            // Create a user who would be disabled except their reactivated_date has been set to more recently
            createUserAccount(userDao, user04, false, ENABLED.getStateText(),
                    UserMapper.toIso(Timestamp.from(ZonedDateTime.now().plusDays(10).toInstant())));

            // Create a user who would be disabled if they hadn't logged in already
            createUserAccount(userDao, user02);
            userDao.recordSuccessfulLogin(user02);

            // Advance the clock and create a test user who shouldn't be disabled
            setClockToDaysFromNow(userDao, 10);
            createUserAccount(userDao, user03);

            // WHEN...
            setClockToDaysFromNow(userDao, 31);
            int numberOfDisabledUsers = userDao.deactivateNewInactiveUsers(Duration.parse("P30D"));

            // THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(userDao.get(user01).get().getState()).isEqualTo(INACTIVE.getStateText());
            assertThat(userDao.get(user02).get().getState()).isEqualTo(ENABLED.getStateText());
            assertThat(userDao.get(user03).get().getState()).isEqualTo(ENABLED.getStateText());
            assertThat(userDao.get(user04).get().getState()).isEqualTo(ENABLED.getStateText());

        } catch (SQLException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testReactivatedDateIsUsedInsteadOfLastLogin(){
        try (Connection conn = getConnection()) {
            // GIVEN...
            UserDao userDao = getUserDao(conn);

            final String user01 = "user01_" + UUID.randomUUID().toString();
            final String user02 = "user02_" + UUID.randomUUID().toString();
            final String user03 = "user03_" + UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(userDao, user01);
            userDao.recordSuccessfulLogin(user01);

//            // Create a user who would be disabled except their reactivated_date has been set to more recently
            createUserAccount(userDao, user03, false, ENABLED.getStateText(),
                    UserMapper.toIso(Timestamp.from(ZonedDateTime.now().plusDays(40).toInstant())));

            // Advance the clock and create a test user who shouldn't be disabled
            setClockToDaysFromNow(userDao, 40);
            createUserAccount(userDao, user02);
            userDao.recordSuccessfulLogin(user02);

            // WHEN...
            setClockToDaysFromNow(userDao, 91);
            int numberOfDisabledUsers = userDao.deactivateInactiveUsers(Duration.parse("P90D"));

            // THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(userDao.get(user01).get().getState()).isEqualTo(INACTIVE.getStateText());
            assertThat(userDao.get(user02).get().getState()).isEqualTo(ENABLED.getStateText());
            assertThat(userDao.get(user03).get().getState()).isEqualTo(ENABLED.getStateText());

        } catch (SQLException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testInactiveUserIsDeactivated(){
        try (Connection conn = getConnection()) {
            // GIVEN...
            UserDao userDao = getUserDao(conn);

            final String user01 = UUID.randomUUID().toString();
            final String user02 = UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(userDao, user01);
            userDao.recordSuccessfulLogin(user01);

            // Advance the clock and create a test user who shouldn't be disabled
            setClockToDaysFromNow(userDao, 10);
            createUserAccount(userDao, user02);
            userDao.recordSuccessfulLogin(user02);

            // WHEN...
            setClockToDaysFromNow(userDao, 91);
            int numberOfDisabledUsers = userDao.deactivateInactiveUsers(Duration.parse("P90D"));

            // THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(userDao.get(user01).get().getState()).isEqualTo(INACTIVE.getStateText());
            assertThat(userDao.get(user02).get().getState()).isEqualTo(ENABLED.getStateText());

            // ALSO WHEN...
            setClockToDaysFromNow(userDao, 200);
            numberOfDisabledUsers = userDao.deactivateInactiveUsers(Duration.parse("P90D"));

            //ALSO THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(userDao.get(user02).get().getState()).isEqualTo(INACTIVE.getStateText());

        } catch (SQLException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testLockedUserIsNeverMadeInactive() {
        try (Connection conn = getConnection()) {
            // GIVEN...
            UserDao userDao = getUserDao(conn);

            final String user01 = UUID.randomUUID().toString();
            final String user02 = UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(userDao, user01);
            userDao.recordSuccessfulLogin(user01);

            createUserAccount(userDao, user02, false, LOCKED.getStateText());

            // WHEN...
            setClockToDaysFromNow(userDao, 91);
            int numberOfDisabledUsers = userDao.deactivateInactiveUsers(Duration.parse("P90D"));

            // THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(userDao.get(user01).get().getState()).isEqualTo(INACTIVE.getStateText());
            assertThat(userDao.get(user02).get().getState()).isEqualTo(LOCKED.getStateText());

        } catch(SQLException e ){
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testDisabledUserIsNeverMadeInactive() {
        try (Connection conn = getConnection()) {
            // GIVEN...
            UserDao userDao = getUserDao(conn);

            final String user01 = UUID.randomUUID().toString();
            final String user02 = UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(userDao, user01);
            userDao.recordSuccessfulLogin(user01);

            createUserAccount(userDao, user02, false, DISABLED.getStateText());

            // WHEN...
            setClockToDaysFromNow(userDao, 91);
            int numberOfDisabledUsers = userDao.deactivateInactiveUsers(Duration.parse("P90D"));

            // THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(userDao.get(user01).get().getState()).isEqualTo(INACTIVE.getStateText());
            assertThat(userDao.get(user02).get().getState()).isEqualTo(DISABLED.getStateText());

        } catch(SQLException e ){
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testNeverExpiresUser(){
        try (Connection conn = getConnection()) {
            // GIVEN...
            UserDao userDao = getUserDao(conn);

            final String user01 = UUID.randomUUID().toString();
            final String user02 = UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(userDao, user01, false);
            userDao.recordSuccessfulLogin(user01);

            // Create a test user who should be disabled were it not for the never expire option
            createUserAccount(userDao, user02, true);
            userDao.recordSuccessfulLogin(user02);

            // WHEN...
            setClockToDaysFromNow(userDao, 91);
            int numberOfDisabledUsers = userDao.deactivateInactiveUsers(Duration.parse("P90D"));

            // THEN...
            assertThat(numberOfDisabledUsers).isEqualTo(1);
            assertThat(userDao.get(user01).get().getState()).isEqualTo(INACTIVE.getStateText());
            assertThat(userDao.get(user02).get().getState()).isEqualTo(ENABLED.getStateText());
        } catch (SQLException e) {
            e.printStackTrace();
            fail();
        }
    }


    @Test
    public void testNeedsPasswordChange() {
        try (Connection conn = getConnection()) {
            // GIVEN...
            UserDao userDao = getUserDao(conn);

            final String user01 = UUID.randomUUID().toString();

            // Create a test user who should be disabled
            createUserAccount(userDao, user01);
            userDao.recordSuccessfulLogin(user01);

            // WHEN...
            setClockToDaysFromNow(userDao, 90);

            // THEN...
            // Simple
            Boolean shouldNotNeedChange = userDao.needsPasswordChange(user01, Duration.parse("PT1M"), true);
            assertThat(shouldNotNeedChange).isTrue();

            Boolean shouldNeedChange = userDao.needsPasswordChange(user01, Duration.parse("PT200M"), true);
            // True because they've not had a password change.
            assertThat(shouldNeedChange).isTrue();

            // Boundary cases
            Boolean shouldNotNeedChangeBoundaryCase = userDao.needsPasswordChange(user01, Duration.parse("P90D"), true);
            assertThat(shouldNotNeedChangeBoundaryCase).isTrue();

            userDao.changePassword(user01, "new password");
            shouldNeedChange = userDao.needsPasswordChange(user01, Duration.parse("PT200M"), true);
            assertThat(shouldNeedChange).isFalse();

            Boolean shouldNeedChangeBoundaryCase = userDao.needsPasswordChange(user01, Duration.parse("PT91M"), true);
            assertThat(shouldNeedChangeBoundaryCase).isFalse();
        } catch (SQLException e) {
            e.printStackTrace();
            fail();
        }
    }

    private static void createUserAccount(UserDao userDao, String email) {
        createUserAccount(userDao, email, false, ENABLED.getStateText());
    }

    private static void createUserAccount(UserDao userDao, String email, boolean neverExpires){
        createUserAccount(userDao, email, neverExpires, ENABLED.getStateText());
    }

    private static void createUserAccount(UserDao userDao, String email, boolean neverExpires, String status){
        createUserAccount(userDao, email, neverExpires, status, null);
    }

    private static void createUserAccount(UserDao userDao, String email, boolean neverExpires, String status, String reactivatedDate){
        User user = new User();
        user.setEmail(email);
        user.setState(status);
        user.setNeverExpires(neverExpires);
        user.setReactivatedDate(reactivatedDate);
        userDao.create(user, "UserDao_IT");
        User newUser = userDao.get(email).get();
        assertThat(newUser.getState()).isEqualTo(status);
    }

    private UserDao getUserDao(Connection conn){
        DSLContext database = DSL.using(conn, SQLDialect.MYSQL);

        // We don't care about most config for this test, so we'll pass in null
        UserDao userDao = new UserDao(null, this.authDbConnProvider);
        // We're doing tests against elapsed time so we need to be able to move the clock.
        Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        userDao.setClock(clock);

        return userDao;
    }

    private static void setClockToDaysFromNow(UserDao userDao, int days){
        Instant futureInstant = Instant.now().plus(Period.ofDays(days));
        userDao.setClock(Clock.fixed(futureInstant, ZoneId.systemDefault()));
    }

    private static void printUser(UserDao userDao, String userId){
        User user = userDao.get(userId).get();
        LOGGER.info(user.toString());
    }
}
