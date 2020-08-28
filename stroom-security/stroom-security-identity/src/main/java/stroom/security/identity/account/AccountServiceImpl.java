package stroom.security.identity.account;

import stroom.security.identity.authenticate.PasswordValidator;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import com.google.common.base.Strings;

import javax.inject.Inject;
import java.util.Optional;

public class AccountServiceImpl implements AccountService {
    private final AccountDao accountDao;
    private final SecurityContext securityContext;
    private final IdentityConfig config;

    @Inject
    AccountServiceImpl(final AccountDao accountDao,
                       final SecurityContext securityContext,
                       final IdentityConfig config) {
        this.accountDao = accountDao;
        this.securityContext = securityContext;
        this.config = config;
    }

    @Override
    public ResultPage<Account> list() {
        checkPermission();
        return accountDao.list();
    }

    @Override
    public ResultPage<Account> search(final SearchAccountRequest request) {
        checkPermission();
        return accountDao.search(request);
    }

    @Override
    public Account create(final CreateAccountRequest request) {
        checkPermission();
        validateCreateRequest(request);

        // Validate
        final String userId = securityContext.getUserId();

        final long now = System.currentTimeMillis();

        final Account account = new Account();
        account.setCreateTimeMs(now);
        account.setCreateUser(userId);
        account.setUpdateTimeMs(now);
        account.setUpdateUser(userId);
        account.setFirstName(request.getFirstName());
        account.setLastName(request.getLastName());
        account.setUserId(request.getUserId());
        account.setEmail(request.getEmail());
        account.setComments(request.getComments());
        account.setForcePasswordChange(request.isForcePasswordChange());
        account.setNeverExpires(request.isNeverExpires());
        account.setLoginCount(0);
        // Set enabled by default.
        account.setEnabled(true);

        return accountDao.create(account, request.getPassword());
    }

    @Override
    public Optional<Account> read(final int accountId) {
        final String loggedInUser = securityContext.getUserId();

        Optional<Account> optionalUser = accountDao.get(accountId);
        if (optionalUser.isPresent()) {
            Account foundAccount = optionalUser.get();
            // We only need to check auth permissions if the user is trying to access a different user.
            final boolean isUserAccessingThemselves = loggedInUser.equals(foundAccount.getUserId());
            boolean canManageUsers = securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION);
            if (!isUserAccessingThemselves && !canManageUsers) {
                throw new RuntimeException("Unauthorized");
            }
        }
        return optionalUser;
    }

    @Override
    public Optional<Account> read(final String email) {
        checkPermission();
        return accountDao.get(email);
    }

    @Override
    public void update(final UpdateAccountRequest request, final int accountId) {
        checkPermission();
        validateUpdateRequest(request);

//        // Update Stroom user
//        Optional<Account> optionalUser = accountDao.get(userId);
//        Account foundAccount = optionalUser.get();
//        boolean isEnabled = account.isEnabled();
//        stroom.security.shared.User userToUpdate = securityUserService.getUserByName(foundAccount.getEmail());
//        userToUpdate.setEnabled(isEnabled);
//        securityUserService.update(userToUpdate);

        final String loggedInUser = securityContext.getUserId();
        final Account account = request.getAccount();
        account.setUpdateUser(loggedInUser);
        account.setUpdateTimeMs(System.currentTimeMillis());
        account.setId(accountId);
        accountDao.update(account);

        // Change the account password if the update request includes a new password.
        if (!Strings.isNullOrEmpty(request.getPassword()) && request.getPassword().equals(request.getConfirmPassword())) {
            accountDao.changePassword(account.getUserId(), request.getPassword());
        }
    }

    @Override
    public void delete(final int accountId) {
        checkPermission();
        accountDao.delete(accountId);
    }

    private void validateCreateRequest(final CreateAccountRequest request) {
        if (request == null) {
            throw new RuntimeException("Null request");
        } else {
            if (Strings.isNullOrEmpty(request.getUserId())) {
                throw new RuntimeException("No user id has been provided");
            }

            if (request.getPassword() != null || request.getConfirmPassword() != null) {
                PasswordValidator.validateLength(request.getPassword(), config.getPasswordPolicyConfig().getMinimumPasswordLength());
                PasswordValidator.validateComplexity(request.getPassword(), config.getPasswordPolicyConfig().getPasswordComplexityRegex());
                PasswordValidator.validateConfirmation(request.getPassword(), request.getConfirmPassword());
            }
        }
    }

    private void validateUpdateRequest(final UpdateAccountRequest request) {
        if (request == null) {
            throw new RuntimeException("Null request");
        } else {
            if (request.getAccount() == null || request.getAccount().getId() == null) {
                throw new RuntimeException("No user id has been provided");
            }

            if (request.getPassword() != null || request.getConfirmPassword() != null) {
                PasswordValidator.validateLength(request.getPassword(), config.getPasswordPolicyConfig().getMinimumPasswordLength());
                PasswordValidator.validateComplexity(request.getPassword(), config.getPasswordPolicyConfig().getPasswordComplexityRegex());
                PasswordValidator.validateConfirmation(request.getPassword(), request.getConfirmPassword());
            }
        }
    }

    private void checkPermission() {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to manage users");
        }
    }
}
