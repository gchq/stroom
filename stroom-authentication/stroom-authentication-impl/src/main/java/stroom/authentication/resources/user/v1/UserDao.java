package stroom.authentication.resources.user.v1;

import org.jooq.Record13;
import org.jooq.Result;
import stroom.authentication.LoginResult;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

public interface UserDao {
    void setClock(Clock clock);

    int create(User newUser, String creatingUsername);

    void recordSuccessfulLogin(String email);

    LoginResult areCredentialsValid(String email, String password);

    boolean incrementLoginFailures(String email);

    Optional<User> get(String email);

    void update(User user);

    void delete(int id);

    Optional<User> get(int id);

    String getAll();

    void changePassword(String email, String newPassword);

    Boolean needsPasswordChange(String email, Duration mandatoryPasswordChangeDuration, boolean forcePasswordChangeOnFirstLogin);

    int deactivateNewInactiveUsers(Duration neverUsedAccountDeactivationThreshold);

    int deactivateInactiveUsers(Duration unusedAccountDeactivationThreshold);

    Result<Record13<Integer, String, String, String, String, String, Integer, Integer, Timestamp, Timestamp, String, Timestamp, String>> searchUsersForDisplay(String email);

    boolean exists(String id);
}
