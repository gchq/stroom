package stroom.security.identity.account;

import stroom.security.identity.authenticate.CredentialValidationResult;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountResultPage;
import stroom.security.identity.shared.FindAccountRequest;
import stroom.util.shared.ResultPage;

import java.time.Duration;
import java.util.Optional;

public interface AccountDao {

    AccountResultPage list();

    ResultPage<Account> search(FindAccountRequest request);

    Account create(Account account, String password);

    Account tryCreate(final Account account, final String password);

    Optional<Integer> getId(String userId);

    Optional<Account> get(String userId);

    Optional<Account> get(int id);

    void update(Account account);

    void delete(int id);

    void recordSuccessfulLogin(String userId);

    CredentialValidationResult validateCredentials(String username, String password);

    boolean incrementLoginFailures(String userId);

    void changePassword(String userId, String newPassword);

    void resetPassword(String userId, String newPassword);

    Boolean needsPasswordChange(String userId,
                                Duration mandatoryPasswordChangeDuration,
                                boolean forcePasswordChangeOnFirstLogin);

    int deactivateNewInactiveUsers(Duration neverUsedAccountDeactivationThreshold);

    int deactivateInactiveUsers(Duration unusedAccountDeactivationThreshold);
}
