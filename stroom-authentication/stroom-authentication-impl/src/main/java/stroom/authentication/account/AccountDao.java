package stroom.authentication.account;

import stroom.authentication.authenticate.CredentialValidationResult;
import stroom.util.shared.ResultPage;

import java.time.Duration;
import java.util.Optional;

public interface AccountDao {
    Account create(Account account, String password);

    void recordSuccessfulLogin(String userId);

    CredentialValidationResult validateCredentials(String username, String password);

    boolean incrementLoginFailures(String userId);

    Optional<Integer> getId(String userId);

    Optional<Account> get(String userId);

    void update(Account account);

    void delete(int id);

    Optional<Account> get(int id);

    ResultPage<Account> list();

    void changePassword(String userId, String newPassword);

    Boolean needsPasswordChange(String userId, Duration mandatoryPasswordChangeDuration, boolean forcePasswordChangeOnFirstLogin);

    int deactivateNewInactiveUsers(Duration neverUsedAccountDeactivationThreshold);

    int deactivateInactiveUsers(Duration unusedAccountDeactivationThreshold);

    ResultPage<Account> searchUsersForDisplay(SearchAccountRequest request);
}
