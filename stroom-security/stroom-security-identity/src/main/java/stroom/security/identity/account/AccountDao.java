package stroom.security.identity.account;

import stroom.security.identity.authenticate.CredentialValidationResult;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;

import java.time.Duration;
import java.util.Optional;

public interface AccountDao {

    FilterFieldMappers<Account> FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(
                    AccountResource.FIELD_DEF_USER_ID,
                    Account::getUserId),
            FilterFieldMapper.of(
                    AccountResource.FIELD_DEF_EMAIL,
                    Account::getEmail),
            FilterFieldMapper.of(
                    AccountResource.FIELD_DEF_STATUS,
                    Account::getStatus),
            FilterFieldMapper.of(
                    AccountResource.FIELD_DEF_FIRST_NAME,
                    Account::getFirstName),
            FilterFieldMapper.of(
                    AccountResource.FIELD_DEF_LAST_NAME,
                    Account::getLastName));

    AccountResultPage list();

    AccountResultPage search(SearchAccountRequest request);

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
