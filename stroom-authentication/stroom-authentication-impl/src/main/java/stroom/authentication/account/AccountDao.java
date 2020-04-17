package stroom.authentication.account;

import stroom.authentication.authenticate.LoginResult;
import stroom.util.shared.ResultPage;

import java.time.Duration;
import java.util.Optional;

public interface AccountDao {
    Account create(Account account, String password);

    void recordSuccessfulLogin(String email);

    LoginResult areCredentialsValid(String email, String password);

    boolean incrementLoginFailures(String email);

    Optional<Account> get(String email);

    void update(Account account);

    void delete(int id);

    Optional<Account> get(int id);

    ResultPage<Account> getAll();

    void changePassword(String email, String newPassword);

    Boolean needsPasswordChange(String email, Duration mandatoryPasswordChangeDuration, boolean forcePasswordChangeOnFirstLogin);

    int deactivateNewInactiveUsers(Duration neverUsedAccountDeactivationThreshold);

    int deactivateInactiveUsers(Duration unusedAccountDeactivationThreshold);

    ResultPage<Account> searchUsersForDisplay(String email);
//
//    boolean exists(String id);
}
